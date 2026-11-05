package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * 学生。
 * <p>
 * 迁移自 {@code dissertation.student}。相对原表做了两处收敛：
 * <ul>
 *     <li>删除 {@code teacher_id}：论文指导教师已由 {@link Paper#teacherId} 表达，
 *         实测两者 1177/1177 完全一致，是典型的双写；原列还缺外键，完整性只靠应用层；</li>
 *     <li>删除 {@code student_grade}：年级由所属班级的 {@code enrollYear} 派生。</li>
 * </ul>
 * {@code student_detail} 改名为 {@code remark}，原库 1177 行有 1176 行为 NULL，属于事实上的废列，
 * 保留字段但不再参与任何业务判断。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "学生", table = "th_student", icon = "fa-user-graduate",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${name}（${studentNo}）")
public class Student {

    /** 主键。 */
    @ModelField(column = "student_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 所属班级外键。 */
    @ModelField(column = "class_id", type = FieldType.ASSOCIATION_ID, byModel = SchoolClass.class,
            text = "所属班级", order = 2)
    public String classId;

    /** 所属班级对象。 */
    @ModelField(column = "class_id", type = FieldType.ASSOCIATION_MODEL, byModel = SchoolClass.class,
            text = "所属班级", order = 3)
    public SchoolClass schoolClass;

    /** 学号，全局唯一。 */
    @ModelField(column = "student_no", text = "学号", order = 4)
    @ValidatorNotNull(message = "学号不能为空")
    @ValidatorLength(min = 4, max = 20, message = "学号长度需在 4–20 之间")
    public String studentNo;

    /** 姓名。 */
    @ModelField(column = "name", text = "姓名", order = 5)
    @ValidatorNotNull(message = "姓名不能为空")
    @ValidatorLength(max = 30, message = "姓名最长 30 个字符")
    public String name;

    /** 备注。 */
    @ModelField(column = "remark", text = "备注", order = 6)
    @ValidatorLength(max = 255, message = "备注最长 255 个字符")
    public String remark;

    /** 创建人。 */
    @ModelField(column = "create_by", type = FieldType.ASSOCIATION_ID, byModel = User.class,
            text = "创建人", order = 19, look = false)
    public String createBy;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 20, look = false)
    public LocalDateTime createdAt;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 21, look = false)
    public LocalDateTime updatedAt;

    @Override
    public String toString() {
        if (name == null) {
            return "学生";
        }
        return studentNo == null ? name : name + "（" + studentNo + "）";
    }
}
