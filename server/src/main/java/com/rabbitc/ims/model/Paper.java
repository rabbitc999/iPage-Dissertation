package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.PaperStatus;
import com.rabbitc.ims.enums.TopicDifficulty;
import com.rabbitc.ims.enums.TopicNature;
import com.rabbitc.ims.enums.TopicSource;
import com.rabbitc.ims.enums.TopicType;

import java.time.LocalDateTime;

/**
 * 毕业论文（与 {@link Student} 一对一）。
 * <p>
 * 迁移自 {@code dissertation.paper}。相对原表：
 * <ul>
 *     <li><b>成绩拆出去</b>：原表把答辩成绩、论文成绩、总成绩三列塞在论文行里，
 *         成绩一旦要按批次/评委/多次答辩记录就没处放。移植后成绩独立成
 *         {@link PaperScore}，本表只保留「选题」这层信息。</li>
 *     <li><b>新增状态</b>：原库用「题目是空串 + 成绩全 NULL」表示还没选题（实测 9 行），
 *         空串既不能进唯一约束也不好查询，改用 {@link PaperStatus} 表达。</li>
 *     <li><b>课题属性</b>：{@code paper_nature/type/source/difficulty} 四个单字母编码
 *         原样保留在库里（A/B/C），含义由 {@link TopicNature}、{@link TopicType}、
 *         {@link TopicSource}、{@link TopicDifficulty} 四个枚举给出中文，
 *         页面不再显示裸字母。</li>
 * </ul>
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "毕业论文", table = "th_paper", icon = "fa-book",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${title}")
public class Paper {

    /** 主键。 */
    @ModelField(column = "paper_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 学生外键（一名学生只有一篇论文，由唯一索引 uq_th_paper_student 保证）。 */
    @ModelField(column = "student_id", type = FieldType.ASSOCIATION_ID, byModel = Student.class,
            text = "学生", order = 2)
    public String studentId;

    /** 学生对象。 */
    @ModelField(column = "student_id", type = FieldType.ASSOCIATION_MODEL, byModel = Student.class,
            text = "学生", order = 3)
    public Student student;

    /** 指导教师外键。 */
    @ModelField(column = "teacher_id", type = FieldType.ASSOCIATION_ID, byModel = Teacher.class,
            text = "指导教师", order = 4)
    public String teacherId;

    /** 指导教师对象。 */
    @ModelField(column = "teacher_id", type = FieldType.ASSOCIATION_MODEL, byModel = Teacher.class,
            text = "指导教师", order = 5)
    public Teacher teacher;

    /** 论文状态。 */
    @ModelField(column = "paper_status", type = FieldType.ENUM, byEnum = PaperStatus.class,
            text = "状态", order = 6)
    public PaperStatus status = PaperStatus.NOT_SELECTED;

    /** 论文题目，未选题时为空。 */
    @ModelField(column = "title", text = "论文题目", order = 7)
    @ValidatorLength(max = 255, message = "论文题目最长 255 个字符")
    public String title;

    /** 课题性质。 */
    @ModelField(column = "topic_nature", type = FieldType.ENUM, byEnum = TopicNature.class,
            text = "课题性质", order = 8)
    public TopicNature nature;

    /** 课题类型。 */
    @ModelField(column = "topic_type", type = FieldType.ENUM, byEnum = TopicType.class,
            text = "课题类型", order = 9)
    public TopicType type;

    /** 课题来源。 */
    @ModelField(column = "topic_source", type = FieldType.ENUM, byEnum = TopicSource.class,
            text = "课题来源", order = 10)
    public TopicSource source;

    /** 课题难度（A 最难、E 最易）。 */
    @ModelField(column = "topic_difficulty", type = FieldType.ENUM, byEnum = TopicDifficulty.class,
            text = "课题难度", order = 11)
    public TopicDifficulty difficulty;

    /** 创建人。 */
    @ModelField(column = "create_by", type = FieldType.ASSOCIATION_ID, byModel = User.class,
            text = "创建人", order = 19, look = false)
    public String createBy;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 20, look = false)
    public LocalDateTime createdAt;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 21, look = false)
    public LocalDateTime updatedAt;

    @Override
    public String toString() {
        return title == null || title.isBlank() ? "未选题" : title;
    }
}
