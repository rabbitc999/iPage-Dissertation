package com.rabbitc.ims.security;

import com.rabbitc.ipage.exception.CheckerException;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.enums.RecordStatus;
import com.rabbitc.ims.model.Permission;
import com.rabbitc.ims.model.Role;
import com.rabbitc.ims.model.RolePermission;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.model.UserRole;
import com.rabbitc.ims.web.ApiException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 认证与会话装配。
 * <p>
 * 角色、权限点、数据范围都在每次请求时重新读库：改了权限，客户端无需重新登录即可生效；
 * 停用账号或改密码时把 {@code token_version} 加一，旧令牌立刻作废。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Service
public class AuthService {

    private final ModelSession sql;
    private final PasswordHasher hasher;
    private final TokenService tokens;

    /**
     * 构造服务。
     *
     * @param sql    SQL 会话
     * @param hasher 密码哈希器
     * @param tokens 令牌服务
     */
    public AuthService(ModelSession sql, PasswordHasher hasher, TokenService tokens) {
        this.sql = sql;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    /**
     * 登录结果。
     *
     * @param token 令牌
     * @param user  用户快照
     */
    public record AuthResult(String token, LoginUser user) {
    }

    /**
     * 账号密码登录。
     *
     * @param username 账号
     * @param password 明文密码
     * @return 令牌与用户
     */
    public AuthResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("请输入账号和密码");
        }
        User user = findByUsername(username.trim())
                .orElseThrow(() -> ApiException.unauthorized("账号或密码不正确"));
        if (user.status == RecordStatus.DISABLED) {
            throw ApiException.forbidden("账号已停用，请联系管理员");
        }
        if (!hasher.matches(password, user.passwordHash)) {
            throw ApiException.unauthorized("账号或密码不正确");
        }
        // 登录成功是唯一能拿到明文密码的时刻：顺手把低强度哈希升级到当前参数。
        // 不改令牌版本，用户无感知；失败也不影响登录（下次再试）。
        if (hasher.needsUpgrade(user.passwordHash)) {
            user.passwordHash = hasher.hash(password);
            save(user);
        }
        LoginUser snapshot = snapshot(user);
        return new AuthResult(tokens.issue(user.id, snapshot.tokenVersion()), snapshot);
    }

    /**
     * 按主键加载用户快照，供拦截器每次请求调用。
     *
     * @param userId 用户主键（UUID）
     * @return 用户
     */
    public LoginUser load(String userId) {
        User user = sql.findById(User.class, userId)
                .orElseThrow(() -> ApiException.unauthorized("用户不存在"));
        if (user.status == RecordStatus.DISABLED) {
            throw ApiException.forbidden("账号已停用，请联系管理员");
        }
        return snapshot(user);
    }

    /**
     * 把用户实体装配成权限快照。
     *
     * @param user 用户
     * @return 快照
     */
    public LoginUser snapshot(User user) {
        List<String> roleIds = new ArrayList<>();
        for (UserRole link : sql.selectList(Query.of(UserRole.class).where("userId", user.id))) {
            if (link.roleId != null) {
                roleIds.add(link.roleId);
            }
        }
        List<Role> roles = roleIds.isEmpty()
                ? List.of()
                : sql.selectList(Query.of(Role.class).in("id", roleIds));

        int level = Integer.MIN_VALUE;
        DataScope scope = DataScope.SELF;
        for (Role role : roles) {
            if (role.level != null) {
                level = Math.max(level, role.level);
            }
            scope = wider(scope, role.dataScope);
        }
        if (level == Integer.MIN_VALUE) {
            level = 0;
        }

        Set<String> codes = new LinkedHashSet<>();
        if (!roleIds.isEmpty()) {
            List<String> permissionIds = new ArrayList<>();
            for (RolePermission link : sql.selectList(Query.of(RolePermission.class).in("roleId", roleIds))) {
                if (link.permissionId != null) {
                    permissionIds.add(link.permissionId);
                }
            }
            if (!permissionIds.isEmpty()) {
                for (Permission permission : sql.selectList(Query.of(Permission.class).in("id", permissionIds))) {
                    if (permission.code != null) {
                        codes.add(permission.code);
                    }
                }
            }
        }
        int version = user.tokenVersion == null ? 0 : user.tokenVersion;
        return new LoginUser(user.id, user.username, user.name, user.avatar, level, codes, scope,
                version);
    }

    /**
     * 修改自己的密码，并使所有旧令牌失效。
     *
     * @param current 当前用户
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 新令牌
     */
    public String changePassword(LoginUser current, String oldPassword, String newPassword) {
        User user = sql.findById(User.class, current.id())
                .orElseThrow(() -> ApiException.unauthorized("用户不存在"));
        if (!hasher.matches(oldPassword, user.passwordHash)) {
            throw ApiException.badRequest("原密码不正确");
        }
        requireStrongEnough(newPassword);
        user.passwordHash = hasher.hash(newPassword);
        user.tokenVersion = (user.tokenVersion == null ? 0 : user.tokenVersion) + 1;
        save(user);
        return tokens.issue(user.id, user.tokenVersion);
    }

    /**
     * 管理员重置他人密码。
     *
     * @param userId      目标用户
     * @param newPassword 新密码
     */
    public void resetPassword(String userId, String newPassword) {
        requireStrongEnough(newPassword);
        User user = sql.findById(User.class, userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.passwordHash = hasher.hash(newPassword);
        user.tokenVersion = (user.tokenVersion == null ? 0 : user.tokenVersion) + 1;
        save(user);
    }

    /**
     * 退出登录：令牌版本加一，该用户所有已签发令牌失效。
     *
     * @param current 当前用户
     */
    public void logout(LoginUser current) {
        sql.findById(User.class, current.id()).ifPresent(user -> {
            user.tokenVersion = (user.tokenVersion == null ? 0 : user.tokenVersion) + 1;
            save(user);
        });
    }

    /**
     * 按账号查用户。
     *
     * @param username 账号
     * @return 用户
     */
    public Optional<User> findByUsername(String username) {
        return sql.selectOne(Query.of(User.class).where("username", username));
    }

    private static void requireStrongEnough(String password) {
        if (password == null || password.length() < 6) {
            throw ApiException.badRequest("新密码至少 6 位");
        }
    }

    private static DataScope wider(DataScope left, DataScope right) {
        DataScope a = left == null ? DataScope.SELF : left;
        DataScope b = right == null ? DataScope.SELF : right;
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(DataScope scope) {
        return switch (scope) {
            case ALL -> 3;
            case DEPT -> 2;
            case SELF -> 1;
        };
    }

    /**
     * 判断两个用户是否同一人。
     *
     * @param user 用户
     * @param id   主键
     * @return 相同返回 {@code true}
     */
    public static boolean isSelf(LoginUser user, String id) {
        return user != null && Objects.equals(user.id(), id);
    }

    /**
     * 保存用户，把模型的校验异常转成 400。
     *
     * @param user 用户
     */
    private void save(User user) {
        try {
            sql.update(user);
        } catch (CheckerException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }
}
