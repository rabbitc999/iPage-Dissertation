package com.rabbitc.ims.service;

import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.reflect.FieldDescriptor;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.web.ApiException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 关联字段的选项与保存校验。
 * <p>
 * 演示业务模型（部门 / 员工 / 资产 / 工单）移除后，不再有「按数据范围裁剪下拉候选」的模型：
 * 剩余的论文域关联表在业务上都是公开可选的，因此选项直接取关联表全量。
 * 保存时仍校验目标主键存在，避免把一个已删除的 id 写进外键列。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class AssociationAccess {

    private final ModelSession sql;

    public AssociationAccess(ModelSession sql) {
        this.sql = sql;
    }

    /**
     * 关联字段的下拉选项。
     *
     * @param user  当前用户
     * @param owner 拥有该字段的模型
     * @param field 关联字段
     * @return 主键到显示文本的映射
     */
    public Map<String, String> options(LoginUser user, Class<?> owner, FieldDescriptor field) {
        return sql.options(field.relatedClass());
    }

    /**
     * 保存前校验关联目标存在。
     *
     * @param user  当前用户
     * @param owner 拥有该字段的模型
     * @param field 关联字段
     * @param id    关联主键
     */
    public void validate(LoginUser user, Class<?> owner, FieldDescriptor field, String id) {
        if (id == null) {
            return;
        }
        Class<?> target = field.relatedClass();
        ModelMeta meta = ModelMeta.of(target);
        if (sql.count(Query.of(target).where(meta.idField().name(), id)) == 0) {
            throw ApiException.badRequest("所选「" + field.text() + "」不存在：" + id);
        }
    }
}
