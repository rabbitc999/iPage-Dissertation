package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * 用户级界面配置：列显隐、每页条数。
 * <p>
 * 这是「自定义界面」的落点之一：同一张表，不同用户可以有自己的列顺序与分页偏好。
 * 配置以紧凑 JSON 存在 {@code config} 列里（String 列在 ipage 中是 VARCHAR(255)，够用即可）。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "界面配置", table = "sys_view_config", icon = "fa-sliders-h",
        look = ModelPermission.STUDENT, edit = ModelPermission.STUDENT,
        delete = ModelPermission.STUDENT, text = "${modelKey}")
public class ViewConfig {

    /** 主键。 */
    @ModelField(column = "view_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 归属用户。 */
    @ModelField(column = "owner_id", type = FieldType.NUMBER, text = "用户", order = 2)
    public String ownerId;

    /** 模型 key。 */
    @ModelField(column = "model_key", text = "模型", order = 3)
    public String modelKey;

    /** 配置 JSON。 */
    @ModelField(column = "config", text = "配置", order = 4)
    public String config;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 5)
    public LocalDateTime updatedAt;
}
