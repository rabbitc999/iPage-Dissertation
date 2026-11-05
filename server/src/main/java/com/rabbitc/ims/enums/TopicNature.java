package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 课题性质（原 {@code dissertation.paper.paper_nature} 的 A–E 编码）。
 * <p>
 * <b>编码含义为数据反推，非原库文档</b>，依据是 1177 条论文的题目与其它维度的相关性：
 * <ul>
 *     <li>取值为 A 的 62 条题目标题绝大多数以「…方法研究」「…的研究」结尾，
 *         且 100% 落在「论文类」课题上，与「理论研究」一致；</li>
 *     <li>取值为 B 的 1106 条几乎都是「…系统的设计与实现」，与「应用研究」一致。</li>
 * </ul>
 * 常量名直接沿用原始字母，好处是：<b>库里的值不用改</b>（仍是 A/B/C），
 * 页面显示换成中文；日后若确认含义不同，只改 {@code @ModelEnum(text=...)} 一处即可，
 * 历史数据与统计口径都不受影响。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum TopicNature {

    /** A：理论研究。 */
    @ModelEnum(text = "理论研究")
    A,

    /** B：应用研究。 */
    @ModelEnum(text = "应用研究")
    B,

    /** C：开发研究（原库未出现，按常见选题表补齐）。 */
    @ModelEnum(text = "开发研究")
    C
}
