package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.enums.SqlFunction;
import com.rabbitc.ipage.meta.ModelPermission;

/**
 * 部门。
 * <p>
 * 同时演示两个自动聚合列：人数与资产数。聚合列不落库，由 ipage 在查询时按
 * {@code useRelatedForeignKey = true} 连到子表统计。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "部门", table = "ims_department", icon = "fa-sitemap",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${name}")
public class Department {

    /** 主键。 */
    @ModelField(column = "dept_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 部门编码。 */
    @ModelField(column = "dept_code", text = "编码", order = 2)
    @ValidatorNotNull(message = "部门编码不能为空")
    @ValidatorLength(min = 2, max = 32, message = "部门编码长度需在 2–32 之间")
    public String code;

    /** 部门名称。 */
    @ModelField(column = "dept_name", text = "名称", order = 3)
    @ValidatorNotNull(message = "部门名称不能为空")
    @ValidatorLength(max = 64, message = "部门名称最长 64 个字符")
    public String name;

    /** 负责人。 */
    @ModelField(column = "leader", text = "负责人", order = 4)
    public String leader;

    /** 联系电话。 */
    @ModelField(column = "phone", text = "联系电话", order = 5)
    public String phone;

    /** 显示排序。 */
    @ModelField(column = "sort_no", type = FieldType.NUMBER, text = "排序", order = 6)
    public Integer sortNo;

    /** 部门人数，聚合自员工表。 */
    @ModelField(column = "employee_id", type = FieldType.AGGREGATION, byModel = Employee.class,
            function = SqlFunction.COUNT, useRelatedForeignKey = true, text = "人数", order = 7)
    public Long employeeCount;

    /** 部门资产数，聚合自资产表。 */
    @ModelField(column = "asset_id", type = FieldType.AGGREGATION, byModel = Asset.class,
            function = SqlFunction.COUNT, useRelatedForeignKey = true, text = "资产数", order = 8)
    public Long assetCount;

    /** 备注。 */
    @ModelField(column = "remark", text = "备注", order = 9)
    public String remark;

    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}
