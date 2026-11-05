package com.rabbitc.ims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.meta.MetaService;
import com.rabbitc.ims.meta.ModelRegistry;
import com.rabbitc.ims.model.Paper;
import com.rabbitc.ims.model.Student;
import com.rabbitc.ims.model.Teacher;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证外键字段可以按关联记录的显示名称过滤。
 * <p>
 * 页面上的「指导教师」是 {@code teacherId}（存 UUID），用户输入的是姓名。
 * 若直接把姓名拼进 {@code teacher_id LIKE ?}，永远查不到；本测试守住
 * 「先解析姓名到主键，再用 IN 过滤」的行为。
 */
class AssociationFilterTest {

    private ModelSession sql;
    private CrudService crud;

    @BeforeEach
    void setup() throws Exception {
        String url = "jdbc:h2:mem:association_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        sql = ModelSession.sql(() -> DriverManager.getConnection(url));
        sql.synchronizeSchema(Teacher.class, Student.class, User.class, Paper.class);

        ModelRegistry registry = mock(ModelRegistry.class);
        doReturn(Paper.class).when(registry).require("paper");
        when(registry.requireMeta("paper")).thenReturn(ModelMeta.of(Paper.class));
        AssociationAccess associations = new AssociationAccess(sql);
        MetaService meta = new MetaService(sql, registry, associations, null);
        crud = new CrudService(sql, registry, meta, mock(AuditService.class),
                new ObjectMapper(), List.of(), associations);
    }

    @Test
    void filtersForeignKeyByRelatedDisplayName() throws Exception {
        Teacher teacher = new Teacher();
        teacher.name = "胡晓天";
        sql.insert(teacher);

        Paper paper = new Paper();
        paper.teacherId = teacher.id;
        paper.title = "基于 Java 的论文管理系统";
        sql.insert(paper);

        LoginUser admin = new LoginUser("u", "admin", "管理员", null, 4, Set.of(), DataScope.ALL, 0);
        PageResult result = crud.page(admin, "paper", Map.of(
                "size", "10",
                "f_teacherId", "胡晓天",
                "op_teacherId", "LIKE"));

        assertEquals(1, result.total());
        assertEquals(teacher.id, result.rows().get(0).get("teacherId"));
    }

    /** 名字对不上时必须是空集，不能因为忽略条件而退化成全表。 */
    @Test
    void unmatchedNameReturnsEmptyPage() throws Exception {
        Teacher teacher = new Teacher();
        teacher.name = "胡晓天";
        sql.insert(teacher);

        Paper paper = new Paper();
        paper.teacherId = teacher.id;
        paper.title = "论文";
        sql.insert(paper);

        LoginUser admin = new LoginUser("u", "admin", "管理员", null, 4, Set.of(), DataScope.ALL, 0);
        PageResult result = crud.page(admin, "paper", Map.of(
                "size", "10",
                "f_teacherId", "不存在的人",
                "op_teacherId", "LIKE"));

        assertEquals(0, result.total());
    }

    /** 关键字搜索也要能命中关联记录的显示名称，而不只是本表字符串列。 */
    @Test
    void keywordSearchMatchesRelatedDisplayName() throws Exception {
        Teacher teacher = new Teacher();
        teacher.name = "胡晓天";
        sql.insert(teacher);

        Paper paper = new Paper();
        paper.teacherId = teacher.id;
        paper.title = "基于 Java 的论文管理系统";
        sql.insert(paper);

        LoginUser admin = new LoginUser("u", "admin", "管理员", null, 4, Set.of(), DataScope.ALL, 0);
        PageResult result = crud.page(admin, "paper", Map.of("size", "10", "kw", "胡晓天"));

        assertEquals(1, result.total());
        assertEquals(teacher.id, result.rows().get(0).get("teacherId"));
    }
}
