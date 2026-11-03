package com.rabbitc.ims.web;

import com.rabbitc.ipage.enums.OperatorType;
import com.rabbitc.ipage.enums.SortType;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ipage.sql.SqlCondition;
import com.rabbitc.ipage.sql.SqlStatement;
import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.model.Permission;
import com.rabbitc.ims.model.Role;
import com.rabbitc.ims.model.RolePermission;
import com.rabbitc.ims.model.UserRole;
import com.rabbitc.ims.security.RequireLevel;
import com.rabbitc.ims.security.RequirePermission;
import com.rabbitc.ims.support.Ids;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 角色与权限矩阵。
 * <p>
 * 通用 CRUD 也能改角色，但权限矩阵是「一次提交一组勾选」，单独做成接口更直接：
 * {@code PUT /api/roles/{id}/permissions} 用先删后插的方式整体替换。
 * <p>
 * 写操作要求 {@code system:role:manage} 权限点（超级管理员默认拥有），
 * 读操作只要求管理员等级。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api")
@RequireLevel(ModelPermission.ADMIN)
public class RoleController {

    private final ModelSession sql;

    /**
     * 构造控制器。
     *
     * @param sql SQL 会话
     */
    public RoleController(ModelSession sql) {
        this.sql = sql;
    }

    /**
     * 角色请求体。
     *
     * @param code      编码
     * @param name      名称
     * @param level     等级
     * @param dataScope 数据范围
     * @param remark    备注
     */
    public record RoleRequest(String code, String name, Integer level, String dataScope, String remark) {
    }

    /**
     * 权限分配请求体。
     *
     * @param permissionIds 权限主键
     */
    public record PermissionRequest(List<String> permissionIds) {
    }

    /**
     * 角色列表。
     *
     * @return 角色
     */
    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Role role : sql.<Role>selectList(Query.of(Role.class).orderBy("level", SortType.DESC))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", role.id);
            item.put("code", role.code);
            item.put("name", role.name);
            item.put("level", role.level);
            item.put("dataScope", role.dataScope == null ? null : role.dataScope.name());
            item.put("builtin", role.builtin);
            item.put("remark", role.remark);
            list.add(item);
        }
        return list;
    }

    /**
     * 新建角色。
     *
     * @param request 请求体
     * @return 新角色
     */
    @PostMapping("/roles")
    @RequirePermission("system:role:manage")
    public Map<String, Object> create(@RequestBody RoleRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw ApiException.badRequest("角色编码不能为空");
        }
        if (sql.selectOne(Query.of(Role.class).where("code", request.code())).isPresent()) {
            throw ApiException.badRequest("角色编码已存在");
        }
        Role role = new Role();
        role.code = request.code().trim();
        role.name = request.name() == null || request.name().isBlank() ? role.code : request.name().trim();
        role.level = request.level() == null ? 1 : Math.max(0, Math.min(4, request.level()));
        role.dataScope = parseScope(request.dataScope());
        role.builtin = Boolean.FALSE;
        role.remark = request.remark();
        // 主键是 UUID，插入前必须由应用侧赋值
        Ids.assignIfAbsent(role);
        try {
            sql.insert(role);
        } catch (com.rabbitc.ipage.exception.CheckerException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        return Map.of("id", role.id, "code", role.code, "name", role.name);
    }

    /**
     * 删除角色：内置角色不允许删除，关联关系一并清理。
     *
     * @param id 角色主键
     * @return 空响应
     */
    @DeleteMapping("/roles/{id}")
    @RequirePermission("system:role:manage")
    public Map<String, Object> delete(@PathVariable String id) {
        Role role = sql.findById(Role.class, id)
                .orElseThrow(() -> ApiException.notFound("角色不存在"));
        if (Boolean.TRUE.equals(role.builtin)) {
            throw ApiException.badRequest("内置角色不允许删除");
        }
        sql.transaction(tx -> {
            removeLinks(tx, RolePermission.class, "roleId", id);
            removeLinks(tx, UserRole.class, "roleId", id);
            tx.deleteById(Role.class, id);
        });
        return Map.of("ok", true);
    }

    /**
     * 全部权限点（按分组、序号排序）。
     *
     * @return 权限点
     */
    @GetMapping("/permissions")
    public List<Map<String, Object>> permissions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Permission permission : sql.<Permission>selectList(Query.of(Permission.class)
                .orderBy("groupName", SortType.ASC)
                .orderBy("sortNo", SortType.ASC))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", permission.id);
            item.put("code", permission.code);
            item.put("name", permission.name);
            item.put("group", permission.groupName);
            item.put("remark", permission.remark);
            list.add(item);
        }
        return list;
    }

    /**
     * 角色已分配的权限点。
     *
     * @param id 角色主键
     * @return 权限点主键
     */
    @GetMapping("/roles/{id}/permissions")
    public Map<String, Object> rolePermissions(@PathVariable String id) {
        List<String> ids = new ArrayList<>();
        for (RolePermission link : sql.<RolePermission>selectList(Query.of(RolePermission.class).where("roleId", id))) {
            if (link.permissionId != null) {
                ids.add(link.permissionId);
            }
        }
        return Map.of("permissionIds", ids);
    }

    /**
     * 整体替换角色权限。
     *
     * @param id      角色主键
     * @param request 权限主键集合
     * @return 已分配的权限点
     */
    @PutMapping("/roles/{id}/permissions")
    @RequirePermission("system:role:manage")
    public Map<String, Object> replacePermissions(@PathVariable String id, @RequestBody PermissionRequest request) {
        sql.findById(Role.class, id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(
                request.permissionIds() == null ? List.of() : request.permissionIds()));

        sql.transaction(tx -> {
            removeLinks(tx, RolePermission.class, "roleId", id);
            if (ids.isEmpty()) {
                return;
            }
            List<Permission> existing = tx.selectList(Query.of(Permission.class).in("id", ids));
            for (Permission permission : existing) {
                RolePermission link = new RolePermission();
                Ids.assignIfAbsent(link);
                link.roleId = id;
                link.permissionId = permission.id;
                tx.insert(link);
            }
        });
        return Map.of("permissionIds", ids);
    }

    /**
     * 按关联表上的角色外键删除关联行。
     *
     * @param session    会话
     * @param modelClass 关联模型类
     * @param fieldName  角色外键字段名
     * @param roleId     角色主键
     */
    private static void removeLinks(ModelSession session, Class<?> modelClass, String fieldName, String roleId) {
        ModelMeta meta = ModelMeta.of(modelClass);
        String column = meta.field(fieldName)
                .orElseThrow(() -> new IllegalStateException(modelClass.getSimpleName() + " 缺少字段 " + fieldName))
                .column();
        SqlCondition condition = new SqlCondition(column, OperatorType.EQ, roleId);
        session.update(SqlStatement.deleteWhere(meta, List.of(condition), session.dialect()));
    }

    private static DataScope parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return DataScope.SELF;
        }
        try {
            return DataScope.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DataScope.SELF;
        }
    }
}
