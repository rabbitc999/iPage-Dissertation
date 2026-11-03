package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.TicketPriority;
import com.rabbitc.ims.enums.TicketStatus;
import com.rabbitc.ims.enums.TicketType;

import java.time.LocalDateTime;

/**
 * 服务工单。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "工单", table = "ims_ticket", icon = "fa-life-ring",
        look = ModelPermission.LEAST, edit = ModelPermission.LEAST, delete = ModelPermission.ADMIN,
        text = "${title}")
public class Ticket {

    /** 主键。 */
    @ModelField(column = "ticket_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 标题。 */
    @ModelField(column = "title", text = "标题", order = 2)
    @ValidatorNotNull(message = "标题不能为空")
    @ValidatorLength(max = 120, message = "标题最长 120 个字符")
    public String title;

    /** 类型。 */
    @ModelField(column = "ticket_type", type = FieldType.ENUM, byEnum = TicketType.class,
            text = "类型", order = 3)
    public TicketType type = TicketType.INCIDENT;

    /** 优先级。 */
    @ModelField(column = "priority", type = FieldType.ENUM, byEnum = TicketPriority.class,
            text = "优先级", order = 4)
    public TicketPriority priority = TicketPriority.NORMAL;

    /** 状态。 */
    @ModelField(column = "ticket_status", type = FieldType.ENUM, byEnum = TicketStatus.class,
            text = "状态", order = 5)
    public TicketStatus status = TicketStatus.OPEN;

    /** 所属部门外键。 */
    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_ID, byModel = Department.class,
            text = "所属部门", order = 6)
    public String deptId;

    /** 所属部门对象。 */
    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_MODEL, byModel = Department.class,
            text = "所属部门", order = 7)
    public Department dept;

    /** 处理人外键。 */
    @ModelField(column = "assignee_id", type = FieldType.ASSOCIATION_ID, byModel = User.class,
            text = "处理人", order = 8)
    public String assigneeId;

    /** 处理人对象。 */
    @ModelField(column = "assignee_id", type = FieldType.ASSOCIATION_MODEL, byModel = User.class,
            text = "处理人", order = 9)
    public User assignee;

    /** 描述。 */
    @ModelField(column = "description", text = "描述", order = 10)
    public String description;

    /** 创建人。 */
    @ModelField(column = "create_by", type = FieldType.NUMBER, text = "创建人", order = 20, look = false)
    public String createBy;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 21, look = false)
    public LocalDateTime createdAt;

    /** 最后修改时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 22, look = false)
    public LocalDateTime updatedAt;
}
