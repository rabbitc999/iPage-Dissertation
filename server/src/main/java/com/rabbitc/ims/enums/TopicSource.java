package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 课题来源（原 {@code dissertation.paper.paper_source} 的 A–E 编码）。
 * <p>
 * <b>五档中只有 A、D、E 有较强的数据支撑，B、C 属于推测</b>，依据如下：
 * <ul>
 *     <li><b>A</b>（19 条）：题目集中在具体企业/地方题材——安徽地质旅游、茶叶商城、
 *         农产品销售、詹记商店、PE 板材工厂、微电子公司销售、电影发行，像校企或社会实际课题；</li>
 *     <li><b>B</b>（954 条，81.7%）：体量最大且题材最杂，按「默认来源」处理（教师拟题）；</li>
 *     <li><b>C</b>（80 条）：以校园与教学场景为主（学生信息、外卖订餐、少儿编程、
 *         课程进度、一卡通消费分析），推测为实验室/教学建设类；</li>
 *     <li><b>D</b>（75 条）：53% 是算法与模型研究，且难度为 A 的 9 条里 8 条来自这里，
 *         最像科研项目/教师科研课题；</li>
 *     <li><b>E</b>（40 条）：Unity 游戏、社团招新、健康监测、小说阅读、家政预约，
 *         兴趣导向明显，推测为学生自选。</li>
 * </ul>
 * 这一组是全表<b>置信度最低</b>的映射，建议对照学校当年的纸质选题审批表核一遍；
 * 改标签只动注解文本，库里的值不变。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum TopicSource {

    /** A：生产/社会实际（校企、地方合作）。 */
    @ModelEnum(text = "生产/社会实际")
    A,

    /** B：教师拟题（默认来源，占比最高）。 */
    @ModelEnum(text = "教师拟题")
    B,

    /** C：实验室与教学建设。 */
    @ModelEnum(text = "实验室与教学建设")
    C,

    /** D：科研项目。 */
    @ModelEnum(text = "科研项目")
    D,

    /** E：学生自选。 */
    @ModelEnum(text = "学生自选")
    E
}
