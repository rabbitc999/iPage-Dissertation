package com.rabbitc.ims.web;

import com.rabbitc.ims.ai.AiConfiguration;
import com.rabbitc.ims.ai.AiEngineManager;
import com.rabbitc.ims.enums.ToolAccess;
import com.rabbitc.ims.security.RequirePermission;
import com.rabbitc.ims.security.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "ims.ai", name = "available", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/ai")
public class AiController {

    private final AiEngineManager manager;
    private final AiConfiguration configuration;

    /**
     * 构造控制器。
     *
     * @param manager 会话管理器
     */
    public AiController(AiEngineManager manager, AiConfiguration configuration) {
        this.configuration = configuration;
        this.manager = manager;
    }

    /**
     * 对话请求体。
     *
     * @param message    用户输入
     * @param allowTools 是否允许模型调用工具
     */
    public record ChatRequest(String message, Boolean allowTools, String model, String runId,
                              java.util.List<String> attachments, Boolean allowFileWrites, ToolAccess toolAccess, Boolean deepThinking) {
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
        return manager.chat(UserContext.require(), request.message(), allowTools, request.model(),
                request.runId(), request.attachments(), Boolean.TRUE.equals(request.allowFileWrites()), request.toolAccess(), Boolean.TRUE.equals(request.deepThinking()), event -> {});
    }

    /**
     * 同一请求持续输出换行分隔的 JSON 事件；保持当前请求线程及用户权限上下文。
     * 流开始后的错误通过 error 事件报告，断连不重发已执行操作。
     */
    @PostMapping(value = "/chat/stream", produces = "application/x-ndjson")
    @RequirePermission("ai:use")
    public void stream(@RequestBody ChatRequest request,
                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        var user = UserContext.require();
        response.setContentType("application/x-ndjson");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        var writer = response.getWriter();
        java.util.function.Consumer<com.google.gson.JsonObject> emit = event -> {
            writer.println(event.toString());
            writer.flush();
            if (writer.checkError()) {
                throw new java.io.UncheckedIOException(new java.io.IOException("Chat client disconnected"));
            }
        };
        try {
            manager.chat(user, request.message(), request.allowTools() == null || request.allowTools(),
                    request.model(), request.runId(), request.attachments(), Boolean.TRUE.equals(request.allowFileWrites()), request.toolAccess(), Boolean.TRUE.equals(request.deepThinking()), emit);
        } catch (Exception error) {
            if (!writer.checkError()) {
                if (error instanceof AiEngineManager.ModelFailure failure) {
                    emit.accept(io.embedai.EmbedAiChat.object("type", "error", "source", "model",
                            "status", failure.providerStatus(), "message", failure.getMessage(),
                            "detail", failure.detail()));
                } else if (error instanceof ApiException failure) {
                    emit.accept(io.embedai.EmbedAiChat.object("type", "error", "source", "app",
                            "status", failure.status().value(), "message", failure.getMessage()));
                } else {
                    emit.accept(io.embedai.EmbedAiChat.object("type", "error", "message", "对话处理失败，请查看服务端日志"));
                }
            }
        }
    }

    /** 请求停止当前用户的一轮对话。 */
    @PostMapping("/cancel")
    @RequirePermission("ai:use")
    public Map<String, Object> cancel(@RequestBody Map<String, String> request) {
        return Map.of("ok", manager.cancel(UserContext.require(), request.get("runId")));
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
        configuration.requireEnabled();
        manager.invalidateAll();
        return Map.of("ok", true);
    }
}
