package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

/**
 * 权限点。
 * <p>
 * 权限点只约束「功能入口」这一层：进入某个模型后，行级过滤由角色等级与数据范围决定。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "权限点", table = "sys_permission", icon = "fa-key",
        look = ModelPermission.ADMIN, edit = ModelPermission.SUPER_ADMIN,
        delete = ModelPermission.SUPER_ADMIN, text = "${name}")
public class Permission {

    /** 主键。 */
    @ModelField(column = "perm_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 权限编码，例如 {@code system:role:manage}。 */
    @ModelField(column = "perm_code", text = "编码", order = 2)
    @ValidatorNotNull(message = "权限编码不能为空")
    public String code;

    /** 权限名称。 */
    @ModelField(column = "perm_name", text = "名称", order = 3)
    @ValidatorNotNull(message = "权限名称不能为空")
    public String name;

    /** 分组，用于权限矩阵的分栏。 */
    @ModelField(column = "group_name", text = "分组", order = 4)
    public String groupName;

    /** 显示排序。 */
    @ModelField(column = "sort_no", type = FieldType.NUMBER, text = "排序", order = 5)
    public Integer sortNo;

    /** 备注。 */
    @ModelField(column = "remark", text = "备注", order = 6)
    public String remark;

    @Override
    public String toString() {
        return name == null ? code : name;
    }
}
