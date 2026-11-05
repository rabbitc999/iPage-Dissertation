package com.rabbitc.ims.seed;

import com.rabbitc.ims.model.College;
import com.rabbitc.ims.model.Direction;
import com.rabbitc.ims.model.Paper;
import com.rabbitc.ims.model.PaperScore;
import com.rabbitc.ims.model.Profession;
import com.rabbitc.ims.model.SchoolClass;
import com.rabbitc.ims.model.Student;
import com.rabbitc.ims.model.Teacher;
import com.rabbitc.ims.model.TeacherDirection;
import com.rabbitc.ims.model.TeacherProfession;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.reflect.FieldDescriptor;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ipage.sql.SqlStatement;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 在 iPage 补表后检查历史数据，再补充业务唯一索引与外键。
 * <p>
 * iPage 的 {@code synchronizeSchema} 只建表、只补列，不建索引与约束；唯一性与引用完整性
 * 属于业务规则，集中放在这里，保证「模型改了、约束也跟着改」，不需要人工记迁移脚本。
 * <p>
 * <b>本文件已被 iPage-IMS 修改（2026-11-05）：</b>规则从「单列」扩展为「列组合」，
 * 以支持教师—研究方向、教师—可带专业这类关联表上的复合唯一约束。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class BusinessConstraints {

    /**
     * 一条约束规则。
     * <p>
     * {@code target} 为空表示唯一索引（{@code fields} 可以是多列，即复合唯一）；
     * 非空表示外键（此时 {@code fields} 只有一列）。
     *
     * @param name   约束名
     * @param source 模型类
     * @param fields 字段名（Java 字段名，不是列名）
     * @param target 外键目标模型类，唯一索引时为 {@code null}
     */
    private record Rule(String name, Class<?> source, List<String> fields, Class<?> target) {

        /** 唯一索引规则，支持复合列。 */
        static Rule unique(String name, Class<?> source, String... fields) {
            return new Rule(name, source, List.of(fields), null);
        }

        /** 外键规则。 */
        static Rule foreign(String name, Class<?> source, String field, Class<?> target) {
            return new Rule(name, source, List.of(field), target);
        }
    }

    private static final List<Rule> RULES = List.of(
            // ---------------- 论文管理域（th_ 前缀） ----------------
            // 业务唯一性：原库这些「唯一」全是巧合，没有任何约束阻止重复
            Rule.unique("uq_th_college_name", College.class, "name"),
            Rule.unique("uq_th_profession_name", Profession.class, "name"),
            Rule.unique("uq_th_class_name", SchoolClass.class, "name"),
            Rule.unique("uq_th_student_no", Student.class, "studentNo"),
            Rule.unique("uq_th_teacher_no", Teacher.class, "teacherNo"),
            Rule.unique("uq_th_direction_name", Direction.class, "name"),
            // 关联表的复合唯一：原库把两个唯一键的名字写反了，这里改正
            Rule.unique("uq_th_teacher_direction", TeacherDirection.class, "teacherId", "directionId"),
            Rule.unique("uq_th_teacher_profession", TeacherProfession.class, "teacherId", "professionId"),
            // 一名学生一篇论文；一篇论文一条成绩
            Rule.unique("uq_th_paper_student", Paper.class, "studentId"),
            Rule.unique("uq_th_paper_score_paper", PaperScore.class, "paperId"),
            // 引用完整性：原库 student.teacher_id 只有索引没有外键
            Rule.foreign("fk_th_profession_college", Profession.class, "collegeId", College.class),
            Rule.foreign("fk_th_class_profession", SchoolClass.class, "professionId", Profession.class),
            Rule.foreign("fk_th_student_class", Student.class, "classId", SchoolClass.class),
            Rule.foreign("fk_th_td_teacher", TeacherDirection.class, "teacherId", Teacher.class),
            Rule.foreign("fk_th_td_direction", TeacherDirection.class, "directionId", Direction.class),
            Rule.foreign("fk_th_tp_teacher", TeacherProfession.class, "teacherId", Teacher.class),
            Rule.foreign("fk_th_tp_profession", TeacherProfession.class, "professionId", Profession.class),
            Rule.foreign("fk_th_paper_student", Paper.class, "studentId", Student.class),
            Rule.foreign("fk_th_paper_teacher", Paper.class, "teacherId", Teacher.class),
            Rule.foreign("fk_th_paper_score_paper", PaperScore.class, "paperId", Paper.class));

    private final DataSource dataSource;
    private final ModelSession sql;

    public BusinessConstraints(DataSource dataSource, ModelSession sql) {
        this.dataSource = dataSource;
        this.sql = sql;
    }

    public void synchronize() throws SQLException {
        List<Rule> missing = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            for (Rule rule : RULES) {
                if (!exists(connection, rule)) {
                    missing.add(rule);
                }
            }
        }
        preflight(missing);
        if (missing.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (Rule rule : missing) {
                if (!exists(connection, rule)) {
                    statement.executeUpdate(ddl(rule));
                }
            }
        }
    }

    private void preflight(List<Rule> missing) {
        Map<Class<?>, List<Object>> rows = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (Rule rule : missing) {
            ModelMeta model = ModelMeta.of(rule.source());
            List<FieldDescriptor> fields = new ArrayList<>();
            for (String name : rule.fields()) {
                fields.add(model.field(name).orElseThrow(
                        () -> new IllegalStateException("约束 " + rule.name() + " 引用了不存在的字段：" + name)));
            }
            List<Object> data = rows.computeIfAbsent(rule.source(), this::load);
            long invalid = rule.target() == null
                    ? countDuplicateRows(fields, data)
                    : countDanglingRows(rule, fields.get(0), data);
            if (invalid > 0) {
                List<String> texts = new ArrayList<>();
                fields.forEach(field -> texts.add(field.text()));
                problems.add(model.name() + "／" + String.join("+", texts) + "：" + invalid
                        + (rule.target() == null ? " 条重复" : " 条失效关联"));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("历史数据未通过约束检查，请修复后重启。"
                    + String.join("；", problems));
        }
    }

    /** 复合唯一：任一列为 {@code null} 的行不参与判重（与 SQL 唯一索引语义一致）。 */
    private long countDuplicateRows(List<FieldDescriptor> fields, List<Object> data) {
        Set<List<Object>> seen = new HashSet<>();
        long invalid = 0;
        for (Object row : data) {
            List<Object> key = new ArrayList<>(fields.size());
            boolean complete = true;
            for (FieldDescriptor field : fields) {
                Object value = field.read(row);
                if (value == null) {
                    complete = false;
                    break;
                }
                key.add(value);
            }
            if (complete && !seen.add(key)) {
                invalid++;
            }
        }
        return invalid;
    }

    /** 外键：只读目标主键，避免启动时把附件的文件内容载入内存。 */
    private long countDanglingRows(Rule rule, FieldDescriptor field, List<Object> data) {
        ModelMeta target = ModelMeta.of(rule.target());
        Set<Object> keys = new HashSet<>(
                sql.query(SqlStatement.options(target, null, null, sql.dialect()), rs -> rs.getObject(1)));
        long invalid = 0;
        for (Object row : data) {
            Object value = field.read(row);
            if (value != null && !keys.contains(value)) {
                invalid++;
            }
        }
        return invalid;
    }

    private List<Object> load(Class<?> type) {
        return sql.selectList(Query.of(ModelMeta.of(type), sql.dialect()));
    }

    private boolean exists(Connection connection, Rule rule) throws SQLException {
        var metadata = connection.getMetaData();
        String table = ModelMeta.of(rule.source()).table();
        if (quote(table).equals(table) && metadata.storesUpperCaseIdentifiers()) {
            table = table.toUpperCase(Locale.ROOT);
        }
        try (ResultSet result = rule.target() == null
                ? metadata.getIndexInfo(connection.getCatalog(), connection.getSchema(), table, true, false)
                : metadata.getImportedKeys(connection.getCatalog(), connection.getSchema(), table)) {
            while (result.next()) {
                if (rule.name().equalsIgnoreCase(result.getString(rule.target() == null ? "INDEX_NAME" : "FK_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String ddl(Rule rule) {
        ModelMeta model = ModelMeta.of(rule.source());
        String name = quote(rule.name());
        String table = quote(model.table());
        List<String> columns = new ArrayList<>();
        for (String field : rule.fields()) {
            columns.add(quote(model.field(field).orElseThrow().column()));
        }
        if (rule.target() == null) {
            return "CREATE UNIQUE INDEX " + name + " ON " + table + " (" + String.join(", ", columns) + ")";
        }
        ModelMeta target = ModelMeta.of(rule.target());
        return "ALTER TABLE " + table + " ADD CONSTRAINT " + name + " FOREIGN KEY (" + columns.get(0)
                + ") REFERENCES " + quote(target.table()) + " (" + quote(target.idColumn()) + ")";
    }

    private String quote(String name) {
        return sql.dialect().quote(name);
    }
}
