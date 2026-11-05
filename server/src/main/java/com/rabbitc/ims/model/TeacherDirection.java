package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

/**
 * 教师—研究方向关联。
 * <p>
 * 迁移自 {@code dissertation.teacher_direction}。原表用自增代理主键
 * {@code teacher_direction_id} + 唯一键 {@code (teacher_id, direction_id)}，
 * 代理主键纯属多余，且唯一键被误命名为 {@code uk_teacher_profession}（与另一张表的名字互换）。
 * 移植后由唯一索引 {@code uq_th_teacher_direction} 保证不重复。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "教师研究方向", table = "th_teacher_direction", icon = "fa-link",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${id}")
public class TeacherDirection {

    /** 主键。 */
    @ModelField(column = "teacher_direction_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 教师外键。 */
    @ModelField(column = "teacher_id", type = FieldType.ASSOCIATION_ID, byModel = Teacher.class,
            text = "教师", order = 2)
    public String teacherId;

    /** 教师对象。 */
    @ModelField(column = "teacher_id", type = FieldType.ASSOCIATION_MODEL, byModel = Teacher.class,
        text = "教师", order = 3)
    public Teacher teacher;

    /** 研究方向外键。 */
    @ModelField(column = "direction_id", type = FieldType.ASSOCIATION_ID, byModel = Direction.class,
            text = "研究方向", order = 4)
    public String directionId;

    /** 研究方向对象。 */
    @ModelField(column = "direction_id", type = FieldType.ASSOCIATION_MODEL, byModel = Direction.class,
            text = "研究方向", order = 5)
    public Direction direction;
}
