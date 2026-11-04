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
        map.put("system:audit:view", permission(tx, "system:audit:view", "审计日志", "系统管理", 3));
        map.put("system:setting:manage", permission(tx, "system:setting:manage", "系统图像设置", "系统管理", 4));
        map.put("ai:use", permission(tx, "ai:use", "使用 AI 助手", "智能助手", 1));
        map.put("ai:manage", permission(tx, "ai:manage", "管理 MCP 与 Skill", "智能助手", 2));
        return map;
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
        roles.put("ADMIN", role(tx, "ADMIN", "系统管理员", 3, DataScope.ALL, "管理用户、角色与数据"));
        roles.put("MANAGER", role(tx, "MANAGER", "部门主管", 2, DataScope.DEPT, "只能看到本部门数据"));
        roles.put("STAFF", role(tx, "STAFF", "普通员工", 1, DataScope.SELF, "只能看到自己创建的数据"));

        grant(tx, roles.get("SUPER_ADMIN"), permissions.values());
        grant(tx, roles.get("ADMIN"), permissions.values());
        grant(tx, roles.get("MANAGER"), List.of(permissions.get("ai:use")));
        grant(tx, roles.get("STAFF"), List.of(permissions.get("ai:use")));
        return roles;
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
