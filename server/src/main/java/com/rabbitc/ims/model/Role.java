package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.DataScope;

/**
 * 角色。
 * <p>
 * {@code level} 直接对应 ipage 的 {@link ModelPermission} 等级，模型注解上的查看/编辑/删除权限
 * 就是拿它比较的；{@code dataScope} 决定这个角色能看到哪些行。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "角色", table = "sys_role", icon = "fa-user-shield",
        look = ModelPermission.ADMIN, edit = ModelPermission.SUPER_ADMIN,
        delete = ModelPermission.SUPER_ADMIN, text = "${name}")
public class Role {

    /** 主键。 */
    @ModelField(column = "role_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 角色编码。 */
    @ModelField(column = "role_code", text = "编码", order = 2)
    @ValidatorNotNull(message = "角色编码不能为空")
    @ValidatorLength(max = 32, message = "角色编码最长 32 个字符")
    public String code;

    /** 角色名称。 */
    @ModelField(column = "role_name", text = "名称", order = 3)
    @ValidatorNotNull(message = "角色名称不能为空")
    public String name;

    /** 权限等级，取值参考 {@link ModelPermission#level()}。 */
    @ModelField(column = "role_level", type = FieldType.NUMBER, text = "等级", order = 4)
    public Integer level;

    /** 数据范围。 */
    @ModelField(column = "data_scope", type = FieldType.ENUM, byEnum = DataScope.class,
            text = "数据范围", order = 5)
    public DataScope dataScope = DataScope.SELF;

    /** 是否内置角色（内置角色不允许删除）。 */
    @ModelField(column = "builtin", type = FieldType.BOOLEAN, text = "内置", order = 6)
    public Boolean builtin = Boolean.FALSE;

    /** 备注。 */
    @ModelField(column = "remark", text = "备注", order = 7)
    public String remark;

    @Override
    public String toString() {
        return name == null ? code : name;
    }
}
