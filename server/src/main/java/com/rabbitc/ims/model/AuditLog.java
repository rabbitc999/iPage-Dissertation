package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * 操作审计日志。
 * <p>
 * 由通用 CRUD 自动写入，页面只读：{@code edit = FORBIDDEN} 让所有等级的编辑请求都被拒绝。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "审计日志", table = "sys_audit_log", icon = "fa-history",
        look = ModelPermission.ADMIN, edit = ModelPermission.FORBIDDEN,
        delete = ModelPermission.SUPER_ADMIN, text = "${action}")
public class AuditLog {

    /** 主键。 */
    @ModelField(column = "log_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 操作人主键。 */
    @ModelField(column = "actor_id", type = FieldType.NUMBER, text = "操作人ID", order = 2)
    public String actorId;

    /** 操作人姓名。 */
    @ModelField(column = "actor_name", text = "操作人", order = 3)
    public String actorName;

    /** 动作：CREATE / UPDATE / DELETE / LOGIN。 */
    @ModelField(column = "action_name", text = "动作", order = 4)
    public String action;

    /** 模型名称。 */
    @ModelField(column = "model_name", text = "模型", order = 5)
    public String model;

    /** 目标记录主键。 */
    @ModelField(column = "target_id", text = "记录ID", order = 6)
    public String targetId;

    /** 变更摘要。 */
    @ModelField(column = "detail", text = "详情", order = 7)
    public String detail;

    /** 来源地址。 */
    @ModelField(column = "ip", text = "来源", order = 8)
    public String ip;

    /** 发生时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "时间", order = 9)
    public LocalDateTime createdAt;
}
