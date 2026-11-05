package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.annotation.ValidatorRange;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.ClassType;

import java.time.LocalDateTime;

/**
 * 班级。
 * <p>
 * 迁移自 {@code dissertation.class}。原表名 {@code class} 与 Java 关键字冲突，
 * 建成实体后无法直接命名，故类名 {@code SchoolClass}、表名 {@code th_class}。
 * <p>
 * 原表 {@code grade} 存的是 22 / 24 这样的两位数年份，语义是「入学年份」而不是「年级」，
 * 这里改名为 {@code enrollYear} 并统一按四位年份存（2022 / 2024），避免和「大几」混淆。
 * 原 {@code student.student_grade} 是同一信息的第二份拷贝（实测 11/1177 已漂移），
 * 移植时直接删除，年级一律从班级派生。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "班级", table = "th_class", icon = "fa-users",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${name}")
public class SchoolClass {

    /** 主键。 */
    @ModelField(column = "class_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 所属专业外键。 */
    @ModelField(column = "profession_id", type = FieldType.ASSOCIATION_ID, byModel = Profession.class,
            text = "所属专业", order = 2)
    public String professionId;

    /** 所属专业对象。 */
    @ModelField(column = "profession_id", type = FieldType.ASSOCIATION_MODEL, byModel = Profession.class,
            text = "所属专业", order = 3)
    public Profession profession;

    /** 班级名称。 */
    @ModelField(column = "name", text = "班级名称", order = 4)
    @ValidatorNotNull(message = "班级名称不能为空")
    @ValidatorLength(max = 50, message = "班级名称最长 50 个字符")
    public String name;

    /** 班级类型。 */
    @ModelField(column = "class_type", type = FieldType.ENUM, byEnum = ClassType.class,
            text = "班级类型", order = 5)
    public ClassType classType = ClassType.REGULAR;

    /** 入学年份，如 2022。 */
    @ModelField(column = "enroll_year", type = FieldType.NUMBER, text = "入学年份", order = 6)
    @ValidatorNotNull(message = "入学年份不能为空")
    @ValidatorRange(min = 1900, max = 2100, message = "入学年份必须在 1900 到 2100 之间")
    public Integer enrollYear;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 20, look = false)
    public LocalDateTime createdAt;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 21, look = false)
    public LocalDateTime updatedAt;

    @Override
    public String toString() {
        return name == null ? "班级" : name;
    }
}
