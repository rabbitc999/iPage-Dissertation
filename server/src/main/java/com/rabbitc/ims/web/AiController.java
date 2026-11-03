package com.rabbitc.ims.web;

import com.rabbitc.ims.ai.AiEngineManager;
import com.rabbitc.ims.security.RequirePermission;
import com.rabbitc.ims.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 助手接口。
 * <p>
 * 状态对所有登录用户开放（只是「开没开、什么模型」），对话要求 {@code ai:use}，
 * 重载会话要求 {@code ai:manage}。工具的实际执行仍受各自的模型权限约束。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiEngineManager manager;

    /**
     * 构造控制器。
     *
     * @param manager 会话管理器
     */
    public AiController(AiEngineManager manager) {
        this.manager = manager;
    }

    /**
     * 对话请求体。
     *
     * @param message    用户输入
     * @param allowTools 是否允许模型调用工具
     */
    public record ChatRequest(String message, Boolean allowTools) {
    }

    /**
     * 当前状态与可用工具。
     *
     * @return 状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return manager.status(UserContext.require());
    }

    /**
     * 对话。
     *
     * @param request 请求体
     * @return 回答与工具审计
     */
    @PostMapping("/chat")
    @RequirePermission("ai:use")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        boolean allowTools = request.allowTools() == null || request.allowTools();
        return manager.chat(UserContext.require(), request.message(), allowTools);
    }

    /**
     * 导出历史归档。
     *
     * @return 归档
     */
    @GetMapping("/history")
    @RequirePermission("ai:use")
    public Map<String, Object> history() {
        return manager.history(UserContext.require());
    }

    /**
     * 清空当前对话。
     *
     * @return 空响应
     */
    @PostMapping("/reset")
    @RequirePermission("ai:use")
    public Map<String, Object> reset() {
        manager.reset(UserContext.require());
        return Map.of("ok", true);
    }

    /**
     * 重载所有 AI 会话（改完 MCP 命令但不想等自动生效时用）。
     *
     * @return 空响应
     */
    @PostMapping("/reload")
    @RequirePermission("ai:manage")
    public Map<String, Object> reload() {
        manager.invalidateAll();
        return Map.of("ok", true);
    }
}
