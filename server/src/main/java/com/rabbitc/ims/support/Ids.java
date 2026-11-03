package com.rabbitc.ims.support;

import com.rabbitc.ipage.reflect.ModelMeta;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 主键生成与校验。
 * <p>
 * 本系统所有表的主键都是 <b>UUID v4 字符串</b>（36 位，带连字符），不再使用数据库自增整数。
 * 这样做的理由：
 * <ul>
 *     <li><b>不可枚举</b>：自增主键会泄露数据量，且 {@code /api/models/employee/1} 这种 URL
 *         可以被顺序遍历；UUID 无法猜测。</li>
 *     <li><b>可离线生成</b>：新增记录时主键在应用侧就已确定，不依赖数据库回填，
 *         批量导入、跨库迁移、分库分表都不需要改造。</li>
 *     <li><b>合并安全</b>：多环境（开发库、测试库）导出的数据合并时不会撞主键。</li>
 * </ul>
 * 代价是主键列变宽（VARCHAR）、索引比 BIGINT 大，对一般管理系统可以忽略。
 * <p>
 * 注意：ipage 只对<b>整数</b>主键做自增处理（{@code SchemaSync} 里的
 * {@code autoIncrement = !id.fixed() && isInteger(...)}）。主键改成 String 之后，
 * 数据库不会再生成主键，因此<b>每次插入前必须先在应用侧赋值</b>——
 * 统一走 {@link #assignIfAbsent(Object)}，不要自己拼。
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

    /** 宽松校验：允许带连字符的标准 UUID，也允许去掉连字符的 32 位形式。 */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$");

    private Ids() {
    }

    /**
     * 生成一个新的主键。
     *
     * @return 36 位 UUID 字符串，例如 {@code 3f2b1c9e-8a44-4d0e-9c31-77b6f0f1a2d5}
     */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 实体主键为空时补一个 UUID；已有主键则原样保留。
     * <p>
     * 所有插入前都要调用它。保留原值的意义在于：种子数据、数据导入可以指定固定主键，
     * 重复执行时不会产生新的 ID。
     *
     * @param entity 模型实体
     */
    public static void assignIfAbsent(Object entity) {
        ModelMeta meta = ModelMeta.of(entity.getClass());
        if (meta.idValue(entity) == null) {
            meta.idField().write(entity, newId());
        }
    }

    /**
     * 判断字符串是否像一个 UUID。
     * <p>
     * 只用于把明显非法的路径参数挡在 SQL 之前（给出 404 而不是让数据库报错），
     * 不作为安全边界——真正的安全边界永远是参数化查询。
     *
     * @param value 待校验的值
     * @return 形如 UUID 返回 {@code true}
     */
    public static boolean looksLikeId(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }
}
