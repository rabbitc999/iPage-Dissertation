package com.rabbitc.ims.enums;

import com.rabbitc.ipage.annotation.ModelEnum;

/**
 * 角色的数据范围。
 * <p>
 * 模型层权限决定「能不能进这张表」，数据范围决定「进来后能看见哪些行」：
 * <ul>
 *     <li>{@link #ALL}：全部数据；</li>
 *     <li>{@link #DEPT}：本部门（模型上有 {@code deptId} 字段时自动追加过滤）；</li>
 *     <li>{@link #SELF}：本人创建的数据（模型上有 {@code createBy} 字段时自动追加过滤）。</li>
 * </ul>
 * 自动追加过滤在 {@link com.rabbitc.ims.service.CrudService} 里完成，模型类不需要为此写代码。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public enum DataScope {

    /** 全部数据。 */
    @ModelEnum(text = "全部数据")
    ALL,

    /** 本部门。 */
    @ModelEnum(text = "本部门")
    DEPT,

    /** 仅本人。 */
    @ModelEnum(text = "仅本人")
    SELF
}
