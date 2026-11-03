package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * 上传文件记录：元数据 + 内容二进制一体落库。
 * <p>
 * 内容存放在 {@code file_data} 列，字段类型是 {@link FieldType#IMAGE} 的 byte[] 形态，
 * 由 ipage 映射为 BLOB（H2）/ MEDIUMBLOB（MySQL）/ BYTEA（PostgreSQL）。
 * 这样做的好处是单一数据源——备份库里就带着全部文件，不需要额外的磁盘目录约定；
 * 代价是不适合放超大文件，因此上传入口限定了 10MB。
 * <p>
 * 本模型不暴露为通用 CRUD（见 {@code ModelRegistry#NOT_EXPOSED}），
 * 所有读写都经过 {@code FileController}，以便校验大小、设置响应头与做删除鉴权。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "文件", table = "sys_file", icon = "fa-file-image-o",
        look = ModelPermission.FORBIDDEN, edit = ModelPermission.FORBIDDEN,
        delete = ModelPermission.FORBIDDEN, text = "${name}")
public class SysFile {

    /** 主键，UUID，由 ipage 在插入时自动赋值。 */
    @ModelField(column = "file_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 原始文件名，下载时作为附件名返回。 */
    @ModelField(column = "file_name", text = "文件名", order = 2)
    @ValidatorNotNull(message = "文件名不能为空")
    @ValidatorLength(max = 200, message = "文件名最长 200 个字符")
    public String name;

    /** MIME 类型，如 image/png；响应时原样写回 Content-Type。 */
    @ModelField(column = "content_type", text = "类型", order = 3)
    public String contentType;

    /** 文件字节数，列表场景用于展示，避免把大字段查出来。 */
    @ModelField(column = "file_size", type = FieldType.NUMBER, text = "大小", order = 4)
    public Long size;

    /**
     * 文件内容本体。IMAGE 类型的 byte[] 形态，只存不查——列表与元数据接口
     * 都不会 SELECT 这一列，下载接口单独按主键取整行。
     */
    @ModelField(column = "file_data", type = FieldType.IMAGE, text = "内容", order = 5,
            look = false, editPermission = ModelPermission.FORBIDDEN)
    public byte[] data;

    /** 上传者用户 ID。 */
    @ModelField(column = "create_by", text = "上传者", order = 6, look = false)
    public String createBy;

    /** 上传时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "上传时间", order = 7,
            look = false)
    public LocalDateTime createdAt;
}
