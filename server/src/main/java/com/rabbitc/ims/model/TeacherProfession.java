package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

/**
 * 教师—可带专业关联。
 * <p>
 * 迁移自 {@code dissertation.teacher_profession}。原表同样多一个自增代理主键，
 * 且唯一键被误命名为 {@code uk_teacher_direction}。移植后由
 * {@code uq_th_teacher_profession} 保证不重复。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "教师可带专业", table = "th_teacher_profession", icon = "fa-link",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${id}")
public class TeacherProfession {

    /** 主键。 */
    @ModelField(column = "teacher_profession_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 教师外键。 */
    @ModelField(column = "teacher_id", type = FieldType.ASSOCIATION_ID, byModel = Teacher.class,
            text = "教师", order = 2)
    public String teacherId;

    /** 教师对象。 */
    @ModelField(column = "teacher_id", type = FieldType.ASSOCIATION_MODEL, byModel = Teacher.class,
            text = "教师", order = 3)
    public Teacher teacher;

    /** 可带专业外键。 */
    @ModelField(column = "profession_id", type = FieldType.ASSOCIATION_ID, byModel = Profession.class,
            text = "可带专业", order = 4)
    public String professionId;

    /** 可带专业对象。 */
    @ModelField(column = "profession_id", type = FieldType.ASSOCIATION_MODEL, byModel = Profession.class,
            text = "可带专业", order = 5)
    public Profession profession;
}
