package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.AssetCategory;
import com.rabbitc.ims.enums.AssetStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "资产", table = "ims_asset", icon = "fa-box",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${name}")
public class Asset {

    /** 主键。 */
    @ModelField(column = "asset_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 资产编号。 */
    @ModelField(column = "asset_code", text = "资产编号", order = 2)
    @ValidatorNotNull(message = "资产编号不能为空")
    @ValidatorLength(max = 32, message = "资产编号最长 32 个字符")
    public String code;

    /** 资产名称。 */
    @ModelField(column = "asset_name", text = "名称", order = 3)
    @ValidatorNotNull(message = "资产名称不能为空")
    public String name;

    /** 分类。 */
    @ModelField(column = "category", type = FieldType.ENUM, byEnum = AssetCategory.class,
            text = "分类", order = 4)
    public AssetCategory category = AssetCategory.DEVICE;

    /** 所属部门外键。 */
    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_ID, byModel = Department.class,
            text = "所属部门", order = 5)
    public String deptId;

    /** 所属部门对象。 */
    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_MODEL, byModel = Department.class,
            text = "所属部门", order = 6)
    public Department dept;

    /** 保管人工号外键。 */
    @ModelField(column = "keeper_id", type = FieldType.ASSOCIATION_ID, byModel = Employee.class,
            text = "保管人", order = 7)
    public String keeperId;

    /** 保管人对象。 */
    @ModelField(column = "keeper_id", type = FieldType.ASSOCIATION_MODEL, byModel = Employee.class,
            text = "保管人", order = 8)
    public Employee keeper;

    /** 购置金额。 */
    @ModelField(column = "price", type = FieldType.NUMBER, text = "金额", order = 9)
    public BigDecimal price;

    /** 购置日期。 */
    @ModelField(column = "purchase_date", type = FieldType.DATE, text = "购置日期", order = 10)
    public LocalDate purchaseDate;

    /** 资产状态。 */
    @ModelField(column = "asset_status", type = FieldType.ENUM, byEnum = AssetStatus.class,
            text = "状态", order = 11)
    public AssetStatus status = AssetStatus.IDLE;

    /** 创建人。 */
    @ModelField(column = "create_by", type = FieldType.NUMBER, text = "创建人", order = 20, look = false)
    public String createBy;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 21, look = false)
    public LocalDateTime createdAt;
}
