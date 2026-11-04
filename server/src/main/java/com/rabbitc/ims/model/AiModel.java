package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ims.enums.ToolAccess;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

/** 供应商模型与权限配置。密文只由 AI 配置服务读写。 */
@ModelClass(name = "AI 模型", table = "ai_model", text = "${name}",
        look = ModelPermission.FORBIDDEN, edit = ModelPermission.FORBIDDEN, delete = ModelPermission.FORBIDDEN)
public class AiModel {
    @ModelField(column = "id", type = FieldType.MODEL_ID)
    public String id;

    @ModelField(column = "display_name")
    public String name;

    @ModelField(column = "model_id")
    public String modelId;

    @ModelField(column = "provider")
    public String provider;

    @ModelField(column = "base_url")
    public String baseUrl;

    @ModelField(column = "encrypted_key", type = FieldType.IMAGE, look = false,
            editPermission = ModelPermission.FORBIDDEN)
    public byte[] encryptedKey;

    @ModelField(column = "key_name")
    public String keyName;

    /** ACL 与密文同一行更新，避免读取到新密钥和旧权限的组合。 */
    @ModelField(column = "key_access", type = FieldType.IMAGE, look = false)
    public byte[] keyAccess;

    /** 每次保存生成新版本，其他服务实例也能识别旧会话。 */
    @ModelField(column = "config_revision", look = false)
    public String revision;

    @ModelField(column = "enabled", type = FieldType.BOOLEAN)
    public Boolean enabled;

    @ModelField(column = "vision", type = FieldType.BOOLEAN)
    public Boolean vision;

    @ModelField(column = "allow_tools", type = FieldType.BOOLEAN)
    public Boolean allowTools;

    @ModelField(column = "max_tool_access", type = FieldType.ENUM, byEnum = ToolAccess.class)
    public ToolAccess maxToolAccess;

    @ModelField(column = "min_level", type = FieldType.NUMBER)
    public Integer minLevel;
}
