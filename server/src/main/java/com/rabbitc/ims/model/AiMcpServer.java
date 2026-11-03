package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * MCP 服务端注册表。
 * <p>
 * {@code command} 是 argv 数组的 JSON 文本，例如 {@code ["python","mcp/demo_server.py"]}；
 * 不走 shell，避免把配置变成注入点。保存后 AI 会话会在下一次对话时重建。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "MCP 服务", table = "ai_mcp_server", icon = "fa-plug",
        look = ModelPermission.ADMIN, edit = ModelPermission.ADMIN,
        delete = ModelPermission.SUPER_ADMIN, text = "${name}")
public class AiMcpServer {

    /** 主键。 */
    @ModelField(column = "mcp_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 显示名称。 */
    @ModelField(column = "mcp_name", text = "名称", order = 2)
    @ValidatorNotNull(message = "名称不能为空")
    @ValidatorLength(max = 64, message = "名称最长 64 个字符")
    public String name;

    /** 工具名前缀，只允许字母、数字、下划线。 */
    @ModelField(column = "prefix_name", text = "工具前缀", order = 3)
    @ValidatorNotNull(message = "前缀不能为空")
    public String prefix;

    /** 启动命令（JSON 数组文本）。 */
    @ModelField(column = "command_line", text = "启动命令", order = 4)
    @ValidatorNotNull(message = "启动命令不能为空")
    @ValidatorLength(max = 200, message = "启动命令最长 200 个字符")
    public String command;

    /** 超时毫秒数。 */
    @ModelField(column = "timeout_ms", type = FieldType.NUMBER, text = "超时(ms)", order = 5)
    public Integer timeoutMs = 15000;

    /** 是否随会话启动。 */
    @ModelField(column = "enabled", type = FieldType.BOOLEAN, text = "启用", order = 6)
    public Boolean enabled = Boolean.FALSE;

    /** 备注。 */
    @ModelField(column = "remark", text = "备注", order = 7)
    public String remark;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 8, look = false)
    public LocalDateTime updatedAt;
}
