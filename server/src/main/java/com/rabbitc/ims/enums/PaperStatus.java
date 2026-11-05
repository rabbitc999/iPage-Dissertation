package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 论文/课题状态。
 * <p>
 * 原库没有这个字段，导致「学生还没选题」只能用「题目写成空串 + 成绩全 NULL」来表达，
 * 实测有 9 行这样的占位数据。引入状态后：
 * <ul>
 *     <li>{@link #NOT_SELECTED} 允许题目为空，表示尚未选题；</li>
 *     <li>其余状态由钩子保证题目非空，空串不再被当作合法值；</li>
 *     <li>成绩是否存在不再需要靠「三列是否全 NULL」反推。</li>
 * </ul>
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum PaperStatus {

    /** 未选题：题目可以为空。 */
    @ModelEnum(text = "未选题")
    NOT_SELECTED,

    /** 已选题：题目必须非空，成绩通常还没有。 */
    @ModelEnum(text = "已选题")
    SELECTED,

    /** 已答辩：答辩成绩、论文成绩均已录入。 */
    @ModelEnum(text = "已答辩")
    DEFENDED,

    /** 已归档：数据冻结，只读。 */
    @ModelEnum(text = "已归档")
    ARCHIVED
}
