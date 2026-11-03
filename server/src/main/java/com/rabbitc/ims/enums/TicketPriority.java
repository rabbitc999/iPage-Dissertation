package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 工单优先级。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum TicketPriority {

    /** 低。 */
    @ModelEnum(text = "低")
    LOW,

    /** 普通。 */
    @ModelEnum(text = "普通")
    NORMAL,

    /** 高。 */
    @ModelEnum(text = "高")
    HIGH,

    /** 紧急。 */
    @ModelEnum(text = "紧急")
    URGENT
}
