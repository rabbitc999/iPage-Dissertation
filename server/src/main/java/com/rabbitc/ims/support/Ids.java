package com.rabbitc.ims.support;

/**
 * 主键相关的杂项常量。
 * <p>
 * 本系统所有表的主键都是 <b>UUID v4 字符串</b>（36 位，带连字符），不再使用数据库自增整数。
 * 这样做的理由：
 * <ul>
 *     <li><b>不可枚举</b>：自增主键会泄露数据量，且 {@code /api/models/employee/1} 这种 URL
 *         可以被顺序遍历；UUID 无法猜测。</li>
 *     <li><b>可离线生成</b>：主键在写入前就已确定，批量导入、跨库迁移、分库分表都不需要改造。</li>
 *     <li><b>合并安全</b>：多环境（开发库、测试库）导出的数据合并时不会撞主键。</li>
 * </ul>
 * <p>
 * UUID 的生成已下沉到 ipage 引擎：{@code insert} 发现 String 主键为空时自动补
 * {@link java.util.UUID#randomUUID()}，业务代码不再需要手动赋值。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public final class Ids {

    /**
     * 关键字搜索无命中时用的哨兵值。
     * <p>
     * 它不可能等于任何真实 UUID，因此 {@code WHERE id IN ('__none__')} 恒为空集，
     * 既不返回数据、又保持语句结构不变（不必为「空结果」写一条特判 SQL）。
     */
    public static final String NO_MATCH = "__none__";

    private Ids() {
    }
}
