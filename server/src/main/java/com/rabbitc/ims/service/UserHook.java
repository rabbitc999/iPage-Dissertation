package com.rabbitc.ims.service;

import com.rabbitc.ipage.enums.OperatorType;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ipage.sql.SqlCondition;
import com.rabbitc.ipage.sql.SqlStatement;
import com.rabbitc.ims.enums.RecordStatus;
import com.rabbitc.ims.model.Role;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.model.UserRole;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.security.PasswordHasher;
import com.rabbitc.ims.web.ApiException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户模型的专属规则。
 * <p>
 * 通用 CRUD 已经处理了权限与审计，这里只补三件模型特有的事：
 * <ol>
 *     <li>明文密码只在本类里出现一次，哈希后写 {@code password_hash}，并让旧令牌失效；</li>
 *     <li>{@code roleIds} 同步到关联表，先删后插，保持幂等；</li>
 *     <li>拦住「删自己」和「删掉最后一个超级管理员」。</li>
 * </ol>
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class UserHook implements ModelHook {

    private final ModelSession sql;
    private final PasswordHasher hasher;

    /**
     * 构造钩子。
     *
     * @param sql    SQL 会话
     * @param hasher 密码哈希器
     */
    public UserHook(ModelSession sql, PasswordHasher hasher) {
        this.sql = sql;
        this.hasher = hasher;
    }

    @Override
    public boolean supports(Class<?> modelClass) {
        return modelClass == User.class;
    }

    @Override
    public void beforeCreate(ModelMeta meta, Object entity, Map<String, Object> body, LoginUser user) {
        User target = (User) entity;
        String password = text(body.get("password"));
        if (password == null || password.length() < 6) {
            throw ApiException.badRequest("请设置至少 6 位的初始密码");
        }
        target.passwordHash = hasher.hash(password);
        target.tokenVersion = 0;
        if (target.createdAt == null) {
            target.createdAt = LocalDateTime.now();
        }
        ensureUniqueUsername(target, null);
        if (target.status == null) {
            target.status = RecordStatus.ENABLED;
        }
    }

    @Override
    public void afterCreate(ModelMeta meta, Object entity, Map<String, Object> body, LoginUser user) {
        syncRoles((User) entity, body.get("roleIds"));
    }

    @Override
    public void beforeUpdate(ModelMeta meta, Object entity, Object before,
                             Map<String, Object> body, LoginUser user) {
        User target = (User) entity;
        String password = text(body.get("password"));
        if (password != null && !password.isBlank()) {
            if (password.length() < 6) {
                throw ApiException.badRequest("新密码至少 6 位");
            }
            target.passwordHash = hasher.hash(password);
            // 改密码后旧令牌立即失效
            target.tokenVersion = (target.tokenVersion == null ? 0 : target.tokenVersion) + 1;
        }
        ensureUniqueUsername(target, target.id);
        if (LoginUserGuard.isSelf(user, target.id) && target.status == RecordStatus.DISABLED) {
            throw ApiException.badRequest("不能停用当前登录账号");
        }
    }

    @Override
    public void afterUpdate(ModelMeta meta, Object entity, Object before,
                            Map<String, Object> body, LoginUser user) {
        if (body.containsKey("roleIds")) {
            syncRoles((User) entity, body.get("roleIds"));
        }
    }

    @Override
    public void beforeDelete(ModelMeta meta, Object entity, LoginUser user) {
        User target = (User) entity;
        if (LoginUserGuard.isSelf(user, target.id)) {
            throw ApiException.badRequest("不能删除当前登录账号");
        }
        if (isLastSuperAdmin(target.id)) {
            throw ApiException.badRequest("不能删除最后一个超级管理员");
        }
    }

    // ------------------------------------------------------------------ 内部实现

    private void ensureUniqueUsername(User target, String selfId) {
        if (target.username == null || target.username.isBlank()) {
            return;
        }
        Optional<User> existing = sql.selectOne(Query.of(User.class).where("username", target.username));
        if (existing.isPresent() && !Objects.equals(existing.get().id, selfId)) {
            throw ApiException.badRequest("账号「" + target.username + "」已存在");
        }
    }

    /**
     * 同步用户角色：先删除旧关联，再写入新关联（同一事务内）。
     *
     * @param target 用户
     * @param raw    请求体里的 roleIds
     */
    private void syncRoles(User target, Object raw) {
        if (raw == null || target.id == null) {
            return;
        }
        List<String> roleIds = parseIds(raw);
        ModelMeta linkMeta = ModelMeta.of(UserRole.class);
        String userColumn = linkMeta.field("userId")
                .orElseThrow(() -> new IllegalStateException("UserRole 缺少 userId 字段"))
                .column();
        SqlCondition condition = new SqlCondition(userColumn, OperatorType.EQ, target.id);
        sql.update(SqlStatement.deleteWhere(linkMeta, List.of(condition), sql.dialect()));

        if (roleIds.isEmpty()) {
            return;
        }
        List<Role> roles = sql.selectList(Query.of(Role.class).in("id", roleIds));
        for (Role role : roles) {
            UserRole link = new UserRole();
            link.userId = target.id;
            link.roleId = role.id;
            try {
                sql.insert(link);
            } catch (Exception e) {
                throw ApiException.badRequest("保存角色关联失败：" + e.getMessage());
            }
        }
    }

    /**
     * 把请求体里的角色主键解析成 UUID 字符串集合。
     * <p>
     * 兼容三种形态：JSON 数组、逗号分隔字符串、单个字符串。解析不出来的直接丢弃，
     * 交给后面 {@code Query.in} 自然查不到，不会因为脏数据把整个保存流程打断。
     *
     * @param raw 原始值
     * @return 去重后的主键集合
     */
    private static List<String> parseIds(Object raw) {
        List<String> ids = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                String id = toId(item);
                if (id != null) {
                    ids.add(id);
                }
            }
        } else if (raw instanceof String text) {
            for (String part : text.split(",")) {
                String id = toId(part);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private static String toId(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isLastSuperAdmin(String userId) {
        Optional<Role> superRole = sql.selectOne(Query.of(Role.class).where("code", "SUPER_ADMIN"));
        if (superRole.isEmpty()) {
            return false;
        }
        List<UserRole> links = sql.selectList(Query.of(UserRole.class).where("roleId", superRole.get().id));
        boolean targetIsSuper = links.stream().anyMatch(link -> Objects.equals(link.userId, userId));
        long distinctUsers = links.stream().map(link -> link.userId).filter(Objects::nonNull).distinct().count();
        return targetIsSuper && distinctUsers <= 1;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 判断是否本人，避免与安全包的静态方法互相引用。
     */
    private static final class LoginUserGuard {

        private LoginUserGuard() {
        }

        static boolean isSelf(LoginUser user, String id) {
            return user != null && Objects.equals(user.id(), id);
        }
    }
}
