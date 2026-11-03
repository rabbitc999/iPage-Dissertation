package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * 系统图像设置：键值对，存站点名、Logo、登录页背景这类「整站视觉」配置。
 * <p>
 * 值是 IMAGE 类型的 String 形态——相对链接、绝对链接、{@code data:} 内联 URI 三种来源都兼容。
 * 注意 String 列在 ipage 里是 VARCHAR(255)，{@code data:} URI 过长会被截断，
 * 大图建议走 {@code /api/files} 上传后引用相对链接。
 * <p>
 * 本模型不暴露为通用 CRUD（见 {@code ModelRegistry#NOT_EXPOSED}），
 * 读写都经过 {@code SettingController}：改值需要 {@code system:setting:manage} 权限点，
 * 读值只允许键白名单（避免把全部内部键值泄漏给未登录访客）。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "系统设置", table = "sys_setting", icon = "fa-picture-o",
        look = ModelPermission.FORBIDDEN, edit = ModelPermission.FORBIDDEN,
        delete = ModelPermission.FORBIDDEN, text = "${settingKey}")
public class SysSetting {

    /** 主键，UUID，由 ipage 在插入时自动赋值。 */
    @ModelField(column = "setting_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 设置键，全站唯一，如 {@code siteName}、{@code logoUrl}。 */
    @ModelField(column = "setting_key", text = "键", order = 2)
    @ValidatorNotNull(message = "设置键不能为空")
    @ValidatorLength(max = 64, message = "设置键最长 64 个字符")
    public String settingKey;

    /** 设置值：图像链接或纯文本。 */
    @ModelField(column = "setting_value", type = FieldType.IMAGE, text = "值", order = 3)
    public String settingValue;

    /** 最后更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 4,
            look = false)
    public LocalDateTime updatedAt;
}
