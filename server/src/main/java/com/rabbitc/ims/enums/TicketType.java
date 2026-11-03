package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 工单类型。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum TicketType {

    /** 故障。 */
    @ModelEnum(text = "故障")
    INCIDENT,

    /** 服务请求。 */
    @ModelEnum(text = "服务请求")
    REQUEST,

    /** 变更。 */
    @ModelEnum(text = "变更")
    CHANGE
}
