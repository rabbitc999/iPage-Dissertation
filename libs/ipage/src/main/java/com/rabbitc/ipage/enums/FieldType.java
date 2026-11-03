package com.rabbitc.ipage.enums;

/**
 * 字段语义类型。
 * <p>
 * 决定该字段是否落库、SQL 中如何被选取、以及表单里渲染成什么控件。
 *
 * <p><b>本文件已被 iPage-IMS 修改（2026-11-04）：</b>新增 {@link #IMAGE} 图像类型，
 * 支持 String 引用（相对／绝对链接）与 byte[] 二进制 BLOB 两种存法；
 * 上游仓库见 https://github.com/rabbitc999/iPage。
 *
 * @author 小兔糖糖OvO
 * @version 1.0
 * @since 1.0
 */
public enum FieldType {
    /**
     * 主键列，落库、可排序、表单中不可编辑。
     */
    MODEL_ID,
    /**
     * 外键列，落库；表单中渲染为下拉框，选项来自 {@code byModel} 的全部记录。
     */
    ASSOCIATION_ID,
    /**
     * 关联对象，不落库；查询时通过 LEFT JOIN 填充，页面中显示为可点击的对象。
     */
    ASSOCIATION_MODEL,
    /**
     * 聚合列，不落库；查询时以 {@code function} 聚合 {@code byModel} 对应表中的 {@code column}。
     */
    AGGREGATION,
    /**
     * 字符串。
     */
    STRING,
    /**
     * 数值。
     */
    NUMBER,
    /**
     * 枚举，落库为枚举常量名。
     */
    ENUM,
    /**
     * 日期。
     */
    DATE,
    /**
     * 布尔值。
     */
    BOOLEAN,
    /**
     * 图像，落库。按字段的 Java 类型二选一：
     * <ul>
     *     <li>{@code String}：存图像<b>引用</b>，三种来源都兼容——相对链接
     *         （如 {@code /api/files/<id>/raw}，由应用自己托管）、绝对链接
     *         （{@code https://cdn.example.com/a.png}）、以及 {@code data:image/...;base64,}
     *         内联 URI；</li>
     *     <li>{@code byte[]}：图像二进制<b>直接进 SQL</b>，列类型由方言决定
     *         （H2 BLOB / MySQL MEDIUMBLOB / PostgreSQL BYTEA），查询结果原样读回字节数组。</li>
     * </ul>
     * 两种形态都不参与模糊匹配，只支持等值比较与判空；表单渲染、预览与缩放由应用层决定，
     * 引擎只保证「写进去什么、读出来还是什么」。
     */
    IMAGE;

    /**
     * 该类型是否对应表中的一个真实列。
     *
     * @return 是真实列返回 {@code true}
     */
    public boolean isColumn() {
        return this == MODEL_ID || this == ASSOCIATION_ID || this == STRING
               || this == NUMBER || this == ENUM || this == DATE || this == BOOLEAN
               || this == IMAGE;
    }

    /**
     * 该类型是否是写入数据库时参与 INSERT / UPDATE 的列。
     * <p>
     * 关联对象与聚合值是查询期算出来的，不属于表结构。
     *
     * @return 可写入返回 {@code true}
     */
    public boolean isWritable() {
        return isColumn();
    }
}
