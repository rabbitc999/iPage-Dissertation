package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 教师职称。
 * <p>
 * 迁移自 dissertation.teacher.teacher_post。原列是 varchar(20)，实测出现 10 种写法，
 * 其中「高工」与「高级工程师」是同一个职称的两种叫法，「无」也被当成一个真实取值。
 * 这里统一为「高级工程师」，并把「无」显式建模成 {@link #NONE}，
 * 避免出现「空串 / 无 / NULL」三种都表示没有职称的混乱。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum TeacherPost {

    /** 教授。 */
    @ModelEnum(text = "教授")
    PROFESSOR,

    /** 副教授。 */
    @ModelEnum(text = "副教授")
    ASSOCIATE_PROFESSOR,

    /** 讲师。 */
    @ModelEnum(text = "讲师")
    LECTURER,

    /** 助教。 */
    @ModelEnum(text = "助教")
    ASSISTANT,

    /** 正高级工程师。 */
    @ModelEnum(text = "正高级工程师")
    PROFESSOR_ENGINEER,

    /** 高级工程师（原表里的「高工」并入此项）。 */
    @ModelEnum(text = "高级工程师")
    SENIOR_ENGINEER,

    /** 工程师。 */
    @ModelEnum(text = "工程师")
    ENGINEER,

    /** 其他中级。 */
    @ModelEnum(text = "其他中级")
    OTHER_INTERMEDIATE,

    /** 无职称。 */
    @ModelEnum(text = "无")
    NONE
}
