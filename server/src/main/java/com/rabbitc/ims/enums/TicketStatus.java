package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 工单状态。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum TicketStatus {

    /** 待处理。 */
    @ModelEnum(text = "待处理")
    OPEN,

    /** 处理中。 */
    @ModelEnum(text = "处理中")
    PROCESSING,

    /** 已解决。 */
    @ModelEnum(text = "已解决")
    RESOLVED,

    /** 已关闭。 */
    @ModelEnum(text = "已关闭")
    CLOSED
}
