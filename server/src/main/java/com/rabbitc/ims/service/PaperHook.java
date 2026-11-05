package com.rabbitc.ims.service;

import com.rabbitc.ipage.annotation.ModelEnum;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ims.enums.PaperStatus;
import com.rabbitc.ims.model.Paper;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.web.ApiException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 论文模型的业务规则。
 * <p>
 * 只做两件事，其余（权限、数据范围、审计）都交给通用流程：
 * <ol>
 *     <li>把表单里传上来的空串归一成 {@code null}。原库用空串表示「还没选题」，
 *         但空串与唯一约束、与「未填写」判断都冲突；</li>
 *     <li>保证「已选题」之后题目必须有内容——数据库层没法表达这种跨列规则，
 *         放在这里比放在前端可靠。</li>
 * </ol>
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class PaperHook implements ModelHook {

    @Override
    public boolean supports(Class<?> modelClass) {
        return modelClass == Paper.class;
    }

    @Override
    public void beforeCreate(ModelMeta meta, Object entity, Map<String, Object> body, LoginUser user) {
        normalize((Paper) entity);
    }

    @Override
    public void beforeUpdate(ModelMeta meta, Object entity, Object before,
                             Map<String, Object> body, LoginUser user) {
        Paper next = (Paper) entity;
        normalize(next);
        // 状态流转（未选题 → 已选题 → 已答辩 → 已归档）属于审核动作，单独要权限点；
        // 只改题目、课题属性这类字段不受影响。
        Paper previous = (Paper) before;
        if (previous != null && previous.status != next.status && !user.has("thesis:paper:review")) {
            throw ApiException.forbidden("没有「审核论文状态」的权限（thesis:paper:review）");
        }
    }

    private void normalize(Paper paper) {
        // 四个课题属性已是枚举：接口传来的空白值在 applyBody 阶段就被转成 null，
        // 这里只需要处理题目——它是唯一用「空」表达「还没选题」的字符串列。
        paper.title = blankToNull(paper.title);
        if (paper.status == null) {
            paper.status = PaperStatus.NOT_SELECTED;
        }
        if (paper.status != PaperStatus.NOT_SELECTED && paper.title == null) {
            throw ApiException.badRequest("状态为「" + label(paper.status) + "」的论文必须填写题目");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 取枚举上的中文名，避免错误提示里冒出 {@code SELECTED} 这种常量名。 */
    private static String label(PaperStatus status) {
        try {
            ModelEnum annotation = PaperStatus.class.getField(status.name())
                    .getAnnotation(ModelEnum.class);
            return annotation == null ? status.name() : annotation.text();
        } catch (NoSuchFieldException e) {
            return status.name();
        }
    }
}
