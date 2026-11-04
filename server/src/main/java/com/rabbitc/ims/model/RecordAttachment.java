package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

/** 文件索引独立于 BLOB，附件权限由所属工单或资产记录决定。 */
@ModelClass(name = "业务附件", table = "ims_record_attachment", look = ModelPermission.FORBIDDEN,
        edit = ModelPermission.FORBIDDEN, delete = ModelPermission.FORBIDDEN)
public class RecordAttachment {
    @ModelField(column = "attachment_id", type = FieldType.MODEL_ID)
    public String id;

    @ModelField(column = "file_id")
    public String fileId;

    @ModelField(column = "ticket_id")
    public String ticketId;

    @ModelField(column = "asset_id")
    public String assetId;

    @ModelField(column = "file_name")
    public String name;

    @ModelField(column = "content_type")
    public String contentType;

    @ModelField(column = "byte_size", type = FieldType.NUMBER)
    public Long size;
}
