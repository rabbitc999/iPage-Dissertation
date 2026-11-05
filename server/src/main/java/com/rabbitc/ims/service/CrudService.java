package com.rabbitc.ims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitc.ipage.dialect.Dialect;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.enums.OperatorType;
import com.rabbitc.ipage.enums.SortType;
import com.rabbitc.ipage.exception.CheckerException;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.jdbc.Page;
import com.rabbitc.ipage.reflect.FieldDescriptor;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ipage.sql.SqlStatement;
import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.meta.MetaService;
import com.rabbitc.ims.meta.ModelRegistry;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.model.UserRole;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.support.Ids;
import com.rabbitc.ims.web.ApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用 CRUD。
 * <p>
 * 一件事只做一遍：所有模型共用同一套「权限 → 数据范围 → 查询 → 审计」流程，
 * 模型之间的差异交给 {@link ModelHook}。因此新增一张表只需要写一个模型类，
 * 前端表格、表单、搜索、分页、权限判断都自动出现。
 * <p>
 * 几个刻意的选择：
 * <ul>
 *     <li>查询参数只接受字段名，字段不存在直接忽略，不会把请求参数拼进 SQL；</li>
 *     <li>关键字搜索先在服务端解析成主键集合，再用 {@code IN} 过滤，避免 OR 条件拼字符串；</li>
 *     <li>写操作放在事务里，实体、关联表、审计日志一起提交。</li>
 * </ul>
 * <b>主键约定</b>：所有模型的主键都是 UUID 字符串（见 {@link Ids}）。由于主键不再是
 * 数据库自增的整数，String 主键由 ipage 在 insert 时自动补 UUID
 * 把主键写进实体，否则插入会因主键为空而失败。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Service
public class CrudService {

    /** 关键字搜索最多收集的主键数量。 */
    private static final int KEYWORD_LIMIT = 2000;

    /** 纯数字关键字（可带负号与小数），用于数字列的等值匹配。 */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    /** 关键字里能被日期列识别的三种粒度。 */
    private static final Pattern YEAR = Pattern.compile("\\d{4}");
    private static final Pattern YEAR_MONTH = Pattern.compile("(\\d{4})-(\\d{1,2})");
    private static final Pattern FULL_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ModelSession sql;
    private final AssociationAccess associations;
    private final ModelRegistry registry;
    private final MetaService metaService;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final List<ModelHook> hooks;

    /**
     * 构造服务。
     *
     * @param sql         SQL 会话
     * @param registry    模型注册表
     * @param metaService schema 服务
     * @param audit       审计服务
     * @param mapper      JSON 转换器
     * @param hooks       模型钩子
     */
    public CrudService(ModelSession sql, ModelRegistry registry, MetaService metaService,
                       AuditService audit, ObjectMapper mapper, List<ModelHook> hooks, AssociationAccess associations) {
        this.sql = sql;
        this.associations = associations;
        this.registry = registry;
        this.metaService = metaService;
        this.audit = audit;
        this.mapper = mapper;
        this.hooks = hooks;
    }

    // ------------------------------------------------------------------ 读

    /** Reuses the normal list scope and filters for file exports. */
    private Query<Object> listQuery(LoginUser user, ModelMeta meta, Map<String, String> params) {
        Query<Object> query = Query.of(meta, sql.dialect());
        applyScope(user, meta, query);
        applyKeyword(user, meta, query, params.get("kw"));
        applyFilters(user, meta, query, params);
        applySort(user, meta, query, params.get("sort"), params.get("order"));

        return query;
    }

    /** Exports permitted model columns, or creates an empty import template. */
    public byte[] exportTable(LoginUser user, String key, Map<String, String> params,
                              TabularFiles format, boolean template) throws java.io.IOException {
        ModelMeta meta = registry.requireMeta(key);
        requireView(user, meta, key);
        if (template) requireEdit(user, meta, key);
        requireThesisPermission(user, meta, "thesis:data:export", "导出业务数据");

        List<String> headers = template ? new ArrayList<>(importColumns(user, meta).stream().map(FieldDescriptor::name).toList())
                : meta.columnFields().stream()
                    .filter(field -> field.visible() && field.lookPermission().granted(user.level()))
                    .filter(field -> field.javaType() != byte[].class)
                    .map(FieldDescriptor::name).toList();
        if (template && meta.modelClass() == User.class) headers.add("password");
        List<List<String>> rows = new ArrayList<>();
        if (!template) {
            Query<Object> query = listQuery(user, meta, params).limit(TabularFiles.MAX_ROWS + 1, 0);
            List<Object> entities = sql.selectList(query);
            if (entities.size() > TabularFiles.MAX_ROWS) {
                throw ApiException.badRequest("一次最多导出 " + TabularFiles.MAX_ROWS + " 条记录，请缩小筛选范围");
            }
            for (Object entity : entities) {
                Map<String, Object> row = toRow(user, meta, entity);
                rows.add(headers.stream().map(name -> Objects.toString(row.get(name), "")).toList());
            }
        }
        return format.write(headers, rows);
    }

    private List<FieldDescriptor> importColumns(LoginUser user, ModelMeta meta) {
        return meta.columnFields().stream()
                .filter(field -> field.type() != FieldType.MODEL_ID && !field.fixed() && field.javaType() != byte[].class)
                .filter(field -> field.visible() && field.lookPermission().granted(user.level()))
                .filter(field -> field.editPermission().granted(user.level()))
                .toList();
    }

    /** Imports new records through the normal create path in a single transaction. */
    public int importTable(LoginUser user, String key, TabularFiles format, java.io.InputStream input) {
        ModelMeta meta = registry.requireMeta(key);
        requireEdit(user, meta, key);
        requireThesisPermission(user, meta, "thesis:data:import", "批量导入业务数据");
        List<List<String>> table;
        try {
            table = format.read(input);
        } catch (ApiException error) {
            throw error;
        } catch (java.io.IOException | RuntimeException error) {
            throw ApiException.badRequest("无法读取文件，请检查文件格式和内容");
        }
        if (table.isEmpty()) throw ApiException.badRequest("文件缺少表头");
        Map<String, FieldDescriptor> allowed = new LinkedHashMap<>();
        importColumns(user, meta).forEach(field -> allowed.put(field.name(), field));
        if (meta.modelClass() == User.class) allowed.put("password", null);
        List<String> columns = new ArrayList<>();
        for (String name : table.get(0)) {
            String column = name.strip();
            if (!allowed.containsKey(column) || columns.contains(column)) {
                throw ApiException.badRequest("表头包含未知、不可导入或重复的字段：" + name);
            }
            columns.add(column);
        }

        int[] imported = {0};
        sql.transaction(tx -> {
            for (int index = 1; index < table.size(); index++) {
                List<String> row = table.get(index);
                if (row.stream().allMatch(String::isBlank)) continue;
                if (row.size() > columns.size()) {
                    throw ApiException.badRequest("第 " + (index + 1) + " 行的列数超过表头");
                }
                Map<String, Object> body = new LinkedHashMap<>();
                for (int column = 0; column < row.size(); column++) {
                    String name = columns.get(column);
                    FieldDescriptor field = allowed.get(name);
                    String value = row.get(column);
                    if (value.isBlank()) continue;
                    if (field != null && field.javaType() == LocalDateTime.class
                            && value.length() > 10 && value.charAt(10) == ' ') {
                        value = value.substring(0, 10) + "T" + value.substring(11);
                    }
                    if (field != null && field.javaType() == LocalDate.class
                            && value.length() > 10 && value.charAt(10) == 'T') {
                        value = value.substring(0, 10);
                    }
                    body.put(name, value);
                }
                try {
                    create(user, key, body);
                } catch (ApiException error) {
                    throw new ApiException(error.status(), "第 " + (index + 1) + " 行：" + error.getMessage());
                }
                imported[0]++;
            }
        });
        return imported[0];
    }


    /**
     * 分页查询。
     *
     * @param user   当前用户
     * @param key    模型 key
     * @param params 查询参数（page / size / sort / order / kw / f_字段 / op_字段）
     * @return 分页结果
     */
    public PageResult page(LoginUser user, String key, Map<String, String> params) {
        ModelMeta meta = registry.requireMeta(key);
        requireView(user, meta, key);

        Query<Object> query = listQuery(user, meta, params);

        int page = number(params.get("page"), 1, 1, Integer.MAX_VALUE);
        int size = number(params.get("size"), DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);

        Page<Object> result = sql.selectPage(query, page, size);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object entity : result.rows()) {
            rows.add(toRow(user, meta, entity));
        }
        return PageResult.of(rows, result.total(), result.page(), result.pageSize());
    }

    /**
     * 统计当前用户可见的行数，用于工作台卡片。
     *
     * @param user 当前用户
     * @param key  模型 key
     * @return 行数；无查看权限时返回 0
     */
    public long count(LoginUser user, String key) {
        ModelMeta meta = registry.requireMeta(key);
        if (!metaService.canView(user, meta, key)) {
            return 0;
        }
        Query<Object> query = Query.of(meta, sql.dialect());
        applyScope(user, meta, query);
        return sql.count(query);
    }

    /**
     * 按主键取一条。
     *
     * @param user 当前用户
     * @param key  模型 key
     * @param id   主键
     * @return 行数据
     */
    public Map<String, Object> get(LoginUser user, String key, String id) {
        ModelMeta meta = registry.requireMeta(key);
        requireView(user, meta, key);
        Object entity = requireEntity(meta, id);
        if (!visibleRow(user, meta, entity)) {
            throw ApiException.forbidden("无权查看该记录");
        }
        return toRow(user, meta, entity);
    }

    // ------------------------------------------------------------------ 写

    /**
     * 新增。
     *
     * @param user 当前用户
     * @param key  模型 key
     * @param body 请求体
     * @return 新增后的行数据
     */
    public Map<String, Object> create(LoginUser user, String key, Map<String, Object> body) {
        ModelMeta meta = registry.requireMeta(key);
        requireEdit(user, meta, key);

        Object entity = meta.newInstance();
        applyBody(meta, entity, body, user, true);
        fillCreateDefaults(meta, entity, user);
        // String 主键不会触发数据库自增，ipage 在 insert 时自动补 UUID
        forEachHook(meta, hook -> hook.beforeCreate(meta, entity, body, user));

        sql.transaction(tx -> {
            insert(tx, entity);
            forEachHook(meta, hook -> hook.afterCreate(meta, entity, body, user));
            audit.record(user, "CREATE", meta.name(), meta.idValue(entity), meta.display(entity));
        });

        Object id = meta.idValue(entity);
        return toRow(user, meta, requireEntity(meta, id));
    }

    /**
     * 修改。
     *
     * @param user 当前用户
     * @param key  模型 key
     * @param id   主键
     * @param body 请求体（只更新出现的字段）
     * @return 修改后的行数据
     */
    public Map<String, Object> update(LoginUser user, String key, String id, Map<String, Object> body) {
        ModelMeta meta = registry.requireMeta(key);
        requireEdit(user, meta, key);
        Object before = requireEntity(meta, id);
        if (!visibleRow(user, meta, before)) {
            throw ApiException.forbidden("无权修改该记录");
        }

        Object entity = before;
        applyBody(meta, entity, body, user, false);
        fillUpdateDefaults(meta, entity);
        forEachHook(meta, hook -> hook.beforeUpdate(meta, entity, before, body, user));

        sql.transaction(tx -> {
            update(tx, entity);
            forEachHook(meta, hook -> hook.afterUpdate(meta, entity, before, body, user));
            audit.record(user, "UPDATE", meta.name(), id, meta.display(entity));
        });
        return toRow(user, meta, requireEntity(meta, id));
    }

    /**
     * 删除。
     *
     * @param user 当前用户
     * @param key  模型 key
     * @param id   主键
     */
    public void delete(LoginUser user, String key, String id) {
        ModelMeta meta = registry.requireMeta(key);
        requireDelete(user, meta, key);
        Object entity = requireEntity(meta, id);
        if (!visibleRow(user, meta, entity)) {
            throw ApiException.forbidden("无权删除该记录");
        }
        forEachHook(meta, hook -> hook.beforeDelete(meta, entity, user));
        sql.transaction(tx -> {
            if (tx.deleteById(meta.modelClass(), id) == 0) {
                throw ApiException.notFound(meta.name() + " 不存在：" + id);
            }
            forEachHook(meta, hook -> hook.afterDelete(meta, entity, user));
            audit.record(user, "DELETE", meta.name(), id, meta.display(entity));
        });
    }

    /**
     * 批量删除。
     *
     * @param user 当前用户
     * @param key  模型 key
     * @param ids  主键集合
     * @return 实际删除条数
     */
    public int deleteBatch(LoginUser user, String key, Collection<String> ids) {
        int deleted = 0;
        for (String id : new LinkedHashSet<>(ids)) {
            delete(user, key, id);
            deleted++;
        }
        return deleted;
    }

    // ------------------------------------------------------------------ 查询组装

    private void applyScope(LoginUser user, ModelMeta meta, Query<?> query) {
        DataScope scope = user.dataScope();
        if (scope == null || scope == DataScope.ALL || user.isAdmin()) {
            return;
        }
        if (scope == DataScope.DEPT) {
            // 部门模型已随演示业务一起移除，不再有模型带 deptId 字段。
            // 保持与改造前一致：DEPT 角色遇到没有 deptId 的模型时不做过滤。
            return;
        }
        if (meta.field("createBy").isPresent()) {
            query.where("createBy", user.id());
        }
    }

    /**
     * 关键字搜索：先查出匹配的主键，再用 {@code IN} 过滤。
     * <p>
     * ipage 的条件列表之间是 AND，没有 OR；把 {@code OR (a LIKE ? OR b LIKE ?)} 拼进 SQL 文本
     * 会绕开参数化，所以这里换个方向：用同一套方言把「哪些行匹配」先查出来。
     * 标识符来自模型元数据、值走占位符，仍然是安全的。
     *
     * @param meta  元数据
     * @param query 查询
     * @param kw    关键字
     */
    private void applyKeyword(LoginUser user, ModelMeta meta, Query<?> query, String kw) {
        if (kw == null || kw.isBlank()) {
            return;
        }
        String pattern = "%" + kw.trim() + "%";
        String needle = kw.trim().toLowerCase(Locale.ROOT);
        Dialect dialect = sql.dialect();
        Set<Object> ids = new LinkedHashSet<>();
        for (FieldDescriptor field : meta.fields()) {
            if (!field.visible() || !field.lookPermission().granted(user.level())) {
                continue;
            }
            if (field.type() == FieldType.STRING) {
                // PostgreSQL 的 LIKE 区分大小写，MySQL 默认排序规则通常不区分。
                // 关键字搜索统一用 LOWER() 比较，避免同一个关键字在不同数据库上得到「空结果」。
                String statement = "SELECT " + dialect.quote(meta.idColumn())
                                   + " FROM " + dialect.quote(sql.tableName(meta.modelClass()))
                                   + " WHERE LOWER(" + dialect.quote(field.column()) + ") LIKE LOWER(?)";
                ids.addAll(sql.query(new SqlStatement(statement, List.of(pattern)), rs -> rs.getObject(1)));
            } else if (field.type() == FieldType.ASSOCIATION_ID && field.relatedClass() != null) {
                // 外键列存的是 UUID，关键字通常是关联记录的显示名称（如「胡晓天」）。
                // 先在关联选项里匹配名称，再把命中的外键转成本模型的主键。
                List<String> related = new ArrayList<>();
                for (Map.Entry<String, String> option : associations.options(user, meta.modelClass(), field).entrySet()) {
                    String label = option.getValue() == null ? "" : option.getValue();
                    if (label.toLowerCase(Locale.ROOT).contains(needle)) {
                        related.add(option.getKey());
                    }
                }
                if (!related.isEmpty()) {
                    addIdsByColumnValue(meta, field, related, ids);
                }
            } else if (field.type() == FieldType.ENUM && field.enumClass() != null) {
                // 枚举列存的是常量名（DEFENDED），用户搜的是中文标签（已答辩）。
                // 常量名与标签任一命中都算匹配，这样「已答辩」「DEFENDED」「答辩」都能搜到。
                List<String> matched = new ArrayList<>();
                for (Map.Entry<String, String> option : ModelMeta.enumOptions(field).entrySet()) {
                    String label = option.getValue() == null ? "" : option.getValue();
                    if (option.getKey().toLowerCase(Locale.ROOT).contains(needle)
                            || label.toLowerCase(Locale.ROOT).contains(needle)) {
                        matched.add(option.getKey());
                    }
                }
                if (!matched.isEmpty()) {
                    addIdsByColumnValue(meta, field, matched, ids);
                }
            } else if (field.type() == FieldType.NUMBER) {
                // 数字列只做等值匹配：用 LIKE 的话「2」会把 2022、2024、120 全搜出来，噪音太大。
                if (PLAIN_NUMBER.matcher(needle).matches()) {
                    Object value = QueryValues.convert(field, needle);
                    if (value != null) {
                        addIdsByColumnValue(meta, field, List.of(value), ids);
                    }
                }
            } else if (field.type() == FieldType.DATE) {
                // 日期列按「年 / 月 / 日」解析成左闭右开区间，跨库都能走索引。
                LocalDate[] range = dateRangeOf(needle);
                if (range != null) {
                    addIdsByDateRange(meta, field, range[0], range[1], ids);
                }
            }
            if (ids.size() >= KEYWORD_LIMIT) {
                break;
            }
        }
        if (ids.isEmpty()) {
            // 没有任何匹配：用一个必然为假的条件，避免退化成全表。
            // 主键是 UUID，任何真实主键都不会等于哨兵值，所以 IN 恒为空集。
            query.where(meta.idField().name(), OperatorType.IN, List.of(Ids.NO_MATCH));
            return;
        }
        query.in(meta.idField().name(), new ArrayList<>(ids));
    }

    /**
     * 按列值集合反查本模型主键。
     * <p>
     * 服务于三类关键字匹配：外键列（用户搜的是关联记录的显示名，如「胡晓天」）、
     * 枚举列（用户搜的是中文标签「已答辩」，库里存的是常量名 {@code DEFENDED}）、
     * 以及数字列（等值命中，例如班级的入学年份 2022）。
     * 标识符来自模型元数据并按方言加引号，值仍走占位符，不拼字符串。
     */
    private void addIdsByColumnValue(ModelMeta meta, FieldDescriptor field, List<?> values, Set<Object> ids) {
        if (values.isEmpty()) {
            return;
        }
        Dialect dialect = sql.dialect();
        StringBuilder statement = new StringBuilder("SELECT ")
                .append(dialect.quote(meta.idColumn()))
                .append(" FROM ").append(dialect.quote(sql.tableName(meta.modelClass())))
                .append(" WHERE ").append(dialect.quote(field.column())).append(" IN (");
        List<Object> parameters = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                statement.append(", ");
            }
            statement.append('?');
            parameters.add(values.get(i));
        }
        statement.append(')');
        ids.addAll(sql.query(new SqlStatement(statement.toString(), parameters), rs -> rs.getObject(1)));
    }

    /**
     * 日期列按区间反查主键；{@code from} 含、{@code to} 不含，避免跨库比较日期字符串。
     */
    private void addIdsByDateRange(ModelMeta meta, FieldDescriptor field, LocalDate from, LocalDate to, Set<Object> ids) {
        Dialect dialect = sql.dialect();
        String column = dialect.quote(field.column());
        String statement = "SELECT " + dialect.quote(meta.idColumn())
                           + " FROM " + dialect.quote(sql.tableName(meta.modelClass()))
                           + " WHERE " + column + " >= ? AND " + column + " < ?";
        boolean dateOnly = field.javaType() == LocalDate.class;
        List<Object> parameters = new ArrayList<>(2);
        parameters.add(dateOnly ? (Object) from : from.atStartOfDay());
        parameters.add(dateOnly ? (Object) to : to.atStartOfDay());
        ids.addAll(sql.query(new SqlStatement(statement, parameters), rs -> rs.getObject(1)));
    }

    /**
     * 把关键字解析成日期区间：{@code 2022} → 全年，{@code 2022-03} → 当月，
     * {@code 2022-03-05} → 当天。解析不出来返回 {@code null}，该列就不参与匹配。
     */
    private static LocalDate[] dateRangeOf(String needle) {
        try {
            if (YEAR.matcher(needle).matches()) {
                LocalDate start = LocalDate.of(Integer.parseInt(needle), 1, 1);
                return new LocalDate[]{start, start.plusYears(1)};
            }
            Matcher month = YEAR_MONTH.matcher(needle);
            if (month.matches()) {
                LocalDate start = LocalDate.of(Integer.parseInt(month.group(1)),
                        Integer.parseInt(month.group(2)), 1);
                return new LocalDate[]{start, start.plusMonths(1)};
            }
            Matcher day = FULL_DATE.matcher(needle);
            if (day.matches()) {
                LocalDate start = LocalDate.of(Integer.parseInt(day.group(1)),
                        Integer.parseInt(day.group(2)), Integer.parseInt(day.group(3)));
                return new LocalDate[]{start, start.plusDays(1)};
            }
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
        return null;
    }

    /**
     * 枚举筛选值兼容两种写法：常量名（{@code DEFENDED}）与中文标签（{@code 已答辩}）。
     * <p>
     * 前端下拉传常量名；手工请求、脚本或 AI 调用更可能直接给中文标签，
     * 这里统一归一成常量名再交给查询层，避免「明明有这个状态却筛不出东西」。
     */
    private static String enumValue(FieldDescriptor field, String raw) {
        if (raw == null || raw.isBlank() || field.enumClass() == null) {
            return raw;
        }
        String value = raw.trim();
        for (Object constant : field.enumClass().getEnumConstants()) {
            String name = ((Enum<?>) constant).name();
            if (name.equalsIgnoreCase(value)) {
                return name;
            }
        }
        for (Map.Entry<String, String> option : ModelMeta.enumOptions(field).entrySet()) {
            if (option.getValue() != null && option.getValue().equalsIgnoreCase(value)) {
                return option.getKey();
            }
        }
        return value;
    }

    private void applyFilters(LoginUser user, ModelMeta meta, Query<?> query, Map<String, String> params) {
        for (FieldDescriptor field : meta.fields()) {
            if (!field.isColumn() || !field.visible() || !field.lookPermission().granted(user.level())) {
                continue;
            }
            String raw = params.get("f_" + field.name());
            OperatorType operator = OperatorType.parse(params.get("op_" + field.name()));
            if (operator.needsValue() && (raw == null || raw.isBlank())) {
                continue;
            }
            if (field.type() == FieldType.ASSOCIATION_ID
                    && field.relatedClass() != null
                    && isTextOperator(operator)) {
                // 外键列里存的是 UUID，用户输入的是关联记录的显示名称。
                // 先按显示名称解析出候选主键，再让主查询用 IN 过滤；否则
                // `teacher_id LIKE '%胡晓天%'` 永远匹配不到任何行。
                applyAssociationFilter(user, meta, field, operator, query, raw);
                continue;
            }
            raw = enumValue(field, raw);
            Object value = operator.needsValue() ? QueryValues.convert(field, raw) : null;
            query.where(field.name(), operator, value);
        }
    }

    /** 关联字段支持按「显示名称」过滤；大小比较对 UUID 没有业务意义，仍走普通列条件。 */
    private static boolean isTextOperator(OperatorType operator) {
        return operator == OperatorType.LIKE
                || operator == OperatorType.STARTS_WITH
                || operator == OperatorType.ENDS_WITH
                || operator == OperatorType.EQ
                || operator == OperatorType.NE;
    }

    /**
     * 把「关联记录的显示名称 / 主键」解析成外键候选值。
     * <p>
     * 例如论文列表按「指导教师 = 胡晓天」筛选：{@code teacher_id} 列里没有姓名，
     * 只有教师主键。这里通过关联选项拿到 {@code 主键 -> 显示文本}，先做名称匹配，
     * 再用 {@code teacher_id IN (...)} 过滤论文。
     */
    private void applyAssociationFilter(LoginUser user, ModelMeta meta, FieldDescriptor field,
                                        OperatorType operator, Query<?> query, String raw) {
        String needle = raw.trim();
        Map<String, String> options = associations.options(user, meta.modelClass(), field);
        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, String> option : options.entrySet()) {
            if (matchesAssociation(option.getKey(), option.getValue(), operator, needle)) {
                matched.add(option.getKey());
            }
        }
        if (matched.isEmpty()) {
            // 与关键字搜索一致：用哨兵值保证「匹配不到」时是空集，而不是退化成全表。
            query.where(field.name(), OperatorType.IN, List.of(Ids.NO_MATCH));
            return;
        }
        query.where(field.name(), operator == OperatorType.NE ? OperatorType.NOT_IN : OperatorType.IN,
                matched);
    }

    private static boolean matchesAssociation(String id, String label, OperatorType operator, String needle) {
        String text = label == null ? "" : label;
        String lower = needle.toLowerCase(Locale.ROOT);
        return switch (operator) {
            case EQ, NE -> text.equalsIgnoreCase(needle) || id.equalsIgnoreCase(needle);
            case STARTS_WITH -> text.toLowerCase(Locale.ROOT).startsWith(lower)
                    || id.toLowerCase(Locale.ROOT).startsWith(lower);
            case ENDS_WITH -> text.toLowerCase(Locale.ROOT).endsWith(lower)
                    || id.toLowerCase(Locale.ROOT).endsWith(lower);
            default -> text.toLowerCase(Locale.ROOT).contains(lower)
                    || id.toLowerCase(Locale.ROOT).contains(lower);
        };
    }

    private void applySort(LoginUser user, ModelMeta meta, Query<?> query, String sort, String order) {
        String fieldName = sort == null || sort.isBlank() ? meta.idField().name() : sort;
        FieldDescriptor field = meta.field(fieldName)
                .filter(candidate -> candidate.lookPermission().granted(user.level()))
                .filter(candidate -> candidate.isColumn() || candidate.type() == FieldType.AGGREGATION)
                .orElse(meta.idField());
        SortType direction = "asc".equalsIgnoreCase(order) ? SortType.ASC : SortType.DESC;
        query.orderBy(field.name(), direction);
    }

    // ------------------------------------------------------------------ 写入细节

    /**
     * 把请求体写进实体。
     * <p>
     * 只处理「可写列 + 有编辑权限 + 非固定」的字段；固定字段（主键）与
     * {@code editPermission = FORBIDDEN} 的字段（密码哈希、令牌版本）即使出现在请求体里也一律忽略。
     *
     * @param meta      元数据
     * @param entity    实体
     * @param body      请求体
     * @param user      用户
     * @param creating  是否新增
     */
    private void applyBody(ModelMeta meta, Object entity, Map<String, Object> body, LoginUser user, boolean creating) {
        if (body == null) {
            return;
        }
        for (FieldDescriptor field : meta.columnFields()) {
            if (field.fixed() || field.type() == FieldType.MODEL_ID
                || !field.editPermission().granted(user.level())) {
                continue;
            }
            if (!body.containsKey(field.name()) && !body.containsKey(field.column())) {
                continue;
            }
            Object raw = body.containsKey(field.name()) ? body.get(field.name()) : body.get(field.column());
            Object value = convert(field, raw);
            if (field.type() == FieldType.ASSOCIATION_ID && field.javaType() == String.class) {
                String id = value instanceof String text && !text.isBlank() ? text : null;
                if (!Objects.equals(id, field.read(entity))) {
                    associations.validate(user, meta.modelClass(), field, id);
                }
                value = id;
            }
            field.write(entity, value);
        }
    }

    private Object convert(FieldDescriptor field, Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text && text.isBlank()) {
            return field.javaType() == String.class ? "" : null;
        }
        try {
            return mapper.convertValue(raw, field.javaType());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("字段「" + field.text() + "」的值格式不正确：" + raw);
        }
    }

    private void fillCreateDefaults(ModelMeta meta, Object entity, LoginUser user) {
        meta.field("createBy").ifPresent(field -> field.write(entity, user.id()));
        meta.field("createdAt").ifPresent(field -> field.write(entity, LocalDateTime.now()));
        meta.field("updatedAt").ifPresent(field -> field.write(entity, LocalDateTime.now()));
    }

    private void fillUpdateDefaults(ModelMeta meta, Object entity) {
        meta.field("updatedAt").ifPresent(field -> field.write(entity, LocalDateTime.now()));
    }

    private void insert(ModelSession session, Object entity) {
        try {
            session.insert(entity);
        } catch (CheckerException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    private void update(ModelSession session, Object entity) {
        try {
            session.update(entity);
        } catch (CheckerException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 权限与行过滤

    /** 关联附件沿用编辑权限及现有的行范围校验。 */
    public void requireEditableRecord(LoginUser user, String key, String id) {
        ModelMeta meta = registry.requireMeta(key);
        requireEdit(user, meta, key);
        requireView(user, meta, key);
        if (!visibleRow(user, meta, requireEntity(meta, id))) {
            throw ApiException.forbidden("无权修改该记录");
        }
    }

    private void requireView(LoginUser user, ModelMeta meta, String key) {
        if (!metaService.canView(user, meta, key)) {
            throw ApiException.forbidden("没有查看「" + meta.name() + "」的权限");
        }
    }

    private void requireEdit(LoginUser user, ModelMeta meta, String key) {
        if (!metaService.canEdit(user, meta, key)) {
            throw ApiException.forbidden("没有编辑「" + meta.name() + "」的权限");
        }
    }

    private void requireDelete(LoginUser user, ModelMeta meta, String key) {
        if (!metaService.canDelete(user, meta, key)) {
            throw ApiException.forbidden("没有删除「" + meta.name() + "」的权限");
        }
    }

    /**
     * 论文域（{@code th_} 前缀）的操作在模型等级之外还要过权限点。
     * <p>
     * 系统表（用户、角色、审计日志）沿用原来的等级控制：
     * 它们的读写对象是配置而不是论文数据，没必要再叠一层权限点，
     * 也避免升级后管理端因为没授予新权限点而突然用不了。
     */
    private static void requireThesisPermission(LoginUser user, ModelMeta meta, String code, String action) {
        if (meta.table().startsWith("th_") && !user.has(code)) {
            throw ApiException.forbidden("没有「" + action + "」的权限（" + code + "）");
        }
    }

    /**
     * 单行是否在当前用户的数据范围内。
     *
     * @param user   用户
     * @param meta   元数据
     * @param entity 实体
     * @return 可见返回 {@code true}
     */
    private boolean visibleRow(LoginUser user, ModelMeta meta, Object entity) {
        DataScope scope = user.dataScope();
        if (scope == null || scope == DataScope.ALL || user.isAdmin()) {
            return true;
        }
        if (scope == DataScope.DEPT) {
            return true;
        }
        return meta.field("createBy").map(field ->
                Objects.equals(readKey(field, entity), user.id())).orElse(true);
    }

    /**
     * 读取实体上的关联主键。
     * <p>
     * 统一转成字符串比较：主键是 UUID 字符串，但将来换回数字型主键时这段判权逻辑也不用改。
     *
     * @param field  字段
     * @param entity 实体
     * @return 主键文本；为空返回 {@code null}
     */
    private static String readKey(FieldDescriptor field, Object entity) {
        Object value = field.read(entity);
        return value == null ? null : String.valueOf(value);
    }

    private Object requireEntity(ModelMeta meta, Object id) {
        if (id == null) {
            throw ApiException.badRequest("缺少记录主键");
        }
        return sql.findById(meta.modelClass(), id)
                .orElseThrow(() -> ApiException.notFound(meta.name() + " 不存在：" + id));
    }

    private void forEachHook(ModelMeta meta, java.util.function.Consumer<ModelHook> action) {
        for (ModelHook hook : hooks) {
            if (hook.supports(meta.modelClass())) {
                action.accept(hook);
            }
        }
    }

    // ------------------------------------------------------------------ 行输出

    /**
     * 实体转行数据。
     * <p>
     * 字段级权限在这里生效：{@code look = false}（密码哈希）与
     * {@code lookPermission} 不满足的字段不会出现在响应里。
     *
     * @param user   用户
     * @param meta   元数据
     * @param entity 实体
     * @return 行
     */
    private Map<String, Object> toRow(LoginUser user, ModelMeta meta, Object entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", meta.idValue(entity));
        row.put("_display", meta.display(entity));

        for (FieldDescriptor field : meta.fields()) {
            if (!field.visible() || !field.lookPermission().granted(user.level())) {
                continue;
            }
            FieldType type = field.type();
            if (type == FieldType.MODEL_ID) {
                continue;
            }
            Object value = field.read(entity);
            switch (type) {
                case ASSOCIATION_MODEL -> row.put(field.name(), reference(value));
                case ENUM -> {
                    row.put(field.name(), value == null ? null : ((Enum<?>) value).name());
                    row.put(field.name() + "Text", enumText(field, value));
                }
                case DATE -> row.put(field.name(), formatDate(value));
                default -> row.put(field.name(), value);
            }
        }

        if (meta.modelClass() == User.class) {
            row.put("roleIds", roleIdsOf(meta.idValue(entity)));
        }
        return row;
    }

    private static Map<String, Object> reference(Object value) {
        if (value == null) {
            return null;
        }
        ModelMeta related = ModelMeta.of(value.getClass());
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("id", related.idValue(value));
        reference.put("label", related.display(value));
        return reference;
    }

    private static String enumText(FieldDescriptor field, Object value) {
        if (value == null) {
            return "";
        }
        String name = ((Enum<?>) value).name();
        return ModelMeta.enumOptions(field).getOrDefault(name, name);
    }

    private static Object formatDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME);
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DATE_TIME);
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        return value;
    }

    private List<String> roleIdsOf(Object userId) {
        if (userId == null) {
            return List.of();
        }
        Dialect dialect = sql.dialect();
        String statement = "SELECT " + dialect.quote("role_id")
                           + " FROM " + dialect.quote(sql.tableName(UserRole.class))
                           + " WHERE " + dialect.quote("user_id") + " = ?";
        List<String> ids = new ArrayList<>();
        for (Object value : sql.query(new SqlStatement(statement, List.of(userId)), rs -> rs.getObject(1))) {
            if (value != null) {
                ids.add(String.valueOf(value));
            }
        }
        return ids;
    }

    private static int number(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 读取实体主键，供 AI 工具等场景复用。
     *
     * @param meta   元数据
     * @param entity 实体
     * @return 主键（UUID 字符串）；为空返回 {@code null}
     */
    public static String idOf(ModelMeta meta, Object entity) {
        Object value = meta.idValue(entity);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 把任意数字转成 BigDecimal，避免 Jackson 反序列化出科学计数。
     *
     * @param value 值
     * @return 数值
     */
    static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }
}
