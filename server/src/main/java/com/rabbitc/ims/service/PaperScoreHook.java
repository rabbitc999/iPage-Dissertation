package com.rabbitc.ims.service;

import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ims.model.PaperScore;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.web.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 论文成绩模型的业务规则：总成绩一律由分项成绩算出。
 * <p>
 * 原库把总成绩当成一个普通列，靠录入时手工填；虽然实测 1177 行都恰好等于
 * 「论文 × 0.6 + 答辩 × 0.4」，但没有任何东西阻止它被改错。
 * 现在总成绩字段在接口层是 {@code FORBIDDEN}（见 {@link PaperScore}），
 * 只允许本钩子按固定权重写入，因此不存在「总成绩与分项成绩不一致」这种状态。
 * <p>
 * 权重改动（例如学校把答辩占比调到 50%）只改这里的常量，历史数据在下次保存时自动重算；
 * 若需要保留历史权重，应把权重与计算时间一起落库，而不是硬编码。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class PaperScoreHook implements ModelHook {

    /** 论文成绩权重。 */
    private static final BigDecimal THESIS_WEIGHT = new BigDecimal("0.6");

    /** 答辩成绩权重。 */
    private static final BigDecimal DEFENSE_WEIGHT = new BigDecimal("0.4");

    @Override
    public boolean supports(Class<?> modelClass) {
        return modelClass == PaperScore.class;
    }

    @Override
    public void beforeCreate(ModelMeta meta, Object entity, Map<String, Object> body, LoginUser user) {
        requireScorePermission(user);
        recalculate((PaperScore) entity);
    }

    @Override
    public void beforeUpdate(ModelMeta meta, Object entity, Object before,
                             Map<String, Object> body, LoginUser user) {
        requireScorePermission(user);
        recalculate((PaperScore) entity);
    }

    /**
     * 改成绩额外要权限点。
     * <p>
     * 模型上的 {@code edit} 等级只能表达「谁能编辑成绩表」，区分不了「谁能改分数」；
     * 成绩是本系统里最敏感的数据，单独给一个开关，方便只让教务或指定教师改动。
     *
     * @param user 操作人
     */
    private static void requireScorePermission(LoginUser user) {
        if (!user.has("thesis:score:manage")) {
            throw ApiException.forbidden("没有「录入与修改成绩」的权限（thesis:score:manage）");
        }
    }

    /**
     * 按分项成绩重算总成绩；任一分项缺失时总成绩同样置空，避免出现「只有一半成绩却有总分」。
     *
     * @param score 待写入的成绩
     */
    private void recalculate(PaperScore score) {
        if (score.thesisScore == null || score.defenseScore == null) {
            score.comprehensiveScore = null;
            return;
        }
        score.comprehensiveScore = score.thesisScore.multiply(THESIS_WEIGHT)
                .add(score.defenseScore.multiply(DEFENSE_WEIGHT))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
