package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

/**
 * 角色—权限关联。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "角色权限", table = "sys_role_permission", icon = "fa-link",
        look = ModelPermission.ADMIN, edit = ModelPermission.ADMIN, delete = ModelPermission.ADMIN,
        text = "${id}")
public class RolePermission {

    /** 主键。 */
    @ModelField(column = "role_perm_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 角色外键。 */
    @ModelField(column = "role_id", type = FieldType.ASSOCIATION_ID, byModel = Role.class,
            text = "角色", order = 2)
    public String roleId;

    /** 角色对象。 */
    @ModelField(column = "role_id", type = FieldType.ASSOCIATION_MODEL, byModel = Role.class,
            text = "角色", order = 3)
    public Role role;

    /** 权限外键。 */
    @ModelField(column = "perm_id", type = FieldType.ASSOCIATION_ID, byModel = Permission.class,
            text = "权限", order = 4)
    public String permissionId;

    /** 权限对象。 */
    @ModelField(column = "perm_id", type = FieldType.ASSOCIATION_MODEL, byModel = Permission.class,
            text = "权限", order = 5)
    public Permission permission;
}
