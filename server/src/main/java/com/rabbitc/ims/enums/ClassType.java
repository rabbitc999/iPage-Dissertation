package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 班级类型。
 * <p>
 * 迁移自 dissertation.class.class_type。原表用中文串存值（统招/专升本/对口招生），
 * 没有约束，写错一个字就成新类别；改为枚举后取值在编译期收敛，
 * 落库为常量名（{@code VARCHAR}），页面显示用注解里的中文。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum ClassType {

    /** 统招。 */
    @ModelEnum(text = "统招")
    REGULAR,

    /** 专升本。 */
    @ModelEnum(text = "专升本")
    UPGRADE,

    /** 对口招生。 */
    @ModelEnum(text = "对口招生")
    TARGETED
}
