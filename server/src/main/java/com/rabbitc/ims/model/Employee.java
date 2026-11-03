package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.RecordStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "员工", table = "ims_employee", icon = "fa-id-card",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${name}")
public class Employee {

    /** 主键。 */
    @ModelField(column = "employee_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 工号。 */
    @ModelField(column = "employee_no", text = "工号", order = 2)
    @ValidatorNotNull(message = "工号不能为空")
    @ValidatorLength(min = 2, max = 32, message = "工号长度需在 2–32 之间")
    public String empNo;

    /** 姓名。 */
    @ModelField(column = "employee_name", text = "姓名", order = 3)
    @ValidatorNotNull(message = "姓名不能为空")
    @ValidatorLength(max = 32, message = "姓名最长 32 个字符")
    public String name;

    /** 部门外键。 */
    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_ID, byModel = Department.class,
            text = "部门", order = 4)
    public String deptId;

    /** 部门对象，查询时由 LEFT JOIN 填充。 */
    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_MODEL, byModel = Department.class,
            text = "部门", order = 5)
    public Department dept;

    /** 岗位。 */
    @ModelField(column = "position", text = "岗位", order = 6)
    public String position;

    /** 手机号。 */
    @ModelField(column = "phone", text = "手机号", order = 7)
    public String phone;

    /** 邮箱。 */
    @ModelField(column = "email", text = "邮箱", order = 8)
    public String email;

    /** 入职日期。 */
    @ModelField(column = "hire_date", type = FieldType.DATE, text = "入职日期", order = 9)
    public LocalDate hireDate;

    /** 在职状态。 */
    @ModelField(column = "employee_status", type = FieldType.ENUM, byEnum = RecordStatus.class,
            text = "状态", order = 10)
    public RecordStatus status = RecordStatus.ENABLED;

    /** 创建人，数据范围为 SELF 时用于过滤；由服务端写入。 */
    @ModelField(column = "create_by", type = FieldType.NUMBER, text = "创建人", order = 20, look = false)
    public String createBy;

    /** 创建时间，由服务端写入。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 21, look = false)
    public LocalDateTime createdAt;
}
