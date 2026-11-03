package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 通用启用状态。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum RecordStatus {

    /** 启用。 */
    @ModelEnum(text = "启用")
    ENABLED,

    /** 停用。 */
    @ModelEnum(text = "停用")
    DISABLED
}
