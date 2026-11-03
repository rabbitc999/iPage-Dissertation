package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * Skill 注册表。
 * <p>
 * {@code directory} 指向一个包含 {@code SKILL.md} 的目录；只加载文本指令，
 * 不执行脚本，也不因为 Skill 文案而获得额外工具权限——这是 EmbedAiChat 的核心约束。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "Skill", table = "ai_skill", icon = "fa-book",
        look = ModelPermission.ADMIN, edit = ModelPermission.ADMIN,
        delete = ModelPermission.SUPER_ADMIN, text = "${name}")
public class AiSkill {

    /** 主键。 */
    @ModelField(column = "skill_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** Skill 名称，必须与目录名一致。 */
    @ModelField(column = "skill_name", text = "名称", order = 2)
    @ValidatorNotNull(message = "名称不能为空")
    public String name;

    /** Skill 目录。 */
    @ModelField(column = "directory", text = "目录", order = 3)
    @ValidatorNotNull(message = "目录不能为空")
    public String directory;

    /** 是否加载。 */
    @ModelField(column = "enabled", type = FieldType.BOOLEAN, text = "加载", order = 4)
    public Boolean enabled = Boolean.TRUE;

    /** 是否自动激活。 */
    @ModelField(column = "auto_activate", type = FieldType.BOOLEAN, text = "自动激活", order = 5)
    public Boolean autoActivate = Boolean.FALSE;

    /** 备注。 */
    @ModelField(column = "remark", text = "备注", order = 6)
    public String remark;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 7, look = false)
    public LocalDateTime updatedAt;
}
