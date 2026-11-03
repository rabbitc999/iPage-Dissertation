package com.rabbitc.ims.event;

/**
 * MCP / Skill 配置变化事件。
 * <p>
 * 用事件而不是直接调用，是为了避免「CRUD 服务 → AI 引擎 → CRUD 服务」的循环依赖：
 * 钩子只负责发事件，AI 会话管理器收到后把旧的会话作废，下一次对话重建。
 *
 * @param reason 变化原因，仅用于日志
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public record AiConfigChangedEvent(String reason) {
}
