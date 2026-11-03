package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 资产分类。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum AssetCategory {

    /** 电子设备。 */
    @ModelEnum(text = "电子设备")
    DEVICE,

    /** 办公家具。 */
    @ModelEnum(text = "办公家具")
    FURNITURE,

    /** 软件许可。 */
    @ModelEnum(text = "软件许可")
    SOFTWARE,

    /** 车辆。 */
    @ModelEnum(text = "车辆")
    VEHICLE,

    /** 其它。 */
    @ModelEnum(text = "其它")
    OTHER
}
