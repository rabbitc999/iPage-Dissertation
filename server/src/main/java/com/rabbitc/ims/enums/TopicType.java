package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 课题类型（原 {@code dissertation.paper.paper_type} 的 A–E 编码）。
 * <p>
 * <b>编码含义为数据反推，非原库文档</b>。依据：1177 条里只有 B（100 条）与 C（1068 条）
 * 两个取值，且「理论研究」性质的 62 条<b>全部</b>是 B，「应用研究」的 1068 条几乎都是 C。
 * 一套毕业设计选题表里，能同时解释这种完全绑定关系的分类是「论文类 / 设计类」：
 * 写论文的选论文类，做系统的选设计类。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum TopicType {

    /** A：综合类（原库未出现，按常见选题表补齐）。 */
    @ModelEnum(text = "综合类")
    A,

    /** B：论文类。 */
    @ModelEnum(text = "论文类")
    B,

    /** C：设计类。 */
    @ModelEnum(text = "设计类")
    C
}
