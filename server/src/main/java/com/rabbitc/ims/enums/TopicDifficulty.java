package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 课题难度（原 {@code dissertation.paper.paper_difficulty} 的 A–E 编码）。
 * <p>
 * 五档含义由业务方确认：<b>A 最高、E 最低</b>。数据也印证了这个方向——
 * 难度 A 的 9 条全是改进 YOLO12、Transformer 加密流量、LLM 心理模型、二进制漏洞利用
 * 这类研究题；难度 B 的 29 条是 STM32、Unity、MCTS 优化等偏重的实现题；
 * 其余 1130 条常规业务系统都是 C。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum TopicDifficulty {

    /** A：难（最高）。 */
    @ModelEnum(text = "难")
    A,

    /** B：较难。 */
    @ModelEnum(text = "较难")
    B,

    /** C：中等。 */
    @ModelEnum(text = "中等")
    C,

    /** D：较易。 */
    @ModelEnum(text = "较易")
    D,

    /** E：易（最低）。 */
    @ModelEnum(text = "易")
    E
}
