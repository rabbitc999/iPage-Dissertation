package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorRange;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 毕业论文成绩（从论文表拆出来的独立表，与 {@link Paper} 一对一）。
 * <p>
 * 拆表理由：原 {@code dissertation.paper} 把答辩成绩、论文成绩、总成绩作为三列放在选题行里，
 * 结果是（a）成绩无法按答辩批次/评委多次记录，（b）总成绩是另两列的派生值，
 * 存储列必然与源字段漂移（实测 1177/1177 行虽然一致，但没有任何约束阻止它变坏）。
 * 拆出来后：
 * <ul>
 *     <li>一条论文对应一条成绩记录，由唯一索引 {@code uq_th_paper_score_paper} 保证；</li>
 *     <li>总成绩由 {@code PaperScoreHook} 在写入前按「论文×0.6 + 答辩×0.4」算好，
 *         接口层不可直接改（{@code editPermission = FORBIDDEN}）。</li>
 * </ul>
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "论文成绩", table = "th_paper_score", icon = "fa-star-half-alt",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${id}")
public class PaperScore {

    /** 主键。 */
    @ModelField(column = "paper_score_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 论文外键（一论文一条成绩）。 */
    @ModelField(column = "paper_id", type = FieldType.ASSOCIATION_ID, byModel = Paper.class,
            text = "论文", order = 2)
    public String paperId;

    /** 论文对象。 */
    @ModelField(column = "paper_id", type = FieldType.ASSOCIATION_MODEL, byModel = Paper.class,
            text = "论文", order = 3)
    public Paper paper;

    /** 答辩成绩，0–100。 */
    @ModelField(column = "defense_score", type = FieldType.NUMBER, text = "答辩成绩", order = 4)
    @ValidatorRange(min = 0, max = 100, message = "答辩成绩必须在 0 到 100 之间")
    public BigDecimal defenseScore;

    /** 论文成绩，0–100。 */
    @ModelField(column = "thesis_score", type = FieldType.NUMBER, text = "论文成绩", order = 5)
    @ValidatorRange(min = 0, max = 100, message = "论文成绩必须在 0 到 100 之间")
    public BigDecimal thesisScore;

    /**
     * 总成绩 = 论文成绩 × 0.6 + 答辩成绩 × 0.4，四舍五入保留一位小数。
     * <p>
     * 由 {@code PaperScoreHook} 计算，接口层不允许直接写。
     */
    @ModelField(column = "comprehensive_score", type = FieldType.NUMBER, text = "总成绩", order = 6,
            editPermission = ModelPermission.FORBIDDEN)
    public BigDecimal comprehensiveScore;

    /** 备注。 */
    @ModelField(column = "remark", text = "备注", order = 7)
    @ValidatorLength(max = 255, message = "备注最长 255 个字符")
    public String remark;

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
        return comprehensiveScore == null ? "论文成绩" : "总评 " + comprehensiveScore;
    }
}
