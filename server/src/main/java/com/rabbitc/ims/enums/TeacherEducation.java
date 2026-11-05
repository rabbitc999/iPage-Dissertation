package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 教师学历。
 * <p>
 * 迁移自 dissertation.teacher.teacher_educational。原列的「研究生」与「硕士」混用，
 * 这里按最常见口径把「研究生」归入 {@link #MASTER}；
 * 若业务实际把「研究生」当作「博士+硕士」的统称，需要另加一个取值而不是改这条映射。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum TeacherEducation {

    /** 博士。 */
    @ModelEnum(text = "博士")
    DOCTOR,

    /** 硕士（原表的「研究生」并入此项）。 */
    @ModelEnum(text = "硕士")
    MASTER,

    /** 学士。 */
    @ModelEnum(text = "学士")
    BACHELOR,

    /** 无。 */
    @ModelEnum(text = "无")
    NONE
}
