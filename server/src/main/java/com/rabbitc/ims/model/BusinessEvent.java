package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/** 内部事务发件箱。消息确认后删除对应记录。 */
@ModelClass(name = "业务事件", table = "sys_business_event", look = ModelPermission.FORBIDDEN,
        edit = ModelPermission.FORBIDDEN, delete = ModelPermission.FORBIDDEN)
public class BusinessEvent {
    @ModelField(column = "event_id", type = FieldType.MODEL_ID)
    public String id;

    @ModelField(column = "model_key")
    public String model;

    @ModelField(column = "event_action")
    public String action;

    @ModelField(column = "record_id")
    public String recordId;

    @ModelField(column = "occurred_at", type = FieldType.DATE)
    public LocalDateTime occurredAt;
}
