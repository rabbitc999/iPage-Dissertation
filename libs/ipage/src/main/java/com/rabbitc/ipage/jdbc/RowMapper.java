// 本文件已被 iPage-IMS 修改
package com.rabbitc.ipage.jdbc;

import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.reflect.FieldDescriptor;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.reflect.ValueConverter;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ipage.sql.SelectedField;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 把 {@link ResultSet} 的一行映射成模型实例。
 * <p>
 * 映射依据是列标签到实体字段路径的显式登记（{@link SelectedField#entityPath()}），
 * 而不是从列名里反推——旧实现靠「下划线前段是表名」来拆别名，
 * 表名本身含下划线时会错位，这里从设计上消除了该假设。
 * <p>
 * 关联列的目标对象按需创建：同一行的多个关联列共享同一个嵌套实例。
 *
 * @param <T> 模型类型
 * @author 小兔糖糖OvO
 * @version 1.0
 * @since 1.0
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * 映射当前行。
     *
     * @param result 结果集，游标已定位到目标行
     * @return 模型实例
     * @throws SQLException 读取失败
     */
    T mapRow(ResultSet result) throws SQLException;

    /**
     * 按查询对象的选择列表生成映射器。
     * <p>
     * 单个关联列全为空时，该嵌套对象保持 {@code null}（LEFT JOIN 未命中的情形），
     * 而不是留一个所有字段都为空的对象，这样页面上的「无关联」判断更直接。
     *
     * @param query 查询
     * @param <T>   模型类型
     * @return 映射器
     */
    @SuppressWarnings("unchecked")
    static <T> RowMapper<T> of(Query<T> query) {
        ModelMeta meta = query.meta();
        return result -> {
            T entity = (T) meta.newInstance();
            Map<String, Object> associations = new HashMap<>(4);
            var associationHits = new java.util.HashSet<String>();

            for (SelectedField selected : query.selectedFields()) {
                String path = selected.entityPath();
                int dot = path.indexOf('.');
                if (dot < 0) {
                    FieldDescriptor field = meta.field(path).orElse(null);
                    if (field == null) {
                        continue;
                    }
                    Object raw = read(result, selected.alias());
                    if (raw == null && field.type() == FieldType.AGGREGATION) {
                        // COUNT 在无匹配行时可能返回 null，业务上应视为 0
                        field.write(entity, field.type() == FieldType.AGGREGATION
                                ? ValueConverter.fromDatabase(field.javaType(), 0)
                                : null);
                        continue;
                    }
                    field.write(entity, ValueConverter.fromDatabase(field.javaType(), raw));
                    continue;
                }

                int lastDot = path.lastIndexOf('.');
                String ownerPath = path.substring(0, lastDot);
                Object raw = read(result, selected.alias());
                int parentDot = ownerPath.lastIndexOf('.');
                if (parentDot >= 0 && !associationHits.contains(ownerPath.substring(0, parentDot))) continue;
                Object parent = parentDot < 0 ? entity : associations.get(ownerPath.substring(0, parentDot));
                FieldDescriptor association = ModelMeta.of(parent.getClass())
                        .field(ownerPath.substring(parentDot + 1)).orElse(null);
                if (association == null || association.type() != FieldType.ASSOCIATION_MODEL) continue;
                Object owner = associations.computeIfAbsent(ownerPath,
                        key -> ModelMeta.of(association.relatedClass()).newInstance());
                if (raw != null && associationHits.add(ownerPath)) {
                    association.write(parent, owner);
                }
                FieldDescriptor child = ModelMeta.of(owner.getClass()).field(path.substring(lastDot + 1)).orElse(null);
                if (child != null) {
                    child.write(owner, ValueConverter.fromDatabase(child.javaType(), raw));
                }
            }
            return entity;
        };
    }

    /**
     * 只按列标签映射当前模型自身的列，用于单表主键查询。
     *
     * @param meta 模型元数据
     * @param <T>  模型类型
     * @return 映射器
     */
    @SuppressWarnings("unchecked")
    static <T> RowMapper<T> ofColumns(ModelMeta meta) {
        return result -> {
            T entity = (T) meta.newInstance();
            for (FieldDescriptor field : meta.columnFields()) {
                field.write(entity, ValueConverter.fromDatabase(field.javaType(), read(result, field.column())));
            }
            return entity;
        };
    }

    private static Object read(ResultSet result, String label) {
        try {
            return result.getObject(label);
        } catch (SQLException e) {
            throw new IpagDataAccessException("结果集中不存在列「" + label + "」", null, e);
        }
    }
}
