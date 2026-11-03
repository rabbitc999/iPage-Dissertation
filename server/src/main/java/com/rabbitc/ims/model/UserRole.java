package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

/**
 * 用户—角色关联。
 * <p>
 * 关联表不进入菜单，由 {@code UserHook} 在保存用户时整体替换。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "用户角色", table = "sys_user_role", icon = "fa-link",
        look = ModelPermission.ADMIN, edit = ModelPermission.ADMIN, delete = ModelPermission.ADMIN,
        text = "${id}")
public class UserRole {

    /** 主键。 */
    @ModelField(column = "user_role_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 用户外键。 */
    @ModelField(column = "user_id", type = FieldType.ASSOCIATION_ID, byModel = User.class,
            text = "用户", order = 2)
    public String userId;

    /** 用户对象。 */
    @ModelField(column = "user_id", type = FieldType.ASSOCIATION_MODEL, byModel = User.class,
            text = "用户", order = 3)
    public User user;

    /** 角色外键。 */
    @ModelField(column = "role_id", type = FieldType.ASSOCIATION_ID, byModel = Role.class,
            text = "角色", order = 4)
    public String roleId;

    /** 角色对象。 */
    @ModelField(column = "role_id", type = FieldType.ASSOCIATION_MODEL, byModel = Role.class,
            text = "角色", order = 5)
    public Role role;
}
