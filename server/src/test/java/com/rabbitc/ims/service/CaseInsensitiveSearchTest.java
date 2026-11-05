package com.rabbitc.ims.service;

import com.rabbitc.ipage.dialect.PostgresDialect;
import com.rabbitc.ipage.enums.OperatorType;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ipage.sql.SqlStatement;
import com.rabbitc.ims.model.Student;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证模糊匹配不再依赖数据库的排序规则。
 * <p>
 * PostgreSQL 的 {@code LIKE} 区分大小写，MySQL 默认排序规则通常不区分；
 * 统一用 {@code LOWER(...) LIKE LOWER(...)} 后，同一个关键字在两种库上都能命中。
 */
class CaseInsensitiveSearchTest {

    /** 「包含」筛选：列与参数都转成小写，参数仍是原始值（由 SQL 函数处理）。 */
    @Test
    void likeConditionLowersColumnAndParameter() {
        Query<Student> query = Query.of(Student.class, PostgresDialect.INSTANCE).like("name", "Java");
        SqlStatement statement = SqlStatement.select(query, true, PostgresDialect.INSTANCE);

        assertTrue(statement.sql().contains(
                        "LOWER(t_th_student.\"name\") LIKE LOWER(CONCAT('%', ?, '%'))"),
                statement.sql());
        assertEquals("Java", statement.parameters().get(0));
    }

    /** 开头匹配同样大小写不敏感，避免「筛选条件比关键字搜索更严格」的双标行为。 */
    @Test
    void startsWithConditionLowersColumn() {
        Query<Student> query = Query.of(Student.class, PostgresDialect.INSTANCE)
                .where("name", OperatorType.STARTS_WITH, "Ja");
        SqlStatement statement = SqlStatement.select(query, true, PostgresDialect.INSTANCE);

        assertTrue(statement.sql().contains(
                        "LOWER(t_th_student.\"name\") LIKE LOWER(CONCAT(?, '%'))"),
                statement.sql());
    }
}
