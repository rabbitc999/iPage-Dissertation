package com.rabbitc.ims.seed;

import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.jdbc.SchemaSync;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.ai.AiProperties;
import com.rabbitc.ims.config.ImsProperties;
import com.rabbitc.ims.enums.AssetCategory;
import com.rabbitc.ims.enums.AssetStatus;
import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.enums.RecordStatus;
import com.rabbitc.ims.enums.TicketPriority;
import com.rabbitc.ims.enums.TicketStatus;
import com.rabbitc.ims.enums.TicketType;
import com.rabbitc.ims.meta.ModelRegistry;
import com.rabbitc.ims.model.AiMcpServer;
import com.rabbitc.ims.model.AiSkill;
import com.rabbitc.ims.model.Asset;
import com.rabbitc.ims.model.Department;
import com.rabbitc.ims.model.Employee;
import com.rabbitc.ims.model.Permission;
import com.rabbitc.ims.model.Role;
import com.rabbitc.ims.model.RolePermission;
import com.rabbitc.ims.model.Ticket;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.model.UserRole;
import com.rabbitc.ims.security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动装配：按模型建表 / 补列，并在空库里写入一套可直接登录的演示数据。
 * <p>
 * 建表完全交给 ipage 的 {@code synchronizeSchema}：只建表、只补列，不改类型、不删列。
 * 演示数据只在「用户表为空」时写入，重启不会覆盖用户改过的内容。
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
    private final AiProperties aiProperties;
    private final PasswordHasher hasher;

    /** 演示数据里的序号，用来生成确定性的手机号与日期，避免每次启动数据都不一样。 */
    private int sequence = 0;

    /**
     * 构造初始化器。
     *
     * @param sql          会话
     * @param registry     模型注册表
     * @param properties   应用配置
     * @param aiProperties AI 配置
     * @param hasher       密码哈希器
     */
    public DataInitializer(ModelSession sql, ModelRegistry registry, ImsProperties properties,
                           AiProperties aiProperties, PasswordHasher hasher, BusinessConstraints constraints) {
        this.sql = sql;
        this.constraints = constraints;
        this.registry = registry;
        this.properties = properties;
        this.aiProperties = aiProperties;
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
            LOG.info("[ims] 已存在 {} 个用户，跳过演示数据", users);
            return;
        }
        seed();
    }

    private void seed() {
        LOG.info("[ims] 开始写入演示数据");

        Map<String, Permission> permissions = seedPermissions();
        Map<String, Role> roles = seedRoles(permissions);
        List<Department> departments = seedDepartments();
        Map<String, User> users = seedUsers(roles, departments);
        List<Employee> employees = seedEmployees(departments, users.get("admin"));
        seedAssets(employees, departments, users.get("admin"));
        seedTickets(users, departments, employees);
        seedAi(users.get("admin"));

        LOG.info("[ims] 演示数据完成：账号 admin/admin123、manager/manager123、staff/staff123");
    }

    // ------------------------------------------------------------------ 权限与角色

    private Map<String, Permission> seedPermissions() {
        Map<String, Permission> map = new LinkedHashMap<>();
        map.put("system:user:manage", permission("system:user:manage", "用户管理", "系统管理", 1));
        map.put("system:role:manage", permission("system:role:manage", "角色与权限", "系统管理", 2));
        map.put("system:audit:view", permission("system:audit:view", "审计日志", "系统管理", 3));
        map.put("system:setting:manage", permission("system:setting:manage", "系统图像设置", "系统管理", 4));
        map.put("ai:use", permission("ai:use", "使用 AI 助手", "智能助手", 1));
        map.put("ai:manage", permission("ai:manage", "管理 MCP 与 Skill", "智能助手", 2));
        return map;
    }

    private Permission permission(String code, String name, String group, int sort) {
        Permission permission = new Permission();
        permission.code = code;
        permission.name = name;
        permission.groupName = group;
        permission.sortNo = sort;
        insert(permission);
        return permission;
    }

    private Map<String, Role> seedRoles(Map<String, Permission> permissions) {
        Map<String, Role> roles = new LinkedHashMap<>();
        roles.put("SUPER_ADMIN", role("SUPER_ADMIN", "超级管理员", 4, DataScope.ALL, "拥有全部权限"));
        roles.put("ADMIN", role("ADMIN", "系统管理员", 3, DataScope.ALL, "管理用户、角色与数据"));
        roles.put("MANAGER", role("MANAGER", "部门主管", 2, DataScope.DEPT, "只能看到本部门数据"));
        roles.put("STAFF", role("STAFF", "普通员工", 1, DataScope.SELF, "只能看到自己创建的数据"));

        grant(roles.get("SUPER_ADMIN"), permissions.values());
        grant(roles.get("ADMIN"), permissions.values());
        grant(roles.get("MANAGER"), List.of(permissions.get("ai:use")));
        grant(roles.get("STAFF"), List.of(permissions.get("ai:use")));
        return roles;
    }

    private Role role(String code, String name, int level, DataScope scope, String remark) {
        Role role = new Role();
        role.code = code;
        role.name = name;
        role.level = level;
        role.dataScope = scope;
        role.builtin = Boolean.TRUE;
        role.remark = remark;
        insert(role);
        return role;
    }

    private void grant(Role role, Iterable<Permission> permissions) {
        for (Permission permission : permissions) {
            RolePermission link = new RolePermission();
            link.roleId = role.id;
            link.permissionId = permission.id;
            insert(link);
        }
    }

    // ------------------------------------------------------------------ 组织与用户

    private List<Department> seedDepartments() {
        List<Department> list = new ArrayList<>();
        list.add(department("HQ", "总经办", "林一", "010-88880001", 1));
        list.add(department("TECH", "技术部", "陈默", "010-88880002", 2));
        list.add(department("MKT", "市场部", "赵敏", "010-88880003", 3));
        list.add(department("FIN", "财务部", "周然", "010-88880004", 4));
        return list;
    }

    private Department department(String code, String name, String leader, String phone, int sort) {
        Department department = new Department();
        department.code = code;
        department.name = name;
        department.leader = leader;
        department.phone = phone;
        department.sortNo = sort;
        insert(department);
        return department;
    }

    private Map<String, User> seedUsers(Map<String, Role> roles, List<Department> departments) {
        Map<String, User> users = new LinkedHashMap<>();
        users.put("admin", user("admin", "admin123", "林一", departments.get(0), roles.get("SUPER_ADMIN")));
        users.put("manager", user("manager", "manager123", "陈默", departments.get(1), roles.get("MANAGER")));
        users.put("staff", user("staff", "staff123", "苏禾", departments.get(1), roles.get("STAFF")));
        return users;
    }

    private User user(String username, String password, String name, Department department, Role role) {
        User user = new User();
        user.username = username;
        user.passwordHash = hasher.hash(password);
        user.name = name;
        user.deptId = department.id;
        user.phone = phone("138", 1000 + (++sequence));
        user.email = username + "@example.com";
        user.status = RecordStatus.ENABLED;
        user.tokenVersion = 0;
        user.createdAt = LocalDateTime.now();
        insert(user);

        UserRole link = new UserRole();
        link.userId = user.id;
        link.roleId = role.id;
        insert(link);
        return user;
    }

    private List<Employee> seedEmployees(List<Department> departments, User operator) {
        List<Employee> list = new ArrayList<>();
        list.add(employee("E1001", "林一", departments.get(0), "总经理", LocalDate.of(2018, 3, 1), operator));
        list.add(employee("E1002", "陈默", departments.get(1), "技术总监", LocalDate.of(2019, 7, 15), operator));
        list.add(employee("E1003", "苏禾", departments.get(1), "后端工程师", LocalDate.of(2021, 9, 1), operator));
        list.add(employee("E1004", "顾言", departments.get(1), "前端工程师", LocalDate.of(2022, 4, 11), operator));
        list.add(employee("E1005", "赵敏", departments.get(2), "市场经理", LocalDate.of(2020, 5, 20), operator));
        list.add(employee("E1006", "周然", departments.get(3), "财务主管", LocalDate.of(2019, 11, 4), operator));
        return list;
    }

    private Employee employee(String no, String name, Department department, String position,
                              LocalDate hireDate, User operator) {
        Employee employee = new Employee();
        employee.empNo = no;
        employee.name = name;
        employee.deptId = department.id;
        employee.position = position;
        employee.phone = phone("139", 2000 + (++sequence));
        employee.email = no.toLowerCase() + "@example.com";
        employee.hireDate = hireDate;
        employee.status = RecordStatus.ENABLED;
        employee.createBy = operator.id;
        employee.createdAt = LocalDateTime.now();
        insert(employee);
        return employee;
    }

    private void seedAssets(List<Employee> employees, List<Department> departments, User operator) {
        asset("A-1001", "ThinkPad X1", AssetCategory.DEVICE, departments.get(1), employees.get(1),
                new BigDecimal("12999.00"), AssetStatus.IN_USE, operator);
        asset("A-1002", "MacBook Pro 14", AssetCategory.DEVICE, departments.get(1), employees.get(2),
                new BigDecimal("16999.00"), AssetStatus.IN_USE, operator);
        asset("A-1003", "Dell U2723QE 显示器", AssetCategory.DEVICE, departments.get(1), employees.get(3),
                new BigDecimal("3599.00"), AssetStatus.IN_USE, operator);
        asset("A-1004", "人体工学椅", AssetCategory.FURNITURE, departments.get(0), employees.get(0),
                new BigDecimal("1899.00"), AssetStatus.IN_USE, operator);
        asset("A-1005", "会议室投影仪", AssetCategory.DEVICE, departments.get(2), null,
                new BigDecimal("6800.00"), AssetStatus.IDLE, operator);
        asset("A-1006", "财务系统许可", AssetCategory.SOFTWARE, departments.get(3), employees.get(5),
                new BigDecimal("24000.00"), AssetStatus.IN_USE, operator);
        asset("A-1007", "商务车", AssetCategory.VEHICLE, departments.get(0), employees.get(0),
                new BigDecimal("198000.00"), AssetStatus.REPAIR, operator);
        asset("A-1008", "旧打印机", AssetCategory.DEVICE, departments.get(2), null,
                new BigDecimal("1200.00"), AssetStatus.SCRAPPED, operator);
    }

    private void asset(String code, String name, AssetCategory category, Department department,
                       Employee keeper, BigDecimal price, AssetStatus status, User operator) {
        Asset asset = new Asset();
        asset.code = code;
        asset.name = name;
        asset.category = category;
        asset.deptId = department.id;
        asset.keeperId = keeper == null ? null : keeper.id;
        asset.price = price;
        asset.purchaseDate = LocalDate.now().minusMonths(++sequence % 30);
        asset.status = status;
        asset.createBy = operator.id;
        asset.createdAt = LocalDateTime.now();
        insert(asset);
    }

    private void seedTickets(Map<String, User> users, List<Department> departments, List<Employee> employees) {
        ticket("VPN 无法连接", TicketType.INCIDENT, TicketPriority.HIGH, TicketStatus.OPEN,
                departments.get(1), users.get("staff"), users.get("manager"),
                "在家办公时 VPN 客户端报 619 错误。");
        ticket("申请开通财务系统权限", TicketType.REQUEST, TicketPriority.NORMAL, TicketStatus.PROCESSING,
                departments.get(3), users.get("staff"), users.get("admin"),
                "需要访问月度报表模块。");
        ticket("会议室投影仪无信号", TicketType.INCIDENT, TicketPriority.URGENT, TicketStatus.RESOLVED,
                departments.get(2), users.get("manager"), users.get("admin"),
                "HDMI 接口松动，已更换线材。");
        ticket("批量导入员工数据", TicketType.REQUEST, TicketPriority.LOW, TicketStatus.CLOSED,
                departments.get(1), users.get("admin"), users.get("admin"),
                "已通过接口完成导入。");
        ticket("调整工单审批流程", TicketType.CHANGE, TicketPriority.NORMAL, TicketStatus.OPEN,
                departments.get(0), users.get("manager"), users.get("admin"),
                "希望增加部门主管审批节点。");
        ticket("笔记本电池鼓包", TicketType.INCIDENT, TicketPriority.HIGH, TicketStatus.PROCESSING,
                departments.get(1), users.get("staff"), users.get("manager"),
                "已联系厂商换新。");
        LOG.debug("[ims] 工单示例引用了 {} 名员工", employees.size());
    }

    private void ticket(String title, TicketType type, TicketPriority priority, TicketStatus status,
                        Department department, User creator, User assignee, String description) {
        Ticket ticket = new Ticket();
        ticket.title = title;
        ticket.type = type;
        ticket.priority = priority;
        ticket.status = status;
        ticket.deptId = department.id;
        ticket.assigneeId = assignee == null ? null : assignee.id;
        ticket.description = description;
        ticket.createBy = creator == null ? null : creator.id;
        ticket.createdAt = LocalDateTime.now().minusDays(++sequence % 20);
        ticket.updatedAt = LocalDateTime.now();
        ticket.dueDate = ticket.createdAt.toLocalDate().plusDays(7);
        insert(ticket);
    }

    private void seedAi(User operator) {
        AiMcpServer mcp = new AiMcpServer();
        mcp.name = "示例 MCP 服务";
        mcp.prefix = "demo";
        mcp.command = "[\"python\",\"mcp/demo_server.py\"]";
        mcp.timeoutMs = 15000;
        mcp.enabled = Boolean.FALSE;
        mcp.remark = "仓库自带的演示脚本；启用前确认本机有 Python，且从 server 目录启动服务";
        mcp.updatedAt = LocalDateTime.now();
        insert(mcp);

        AiSkill skill = new AiSkill();
        skill.name = "ims-assistant";
        skill.directory = Path.of(aiProperties.getWorkDir())
                .toAbsolutePath().normalize()
                .resolve("skills/ims-assistant")
                .toString();
        skill.enabled = Boolean.TRUE;
        skill.autoActivate = Boolean.TRUE;
        skill.remark = "内置示例：告诉模型如何使用 ims_* 工具维护数据";
        skill.updatedAt = LocalDateTime.now();
        insert(skill);
        LOG.debug("[ims] AI 示例配置已写入，操作人 {}", operator.id);
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 拼一个演示用手机号：前缀 + 6 位序号，形如 {@code 13800001001}。
     * <p>
     * 之前用 {@code Math.random()} 生成，导致每次重建库演示数据都不一样、截图无法复现，
     * 这里改成自增序号，跑多少次结果都一致。
     *
     * @param prefix 号段前缀
     * @param number 序号
     * @return 11 位手机号
     */
    private static String phone(String prefix, int number) {
        return prefix + String.format("%08d", number);
    }

    private void insert(Object entity) {
        // String 主键由 ipage 在 insert 时自动补 UUID
        try {
            sql.insert(entity);
        } catch (Exception e) {
            throw new IllegalStateException("写入演示数据失败：" + entity.getClass().getSimpleName(), e);
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
