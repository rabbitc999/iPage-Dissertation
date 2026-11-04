package com.rabbitc.ims.meta;

import com.rabbitc.ims.service.AssociationAccess;

import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.annotation.ValidatorPattern;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.reflect.FieldDescriptor;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ims.model.Role;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.web.ApiException;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 把 ipage 的模型元数据翻译成前端可以直接渲染的 schema。
 * <p>
 * 权限在这里被「翻译」成三个布尔量（查看 / 编辑 / 删除）和字段级的可见性、可编辑性：
 * 前端不做权限判断，只按服务端给的布尔量渲染——这样即使有人改了前端代码，
 * 请求仍会在服务端被拦下。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Service
public class MetaService {

    private final ModelSession sql;
    private final AssociationAccess associations;
    private final ModelRegistry registry;

    /**
     * 构造服务。
     *
     * @param sql      SQL 会话
     * @param registry 模型注册表
     */
    public MetaService(ModelSession sql, ModelRegistry registry, AssociationAccess associations) {
        this.sql = sql;
        this.associations = associations;
        this.registry = registry;
    }

    /**
     * 侧边菜单：只返回当前用户有权查看的模型。
     *
     * @param user 当前用户
     * @return 菜单项
     */
    public List<Map<String, Object>> nav(LoginUser user) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String key : registry.exposedKeys()) {
            ModelMeta meta = ModelMeta.of(registry.require(key));
            if (!canView(user, meta, key)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("name", meta.name());
            item.put("icon", meta.icon());
            item.put("group", ModelRegistry.groupOf(meta.modelClass()));
            items.add(item);
        }
        return items;
    }

    /**
     * 单个模型的完整 schema。
     *
     * @param user 当前用户
     * @param key  模型 key
     * @return schema
     */
    public Map<String, Object> schema(LoginUser user, String key) {
        Class<?> modelClass = registry.require(key);
        ModelMeta meta = ModelMeta.of(modelClass);

        boolean view = canView(user, meta, key);
        boolean edit = user.reaches(meta.editPermission()) || user.has("model:" + key + ":edit");
        boolean delete = user.reaches(meta.deletePermission()) || user.has("model:" + key + ":delete");
        if (!view && !edit) {
            throw ApiException.forbidden("没有查看「" + meta.name() + "」的权限");
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("key", key);
        schema.put("name", meta.name());
        schema.put("table", meta.table());
        schema.put("icon", meta.icon());
        schema.put("group", ModelRegistry.groupOf(modelClass));
        schema.put("idField", meta.idField().name());
        schema.put("permissions", Map.of("view", view, "create", edit, "edit", edit, "delete", delete));

        Set<String> associationColumns = new LinkedHashSet<>();
        for (FieldDescriptor field : meta.fields()) {
            if (field.type() == FieldType.ASSOCIATION_MODEL) {
                associationColumns.add(field.column());
            }
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        for (FieldDescriptor field : meta.fields()) {
            if (!field.visible() || !field.lookPermission().granted(user.level())) {
                continue;
            }
            fields.add(describe(field, user, modelClass, associationColumns));
        }
        if (modelClass == User.class) {
            fields.addAll(virtualFieldsForUser());
        }
        fields.sort((a, b) -> Integer.compare((Integer) a.get("order"), (Integer) b.get("order")));
        schema.put("fields", fields);

        List<String> searchFields = new ArrayList<>();
        for (FieldDescriptor field : meta.fields()) {
            if (field.type() == FieldType.STRING && field.visible()
                && field.lookPermission().granted(user.level())) {
                searchFields.add(field.name());
            }
        }
        schema.put("searchFields", searchFields);
        schema.put("defaultSort", Map.of("field", meta.idField().name(), "direction", "desc"));
        return schema;
    }

    /**
     * 单个字段的描述。
     *
     * @param field             字段
     * @param user              当前用户
     * @param associationColumns 已有对象型关联字段的列
     * @return 描述
     */
    private Map<String, Object> describe(FieldDescriptor field, LoginUser user, Class<?> owner, Set<String> associationColumns) {
        boolean editable = field.isColumn() && !field.fixed()
                           && field.editPermission().granted(user.level());
        boolean inTable = true;
        boolean inForm = editable;
        switch (field.type()) {
            case MODEL_ID -> {
                inTable = false;
                inForm = false;
            }
            case ASSOCIATION_MODEL -> {
                // 关联对象只用于展示；表单里编辑的是同列的 ASSOCIATION_ID 下拉框
                inForm = false;
            }
            case ASSOCIATION_ID -> {
                // 已经有一个同列的对象字段时，表格里只显示对象，避免同一列出现两次
                inTable = !associationColumns.contains(field.column());
            }
            case AGGREGATION -> inForm = false;
            default -> {
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", field.name());
        result.put("column", field.column());
        result.put("text", field.text());
        result.put("type", field.type().name());
        result.put("order", field.order());
        result.put("fixed", field.fixed());
        result.put("required", has(field, ValidatorNotNull.class));
        result.put("maxLength", maxLength(field));
        result.put("pattern", pattern(field));
        result.put("table", inTable);
        result.put("form", inForm);
        result.put("sortable", field.isColumn() || field.type() == FieldType.AGGREGATION);

        if (field.type() == FieldType.ENUM) {
            result.put("options", ModelMeta.enumOptions(field));
        } else if (field.type() == FieldType.ASSOCIATION_ID && field.relatedClass() != null) {
            result.put("target", ModelRegistry.keyOf(field.relatedClass()));
            result.put("options", associations.options(user, owner, field));
        }
        return result;
    }

    /**
     * 用户模型独有的虚拟字段：明文密码与角色多选。
     * <p>
     * 两者都不在 {@code sys_user} 表里，由 {@code UserHook} 落到密码哈希与关联表。
     *
     * @return 字段描述
     */
    private List<Map<String, Object>> virtualFieldsForUser() {
        List<Map<String, Object>> fields = new ArrayList<>();

        Map<String, Object> password = new LinkedHashMap<>();
        password.put("name", "password");
        password.put("column", "");
        password.put("text", "密码");
        password.put("type", "PASSWORD");
        password.put("order", 12);
        password.put("fixed", false);
        password.put("required", false);
        password.put("requiredOnCreate", true);
        password.put("maxLength", -1);
        password.put("pattern", "");
        password.put("table", false);
        password.put("form", true);
        password.put("sortable", false);
        fields.add(password);

        Map<String, Object> roles = new LinkedHashMap<>();
        roles.put("name", "roleIds");
        roles.put("column", "");
        roles.put("text", "角色");
        roles.put("type", "MULTI_SELECT");
        roles.put("order", 13);
        roles.put("fixed", false);
        roles.put("required", false);
        roles.put("maxLength", -1);
        roles.put("pattern", "");
        roles.put("table", false);
        roles.put("form", true);
        roles.put("sortable", false);
        roles.put("options", sql.options(Role.class));
        fields.add(roles);
        return fields;
    }

    /**
     * 是否可查看某个模型。
     *
     * @param user  用户
     * @param meta  元数据
     * @param key   模型 key
     * @return 可查看返回 {@code true}
     */
    public boolean canView(LoginUser user, ModelMeta meta, String key) {
        return user.reaches(meta.lookPermission()) || user.has("model:" + key + ":view");
    }

    private static boolean has(FieldDescriptor field, Class<? extends Annotation> type) {
        return field.validators().stream().anyMatch(type::isInstance);
    }

    private static int maxLength(FieldDescriptor field) {
        return field.validators().stream()
                .filter(ValidatorLength.class::isInstance)
                .map(ValidatorLength.class::cast)
                .mapToInt(ValidatorLength::max)
                .filter(value -> value != Integer.MAX_VALUE)
                .findFirst()
                .orElse(-1);
    }

    private static String pattern(FieldDescriptor field) {
        return field.validators().stream()
                .filter(ValidatorPattern.class::isInstance)
                .map(ValidatorPattern.class::cast)
                .map(ValidatorPattern::regexp)
                .findFirst()
                .orElse("");
    }

    /**
     * 取某个字段的描述，供其它服务复用。
     *
     * @param user  用户
     * @param meta  元数据
     * @param name  字段名
     * @return 描述
     */
    public Optional<Map<String, Object>> fieldSchema(LoginUser user, ModelMeta meta, String name) {
        Set<String> associationColumns = new LinkedHashSet<>();
        for (FieldDescriptor field : meta.fields()) {
            if (field.type() == FieldType.ASSOCIATION_MODEL) {
                associationColumns.add(field.column());
            }
        }
        return meta.field(name).map(field -> describe(field, user, meta.modelClass(), associationColumns));
    }
}
