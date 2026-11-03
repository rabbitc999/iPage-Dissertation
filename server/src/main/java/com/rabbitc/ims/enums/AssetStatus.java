package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 资产状态。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum AssetStatus {

    /** 闲置。 */
    @ModelEnum(text = "闲置")
    IDLE,

    /** 使用中。 */
    @ModelEnum(text = "使用中")
    IN_USE,

    /** 维修中。 */
    @ModelEnum(text = "维修中")
    REPAIR,

    /** 已报废。 */
    @ModelEnum(text = "已报废")
    SCRAPPED
}
