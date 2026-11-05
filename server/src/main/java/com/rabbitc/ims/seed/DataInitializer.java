package com.rabbitc.ims.seed;

import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.jdbc.SchemaSync;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.config.ImsProperties;
import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.enums.RecordStatus;
import com.rabbitc.ims.meta.ModelRegistry;
import com.rabbitc.ims.model.Permission;
import com.rabbitc.ims.model.Role;
import com.rabbitc.ims.model.RolePermission;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.model.UserRole;
import com.rabbitc.ims.security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动装配：按模型建表 / 补列，并在空库里创建唯一的初始超级管理员。
 * <p>
 * 建表完全交给 ipage 的 {@code synchronizeSchema}：只建表、只补列，不改类型、不删列。
 * 初始账号只在「用户表为空」时创建。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
@Order(10)
public class DataInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DataInitializer.class);

    private final ModelSession sql;
    private final BusinessConstraints constraints;
    private final ModelRegistry registry;
    private final ImsProperties properties;
    private final PasswordHasher hasher;

    /**
     * 构造初始化器。
     *
     * @param sql          会话
     * @param registry     模型注册表
     * @param properties   应用配置
     * @param hasher       密码哈希器
     */
    public DataInitializer(ModelSession sql, ModelRegistry registry, ImsProperties properties,
                           PasswordHasher hasher, BusinessConstraints constraints) {
        this.sql = sql;
        this.constraints = constraints;
        this.registry = registry;
        this.properties = properties;
        this.hasher = hasher;
    }

    @Override
    public void run(ApplicationArguments args) throws java.sql.SQLException {
        if (properties.isSyncSchema()) {
            SchemaSync.Report report = sql.synchronizeSchema(registry.all().toArray(new Class<?>[0]));
            constraints.synchronize();
            LOG.info("[ims] 表结构同步完成，是否有变更：{}", report.changed());
            if (report.changed()) {
                LOG.info("[ims] 执行的 DDL：{}", report.executed());
            }
        }
        if (!properties.isSeed()) {
            return;
        }
        long users = sql.count(Query.of(User.class));
        if (users > 0) {
            // 已有库只补权限定义，不自动改变角色授权。
            sql.transaction(tx -> {
                ensureAuditPermissions(tx);
                grantThesisDefaults(tx, ensureThesisPermissions(tx));
            });
            LOG.info("[ims] 已存在 {} 个用户，跳过初始账号创建", users);
            return;
        }
        sql.transaction(tx -> {
            User admin = new User();
            admin.username = "admin";
            admin.name = "超级管理员";
            admin.passwordHash = hasher.hash("123456");
            admin.status = RecordStatus.ENABLED;
            admin.tokenVersion = 0;
            admin.createdAt = LocalDateTime.now();
            insert(tx, admin);
            rebuildAccess(tx, admin.id);
        });
        LOG.info("[ims] 已创建初始超级管理员 admin");
    }

    /** 清空后只重建登录和后续管理所需的内置权限与角色。 */
    public void rebuildAccess(ModelSession tx, String operatorId) {
        Role superAdmin = seedRoles(tx, seedPermissions(tx)).get("SUPER_ADMIN");
        assignRole(tx, operatorId, superAdmin.id);
    }

    private Map<String, Permission> seedPermissions(ModelSession tx) {
        Map<String, Permission> map = new LinkedHashMap<>();
        map.put("system:user:manage", permission(tx, "system:user:manage", "用户管理", "系统管理", 1));
        map.put("system:role:manage", permission(tx, "system:role:manage", "角色与权限", "系统管理", 2));
        map.putAll(ensureAuditPermissions(tx));
        map.put("system:setting:manage", permission(tx, "system:setting:manage", "系统图像设置", "系统管理", 4));
        map.putAll(ensureThesisPermissions(tx));
        map.put("ai:use", permission(tx, "ai:use", "使用 AI 助手", "智能助手", 1));
        map.put("ai:manage", permission(tx, "ai:manage", "管理 MCP 与 Skill", "智能助手", 2));
        return map;
    }

    /**
     * 论文业务的权限点：与模型等级互补。
     * <p>
     * 模型等级（{@code @ModelClass}）只能表达「谁能读写这张表」，
     * 但「导出全库数据」「批量导入」「改成绩」「审核状态」是同一等级内部还要再分的开关，
     * 所以做成独立权限点，在对应操作上校验。
     */
    private Map<String, Permission> ensureThesisPermissions(ModelSession tx) {
        Map<String, Permission> permissions = new LinkedHashMap<>();
        permissions.put("thesis:data:export",
                permissionIfAbsent(tx, "thesis:data:export", "导出业务数据", "业务数据", 1));
        permissions.put("thesis:data:import",
                permissionIfAbsent(tx, "thesis:data:import", "批量导入业务数据", "业务数据", 2));
        permissions.put("thesis:score:manage",
                permissionIfAbsent(tx, "thesis:score:manage", "录入与修改成绩", "业务数据", 3));
        permissions.put("thesis:paper:review",
                permissionIfAbsent(tx, "thesis:paper:review", "审核论文状态", "业务数据", 4));
        return permissions;
    }

    /** 取权限点，不存在就建；已存在时保留原记录，不覆盖管理员改过的名称与排序。 */
    private Permission permissionIfAbsent(ModelSession tx, String code, String name, String group, int sort) {
        return tx.<Permission>selectOne(Query.of(Permission.class).where("code", code))
                .orElseGet(() -> permission(tx, code, name, group, sort));
    }

    /** 新旧数据库都补齐日志权限；重复启动不重复插入，也不恢复已撤销的授权。 */
    private Map<String, Permission> ensureAuditPermissions(ModelSession tx) {
        Map<String, Permission> permissions = new LinkedHashMap<>();
        for (String action : List.of("view", "delete")) {
            String code = "system:audit:" + action;
            String name = "view".equals(action) ? "查看审计日志" : "删除审计日志";
            Permission existing = tx.<Permission>selectOne(Query.of(Permission.class).where("code", code))
                    .orElse(null);
            if (existing == null) {
                existing = permission(tx, code, name, "系统管理", "view".equals(action) ? 3 : 5);
            } else if ("view".equals(action) && "审计日志".equals(existing.name)) {
                existing.name = name;
                try {
                    tx.update(existing);
                } catch (com.rabbitc.ipage.exception.CheckerException error) {
                    throw new IllegalStateException("更新日志权限名称失败", error);
                }
            }
            permissions.put(code, existing);
        }
        return permissions;
    }

    private Permission permission(ModelSession tx, String code, String name, String group, int sort) {
        Permission permission = new Permission();
        permission.code = code;
        permission.name = name;
        permission.groupName = group;
        permission.sortNo = sort;
        insert(tx, permission);
        return permission;
    }

    private Map<String, Role> seedRoles(ModelSession tx, Map<String, Permission> permissions) {
        Map<String, Role> roles = new LinkedHashMap<>();
        roles.put("SUPER_ADMIN", role(tx, "SUPER_ADMIN", "超级管理员", 4, DataScope.ALL, "拥有全部权限"));
        roles.put("ADMIN", role(tx, "ADMIN", "教务管理员", 3, DataScope.ALL, "维护论文数据、用户与系统配置"));
        roles.put("TEACHER", role(tx, "TEACHER", "指导教师", 2, DataScope.ALL, "查看与维护论文和成绩；账号与学生绑定后再收窄到所带学生"));
        roles.put("STUDENT", role(tx, "STUDENT", "学生", 0, DataScope.SELF, "只读为主，仅能看到本人创建的记录"));

        grant(tx, roles.get("SUPER_ADMIN"), permissions.values());
        grant(tx, roles.get("ADMIN"), permissions.entrySet().stream()
                .filter(entry -> !"system:audit:delete".equals(entry.getKey()))
                .map(Map.Entry::getValue).toList());
        grant(tx, roles.get("TEACHER"), List.of(permissions.get("ai:use"),
                permissions.get("thesis:score:manage")));
        grant(tx, roles.get("STUDENT"), List.of(permissions.get("ai:use")));
        return roles;
    }

    /**
     * 升级已有库时把新增的业务权限点补给内置角色。
     * <p>
     * 只补「缺失」的授权，不覆盖管理员已经调整过的矩阵：某个角色被手动去掉的权限不会被重新加回来，
     * 但从未授予过的权限（升级前根本不存在）会给上，避免升级后管理员突然用不了导出/导入。
     */
    private void grantThesisDefaults(ModelSession tx, Map<String, Permission> permissions) {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        List<String> all = List.of("thesis:data:export", "thesis:data:import",
                "thesis:score:manage", "thesis:paper:review");
        defaults.put("SUPER_ADMIN", all);
        defaults.put("ADMIN", all);
        defaults.put("TEACHER", List.of("thesis:score:manage"));
        for (Map.Entry<String, List<String>> entry : defaults.entrySet()) {
            Role role = tx.<Role>selectOne(Query.of(Role.class).where("code", entry.getKey())).orElse(null);
            if (role == null) {
                continue;
            }
            for (String code : entry.getValue()) {
                Permission permission = permissions.get(code);
                if (permission == null) {
                    continue;
                }
                long exists = tx.count(Query.of(RolePermission.class)
                        .where("roleId", role.id)
                        .where("permissionId", permission.id));
                if (exists > 0) {
                    continue;
                }
                RolePermission link = new RolePermission();
                link.roleId = role.id;
                link.permissionId = permission.id;
                insert(tx, link);
            }
        }
    }

    private Role role(ModelSession tx, String code, String name, int level, DataScope scope, String remark) {
        Role role = new Role();
        role.code = code;
        role.name = name;
        role.level = level;
        role.dataScope = scope;
        role.builtin = Boolean.TRUE;
        role.remark = remark;
        insert(tx, role);
        return role;
    }

    private void grant(ModelSession tx, Role role, Iterable<Permission> permissions) {
        for (Permission permission : permissions) {
            RolePermission link = new RolePermission();
            link.roleId = role.id;
            link.permissionId = permission.id;
            insert(tx, link);
        }
    }

    private void assignRole(ModelSession tx, String userId, String roleId) {
        UserRole link = new UserRole();
        link.userId = userId;
        link.roleId = roleId;
        insert(tx, link);
    }

    private void insert(ModelSession tx, Object entity) {
        // String 主键由 ipage 在 insert 时自动补 UUID
        try {
            tx.insert(entity);
        } catch (Exception e) {
            throw new IllegalStateException("写入初始数据失败：" + entity.getClass().getSimpleName(), e);
        }
    }

    /**
     * 供测试或运维使用：打印每个模型的物理表名。
     *
     * @return 表名到模型名的映射
     */
    public Map<String, String> tableMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Class<?> modelClass : registry.all()) {
            ModelMeta meta = ModelMeta.of(modelClass);
            map.put(meta.table(), meta.name());
        }
        return map;
    }
}
