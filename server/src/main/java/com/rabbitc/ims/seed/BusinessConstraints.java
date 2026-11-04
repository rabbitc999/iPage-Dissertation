package com.rabbitc.ims.seed;

import com.rabbitc.ims.model.Asset;
import com.rabbitc.ims.model.Department;
import com.rabbitc.ims.model.Employee;
import com.rabbitc.ims.model.Ticket;
import com.rabbitc.ims.model.RecordAttachment;
import com.rabbitc.ims.model.SysFile;
import com.rabbitc.ims.model.User;
import com.rabbitc.ipage.jdbc.ModelSession;
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

/** 在 iPage 补表后检查历史数据，再补充业务唯一索引与外键。 */
@Component
public class BusinessConstraints {
    private record Rule(String name, Class<?> source, String field, Class<?> target) {}

    private static final List<Rule> RULES = List.of(
            new Rule("uq_ims_department_code", Department.class, "code", null),
            new Rule("uq_ims_employee_no", Employee.class, "empNo", null),
            new Rule("uq_ims_asset_code", Asset.class, "code", null),
            new Rule("fk_ims_employee_dept", Employee.class, "deptId", Department.class),
            new Rule("fk_ims_asset_dept", Asset.class, "deptId", Department.class),
            new Rule("fk_ims_asset_keeper", Asset.class, "keeperId", Employee.class),
            new Rule("fk_ims_ticket_dept", Ticket.class, "deptId", Department.class),
            new Rule("fk_ims_ticket_assignee", Ticket.class, "assigneeId", User.class),
            new Rule("fk_ims_user_dept", User.class, "deptId", Department.class),
            new Rule("fk_ims_attachment_file", RecordAttachment.class, "fileId", SysFile.class),
            new Rule("fk_ims_attachment_ticket", RecordAttachment.class, "ticketId", Ticket.class),
            new Rule("fk_ims_attachment_asset", RecordAttachment.class, "assetId", Asset.class));

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
            var field = model.field(rule.field()).orElseThrow();
            Set<Object> keys = new HashSet<>();
            if (rule.target() != null) {
                ModelMeta target = ModelMeta.of(rule.target());
                // 校验外键只读取目标主键，避免启动时把附件的文件内容载入内存。
                keys.addAll(sql.query(SqlStatement.options(target, null, null, sql.dialect()), rs -> rs.getObject(1)));
            }
            long invalid = 0;
            for (Object row : rows.computeIfAbsent(rule.source(), this::load)) {
                Object value = field.read(row);
                if (value != null && (rule.target() == null ? !keys.add(value) : !keys.contains(value))) {
                    invalid++;
                }
            }
            if (invalid > 0) {
                problems.add(model.name() + "／" + field.text() + "：" + invalid
                        + (rule.target() == null ? " 条重复" : " 条失效关联"));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("历史数据未通过约束检查，请修复后重启。"
                    + String.join("；", problems));
        }
    }

    private List<Object> load(Class<?> type) {
        return sql.selectList(Query.of(type, sql.dialect()));
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
        String column = quote(model.field(rule.field()).orElseThrow().column());
        if (rule.target() == null) {
            return "CREATE UNIQUE INDEX " + name + " ON " + table + " (" + column + ")";
        }
        ModelMeta target = ModelMeta.of(rule.target());
        String onDelete = rule.source() == RecordAttachment.class && rule.target() != SysFile.class
                ? " ON DELETE CASCADE" : "";
        return "ALTER TABLE " + table + " ADD CONSTRAINT " + name + " FOREIGN KEY (" + column
                + ") REFERENCES " + quote(target.table()) + " (" + quote(target.idColumn()) + ")" + onDelete;
    }

    private String quote(String name) {
        return sql.dialect().quote(name);
    }
}
