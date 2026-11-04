package com.rabbitc.ims.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.event.AiConfigChangedEvent;
import com.rabbitc.ims.enums.ToolAccess;
import com.rabbitc.ims.meta.MetaService;
import com.rabbitc.ims.meta.ModelRegistry;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ims.model.AiMcpServer;
import com.rabbitc.ims.model.AiModel;
import com.rabbitc.ims.model.AiSkill;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.service.CrudService;
import com.rabbitc.ims.service.RecordAttachmentService;
import com.rabbitc.ims.web.ApiException;
import com.rabbitc.ims.web.FileController;
import com.rabbitc.ims.web.ProfileController;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.model.SysFile;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import io.embedai.EmbedAiChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 会话管理：按账号装配引擎，按对话保存可恢复的上下文与可见消息。
 * <p>
 * 三个设计要点：
 * <ul>
 *     <li><b>按用户隔离</b>：引擎里保存着对话历史与服务端密钥引用，不能跨用户共享；</li>
 *     <li><b>工具即 CRUD</b>：模型能调用的每个工具最终都走 {@link CrudService}，
 *     因此权限、数据范围、审计与页面操作完全一致——AI 不能绕过权限；</li>
 *     <li><b>配置热生效</b>：MCP / Skill 表一改就发事件，旧会话作废，下一句话重建。</li>
 * </ul>
 * EmbedAiChat 的引擎要求串行调用，所以每个会话内部的对话与配置修改都加锁。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Service
@ConditionalOnProperty(prefix = "ims.ai", name = "available", havingValue = "true", matchIfMissing = true)
public class AiEngineManager {

    private static final Logger LOG = LoggerFactory.getLogger(AiEngineManager.class);
    private static final Gson GSON = new Gson();

    private final AiProperties props;
    private final AiConfiguration configuration;
    private final CrudService crud;
    private final MetaService metaService;
    private final ModelSession sql;
    private final ModelRegistry registry;
    private final DataSource dataSource;
    private final AiConversations conversations;

    private final FileController files;
    private final RecordAttachmentService recordAttachments;
    private final ProfileController profiles;
    private record Run(String id, AtomicBoolean cancelled) {}

    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private static final Set<String> IMAGES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    /**
     * 构造管理器。
     *
     * @param props       AI 配置
     * @param crud        CRUD 服务
     * @param metaService 元数据服务
     * @param sql         SQL 会话
     */
    public AiEngineManager(AiProperties props, CrudService crud, MetaService metaService, ModelSession sql,
                           FileController files, ProfileController profiles, AiConfiguration configuration,
                           RecordAttachmentService recordAttachments, ModelRegistry registry, DataSource dataSource) {
        this.dataSource = dataSource;
        this.registry = registry;
        this.props = props;
        this.conversations = new AiConversations(workDir().resolve("conversations"));
        this.recordAttachments = recordAttachments;
        this.configuration = configuration;
        this.crud = crud;
        this.metaService = metaService;
        this.sql = sql;
        this.files = files;
        this.profiles = profiles;
    }

    /** Refreshes the generated schema section while keeping locally edited Skill instructions. */
    private Path prepareWorkDir() {
        Path target = workDir().resolve("skills/ims-assistant/SKILL.md");
        ClassPathResource resource = new ClassPathResource("ai/skills/ims-assistant/SKILL.md");
        try {
            String original;
            if (Files.exists(target)) {
                original = Files.readString(target);
            } else {
                try (var in = resource.getInputStream()) {
                    original = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            String updated = AiSkillSchema.append(original, registry, sql, dataSource);
            if (!updated.equals(original) || !Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.writeString(target, updated);
            }
        } catch (Exception e) {
            LOG.warn("[ims] 生成内置 Skill 失败：{}", e.getMessage());
        }
        return target.getParent();
    }

    /** 返回当前用户可用的模型及当前会话状态。 */
    public Map<String, Object> status(LoginUser user) {
        long version = generation.get();
        boolean enabled = configuration.enabled();
        var available = enabled ? availableModels(user) : Map.<String, AiModel>of();
        AiModel first = available.values().stream().findFirst().orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", enabled);
        body.put("models", available.values().stream()
                .map(model -> Map.of("id", model.id, "name", model.name, "vision", model.vision,
                        "provider", model.provider, "allowTools", model.allowTools,
                        "maxToolAccess", AiConfiguration.maxToolAccess(model))).toList());
        body.put("ready", first != null);
        body.put("provider", first == null ? "" : first.provider);
        body.put("model", first == null ? "" : first.modelId);
        body.put("hasKey", first != null);
        body.put("allowTools", first != null && first.allowTools);
        if (first == null) {
            body.put("reason", enabled ? "当前账号没有可用模型，请联系管理员检查模型配置" : "智能助手已关闭");
            body.put("tools", List.of());
            body.put("mcp", List.of());
            body.put("skills", List.of());
            return body;
        }
        Session session = statusSession(user, first, available, version);
        body.put("tools", session == null ? List.of() : toolNames(session.engine));
        body.put("mcp", session == null ? List.of() : session.mcpStatus);
        body.put("skills", session == null ? List.of() : session.skills);
        body.put("skillErrors", session == null ? List.of() : session.skillErrors);
        return body;
    }

    /**
     * 对话。
     *
     * @param user       当前用户
     * @param message    用户输入
     * @param allowTools 本次是否允许调用工具
     * @return 回答与工具审计
     */
    public Map<String, Object> chat(LoginUser user, String message, boolean allowTools) {
        return chat(user, message, allowTools, event -> {});
    }

    /** 区分模型供应商错误与 IMS 本身的权限或资源错误。 */
    public static final class ModelFailure extends ApiException {
        private final int providerStatus;
        private final String detail;

        private ModelFailure(Exception error, String key) {
            super(HttpStatus.BAD_GATEWAY, "模型调用失败");
            Throwable cause = error instanceof java.util.concurrent.ExecutionException && error.getCause() != null
                    ? error.getCause() : error;
            providerStatus = cause instanceof EmbedAiChat.ProviderHttpException http ? http.status
                    : cause instanceof java.net.http.HttpTimeoutException
                        || cause instanceof java.util.concurrent.TimeoutException ? 408
                    : cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException ? 0 : -1;
            String original = cause.toString();
            detail = key == null || key.isBlank() ? original : original.replace(key, "[已隐藏]");
        }

        public int providerStatus() {
            return providerStatus;
        }

        public String detail() {
            return detail;
        }
    }

    private Map<String, AiModel> availableModels(LoginUser user) {
        var available = new LinkedHashMap<String, AiModel>();
        if (!user.has("ai:use")) return available;
        for (AiModel model : configuration.models()) {
            if (configuration.canUse(model, user)) {
                available.put(model.id, model);
            }
        }
        return available;
    }

    /** 同一用户只能运行一轮，取消标记不影响其他用户。 */
    public boolean cancel(LoginUser user, String runId) {
        var run = runs.get(user.id());
        if (run == null || runId == null || !runId.equals(run.id())) return false;
        run.cancelled().set(true);
        return true;
    }

    /** 按顺序转发本轮消息事件，工具仍以当前用户身份同步执行。 */
    public Map<String, Object> chat(LoginUser user, String message, boolean allowTools,
                                    java.util.function.Consumer<JsonObject> events) {
        return chat(user, message, allowTools, null, null, List.of(), false, ToolAccess.READ_ONLY, false, null, events);
    }

    public Map<String, Object> chat(LoginUser user, String message, boolean allowTools,
                                    String model, String runId, List<String> ids, boolean allowFileWrites, ToolAccess requestedAccess, boolean deepThinking, String conversationId,
                                    java.util.function.Consumer<JsonObject> events) {
        long version = generation.get();
        configuration.requireEnabled();
        var available = availableModels(user);
        AiModel selected = model == null || model.isBlank()
                ? available.values().stream().findFirst().orElse(null) : available.get(model);
        // 旧 API 客户端仍可传供应商模型 ID，只有唯一匹配时才接受。
        if (selected == null && model != null && !model.isBlank()) {
            var matches = available.values().stream().filter(item -> item.modelId.equals(model)).toList();
            if (matches.size() == 1) selected = matches.get(0);
        }
        if (selected == null) throw ApiException.forbidden("没有使用该模型的权限");
        boolean toolsEnabled = allowTools && Boolean.TRUE.equals(selected.allowTools);
        ToolAccess access = toolsEnabled ? configuration.requireToolAccess(selected, requestedAccess) : ToolAccess.READ_ONLY;
        ids = ids == null ? List.of() : ids;
        if (ids.size() > 4 || ids.stream().anyMatch(id -> id == null || !id.matches("[a-f0-9-]{36}"))) {
            throw ApiException.badRequest("最多发送 4 个有效附件");
        }
        if ((message == null || message.isBlank()) && ids.isEmpty()) {
            throw ApiException.badRequest("请输入内容或添加附件");
        }
        var run = new Run(runId, new AtomicBoolean());
        synchronized (runs) {
            if (runs.putIfAbsent(user.id(), run) != null) throw ApiException.badRequest("已有对话正在运行");
        }
        try {
            events.accept(EmbedAiChat.object("type", "started"));
            Map<String, Object> result = runChat(user, message, toolsEnabled, selected, version, ids, allowFileWrites,
                    access, deepThinking, conversationId, event -> {
                        if (!"done".equals(event.get("type").getAsString())) events.accept(event);
                    }, run.cancelled());
            events.accept(EmbedAiChat.object("type", "done"));
            return result;
        } finally {
            runs.remove(user.id(), run);
        }
    }

    private Map<String, Object> runChat(LoginUser user, String message, boolean allowTools, AiModel model,
                                        long version, List<String> ids, boolean allowFileWrites, ToolAccess access,
                                        boolean deepThinking, String conversationId,
                                        java.util.function.Consumer<JsonObject> events, AtomicBoolean cancelled) {
        boolean externalMcp = allowTools && access == ToolAccess.FULL && user.isAdmin();
        Session session = sessionFor(user, model, version, externalMcp);
        synchronized (session) {
            var attachments = new LinkedHashMap<String, SysFile>();
            for (String id : ids) attachments.put(id, files.chatAttachment(user, id));
            StringBuilder prompt = new StringBuilder(message == null ? "" : message);
            var images = new com.google.gson.JsonArray();
            int imageBytes = 0;
            boolean anthropic = "anthropic".equals(model.provider);
            for (SysFile file : attachments.values()) {
                prompt.append("\n附件（数据）：").append(file.name).append(" [").append(file.id).append("]");
                if (IMAGES.contains(file.contentType)) {
                    if (!model.vision) {
                        prompt.append("（当前模型不支持视觉）");
                        continue;
                    }
                    imageBytes += file.data.length;
                    if (imageBytes > 5 * 1024 * 1024) throw ApiException.badRequest("视觉附件合计不能超过 5MB");
                    String data = Base64.getEncoder().encodeToString(file.data);
                    images.add(anthropic
                            ? EmbedAiChat.object("type", "image", "source", EmbedAiChat.object(
                                    "type", "base64", "media_type", file.contentType, "data", data))
                            : EmbedAiChat.object("type", "image_url", "image_url", EmbedAiChat.object(
                                    "url", "data:" + file.contentType + ";base64," + data)));
                } else if (file.contentType.startsWith("text/") || file.name.matches("(?i).*\\.(txt|md|csv|json|log)$")) {
                    prompt.append("\n").append(new String(file.data, 0, Math.min(file.data.length, 8192), StandardCharsets.UTF_8));
                    if (file.data.length > 8192) prompt.append("\n（内容已截断）");
                } else {
                    prompt.append("（仅提供文件信息）");
                }
            }
            var content = new com.google.gson.JsonArray();
            content.add(EmbedAiChat.object("type", "text", "text", prompt.toString()));
            content.addAll(images);
            if (prompt.toString().getBytes(StandardCharsets.UTF_8).length > 65536) {
                throw ApiException.badRequest("消息和文本附件合计不能超过 64KB");
            }
            String key = configuration.credential(model);
            JsonObject previous = null;
            JsonArray transcript = null;
            if (conversationId != null) {
                JsonObject saved = conversations.archive(user.id(), conversationId);
                transcript = conversations.messages(user.id(), conversationId);
                previous = session.engine.exportHistory();
                if (saved == null) session.engine.clear();
                else session.engine.importTrustedHistory(saved);
                var attached = new JsonArray();
                attachments.values().forEach(file -> attached.add(EmbedAiChat.object(
                        "id", file.id, "name", file.name, "type", file.contentType)));
                transcript.add(EmbedAiChat.object("role", "user", "text", message == null ? "" : message,
                        "attachments", attached));
                if (deepThinking) transcript.add(EmbedAiChat.object("role", "assistant", "thinkingRequested", true));
            }
            JsonArray visibleMessages = transcript;
            session.access = access;
            session.fileWrites = allowFileWrites;
            session.attachments = attachments;
            session.engine.allowTools = allowTools;
            session.engine.config.key = key;
            int auditStart = session.engine.exportHistory().getAsJsonArray("audit").size();
            String reply;
            JsonObject archive;
            try {
                reply = session.engine.send(prompt.toString(), event -> {
                    if (visibleMessages != null) recordVisibleEvent(visibleMessages, event);
                    events.accept(event);
                }, images.isEmpty() ? null : content, deepThinking, () -> {
                    if (cancelled.get() || version != generation.get() || !configuration.current(model)) {
                        throw new CancellationException();
                    }
                });
            } catch (CancellationException e) {
                if (visibleMessages != null) {
                    visibleMessages.add(EmbedAiChat.object("role", "assistant", "text", "本轮已停止。"));
                }
                events.accept(EmbedAiChat.object("type", "cancelled"));
                return Map.of("cancelled", true);
            } catch (Exception e) {
                var failure = new ModelFailure(e, key);
                LOG.warn("[ims] AI 调用失败：{}", failure.detail());
                throw failure;
            } finally {
                session.engine.config.key = "";
                session.fileWrites = false;
                session.access = ToolAccess.READ_ONLY;
                session.attachments = Map.of();
                archive = session.engine.exportHistory();
                if (previous != null) {
                    try {
                        if (archive.getAsJsonArray("messages").size() > 0) {
                            conversations.save(user.id(), conversationId, archive, visibleMessages, message);
                        }
                    } finally {
                        session.engine.importTrustedHistory(previous);
                    }
                }
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reply", reply);
            com.google.gson.JsonArray currentAudit = new com.google.gson.JsonArray();
            for (int i = auditStart; i < archive.getAsJsonArray("audit").size(); i++) {
                currentAudit.add(archive.getAsJsonArray("audit").get(i));
            }
            body.put("audit", toJava(currentAudit));
            body.put("tools", toolNames(session.engine));
            return body;
        }
    }

    /** Captures only user-visible events for the conversation sidebar. */
    private void recordVisibleEvent(JsonArray transcript, JsonObject event) {
        String type = event.get("type").getAsString();
        if ("assistant".equals(type)) {
            transcript.add(EmbedAiChat.object("role", "assistant", "text", event.get("text")));
        } else if ("tool".equals(type)) {
            JsonObject audit = event.getAsJsonObject("audit");
            String name = audit.get("name").getAsString();
            if (!Set.of("ims_attach_record_file", "ims_upload_text_file").contains(name)) return;
            JsonObject outcome = audit.getAsJsonObject("result");
            JsonElement result = outcome.get("result");
            if (!outcome.get("ok").getAsBoolean() || result == null || !result.isJsonObject()) return;
            JsonObject file = result.getAsJsonObject().deepCopy();
            if (!file.has("id")) return;
            if ("ims_attach_record_file".equals(name)) {
                file.addProperty("downloadPath", "/models/" + file.get("model").getAsString()
                        + "/" + file.get("recordId").getAsString() + "/attachments/"
                        + file.get("id").getAsString() + "/download");
            }
            transcript.add(EmbedAiChat.object("role", "assistant", "attachments", EmbedAiChat.array(file)));
        } else if ("thinking".equals(type)) {
            JsonObject message = EmbedAiChat.object("role", "assistant", "thinking", event.get("text"));
            for (int i = transcript.size() - 1; i >= 0; i--) {
                JsonObject item = transcript.get(i).getAsJsonObject();
                if ("user".equals(item.get("role").getAsString())) break;
                if (item.has("thinkingRequested")) {
                    transcript.set(i, message);
                    return;
                }
            }
            transcript.add(message);
        }
    }

    public List<AiConversations.Entry> conversations(LoginUser user) {
        return conversations.list(user.id());
    }

    public AiConversations.Entry createConversation(LoginUser user) {
        return conversations.create(user.id());
    }

    public List<?> conversation(LoginUser user, String id) {
        return GSON.fromJson(conversations.messages(user.id(), id), List.class);
    }

    public AiConversations.Entry renameConversation(LoginUser user, String id, String title) {
        return conversations.rename(user.id(), id, title);
    }

    public void deleteConversation(LoginUser user, String id) {
        synchronized (runs) {
            if (runs.containsKey(user.id())) throw ApiException.badRequest("请等待本轮对话结束");
            conversations.delete(user.id(), id);
        }
    }

    /**
     * 导出当前用户的历史归档。
     *
     * @param user 当前用户
     * @return 归档
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> history(LoginUser user) {
        configuration.requireEnabled();
        Session existing = sessions.get(user.id());
        if (existing == null) {
            return Map.of("version", 1, "messages", List.of());
        }
        synchronized (existing) {
            if (!existing.matches(availableModels(user).get(existing.modelId), user)) {
                throw ApiException.forbidden("没有使用该模型的权限");
            }
            return GSON.fromJson(existing.engine.exportHistory(), Map.class);
        }
    }

    /**
     * 清空当前用户的对话（不重建工具）。
     *
     * @param user 当前用户
     */
    public void reset(LoginUser user) {
        configuration.requireEnabled();
        Session session = sessions.get(user.id());
        if (session != null) {
            synchronized (session) {
                session.engine.clear();
            }
        }
    }

    /**
     * 作废所有会话：MCP 配置或 Skill 内容变化后调用。
     */
    public synchronized void invalidateAll() {
        generation.incrementAndGet();
        runs.values().forEach(run -> run.cancelled().set(true));
        List<Session> stale = new ArrayList<>(sessions.values());
        sessions.clear();
        stale.forEach(Session::close);
        LOG.info("[ims] AI 会话已作废，共关闭 {} 个", stale.size());
    }

    /**
     * 配置变化事件。
     *
     * @param event 事件
     */
    @EventListener
    public void onConfigChanged(AiConfigChangedEvent event) {
        LOG.info("[ims] AI 配置变化：{}", event.reason());
        invalidateAll();
    }

    // ------------------------------------------------------------------ 会话装配

    private synchronized Session statusSession(LoginUser user, AiModel first, Map<String, AiModel> available,
                                               long version) {
        if (version != generation.get()) return null;
        Session current = sessions.get(user.id());
        if (current != null && current.matches(available.get(current.modelId), user)) return current;
        return runs.containsKey(user.id()) ? null : sessionFor(user, first, version, false);
    }

    private synchronized Session sessionFor(LoginUser user, AiModel model, long version, boolean externalMcp) {
        configuration.requireEnabled();
        if (version != generation.get()) throw ApiException.badRequest("AI 配置已变更，请重试");
        Session current = sessions.get(user.id());
        if (current != null && current.matches(model, user) && current.externalMcp == externalMcp) return current;
        Session rebuilt = build(user, model, externalMcp);
        if (current != null && current.matches(model, user)) {
            synchronized (current) {
                rebuilt.engine.importHistory(current.engine.exportHistory());
            }
        }
        sessions.put(user.id(), rebuilt);
        if (current != null) current.close();
        return rebuilt;
    }

    private Session build(LoginUser user, AiModel model, boolean externalMcp) {
        EmbedAiChat.Engine engine = new EmbedAiChat.Engine();
        engine.config.provider = model.provider;
        engine.config.baseUrl = model.baseUrl;
        engine.config.model = model.modelId;
        engine.config.maxTokens = props.getMaxTokens();
        engine.config.contextBudget = props.getContextBudget();

        Session session = new Session(engine, model, user.isAdmin(), externalMcp);
        engine.toolDenial = name -> toolDenial(session.access, requiredAccess(name));
        if (model.allowTools) {
            registerTools(engine, session);
            // 外部工具无法可靠区分读写，只在管理员明确选择最高权限时挂载。
            if (externalMcp) attachMcp(session);
        }
        loadSkills(session, prepareWorkDir());
        return session;
    }

    private void loadSkills(Session session, Path defaultDirectory) {
        List<AiSkill> skills = sql.selectList(Query.of(AiSkill.class));
        boolean defaultConfigured = false;
        boolean active = false;
        for (AiSkill skill : skills) {
            try {
                Path directory = Path.of(skill.directory).toAbsolutePath().normalize();
                if (directory.equals(defaultDirectory)) defaultConfigured = true;
                if (!Boolean.TRUE.equals(skill.enabled)) continue;
                String name = session.engine.loadSkill(directory);
                session.skills.add(name);
                if (Boolean.TRUE.equals(skill.autoActivate)) {
                    session.engine.activateSkill(name);
                    active = true;
                }
            } catch (Exception e) {
                session.skillErrors.add(skill.name + "：" + e.getMessage());
                LOG.warn("[ims] Skill 加载失败（{}）：{}", skill.directory, e.getMessage());
            }
        }
        if (!defaultConfigured && !session.skills.contains("ims-assistant")) {
            try {
                String name = session.engine.loadSkill(defaultDirectory);
                session.skills.add(name);
                if (!active) session.engine.activateSkill(name);
            } catch (Exception e) {
                session.skillErrors.add("ims-assistant：" + e.getMessage());
                LOG.warn("[ims] 内置 Skill 加载失败：{}", e.getMessage());
            }
        }
    }

    private void attachMcp(Session session) {
        List<AiMcpServer> servers = sql.selectList(Query.of(AiMcpServer.class).where("enabled", true));
        for (AiMcpServer server : servers) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("name", server.name);
            status.put("prefix", server.prefix);
            EmbedAiChat.McpClient client = null;
            try {
                List<String> argv = parseCommand(server.command);
                int timeout = server.timeoutMs == null ? 15000 : server.timeoutMs;
                client = new EmbedAiChat.McpClient(argv, timeout);
                session.engine.attachMcp(server.prefix, client);
                session.mcpClients.add(client);
                status.put("ok", true);
                status.put("tools", client.tools().size());
            } catch (Exception e) {
                status.put("ok", false);
                status.put("error", e.getMessage());
                closeQuietly(client);
                LOG.warn("[ims] MCP 服务 {} 启动失败：{}", server.name, e.getMessage());
            }
            session.mcpStatus.add(status);
        }
    }

    private static List<String> parseCommand(String command) {
        JsonElement parsed = JsonParser.parseString(command == null ? "[]" : command);
        if (!parsed.isJsonArray() || parsed.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("启动命令必须是 JSON 数组");
        }
        List<String> argv = new ArrayList<>();
        parsed.getAsJsonArray().forEach(item -> argv.add(item.getAsString()));
        return argv;
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 注册宿主工具。工具返回的都是「当前用户视角」的数据，权限由 CrudService 兜底。
     *
     * @param engine 引擎
     * @param session 当前会话
     */
    private void registerTools(EmbedAiChat.Engine engine, Session session) {
        registerHostTool(engine, new EmbedAiChat.Tool("ims_attach_record_file",
                "Attach a file uploaded in this turn to an existing ticket or asset and optionally update its fields",
                schema("""
                {"type":"object","properties":{"model":{"type":"string","enum":["ticket","asset"]},
                 "recordId":{"type":"string"},"fileId":{"type":"string"},"values":{"type":"object"}},
                 "required":["model","recordId","fileId"],"additionalProperties":false}
                """), args -> {
            requireFileWrites(session);
            String fileId = required(args, "fileId");
            if (!session.attachments.containsKey(fileId)) throw ApiException.badRequest("请选择本轮上传的附件");
            var values = args.has("values") ? valuesOf(args) : Map.<String, Object>of();
            if (!values.isEmpty()) requireAccess(session, ToolAccess.ADD_EDIT);
            return json(recordAttachments.attach(UserContext.require(), required(args, "model"),
                    required(args, "recordId"), fileId, values));
        }));
        registerHostTool(engine, new EmbedAiChat.Tool("ims_upload_text_file",
                "Save a user-requested UTF-8 text file as a private attachment", schema("""
                {"type":"object","properties":{"name":{"type":"string"},"text":{"type":"string"}},
                 "required":["name","text"],"additionalProperties":false}
                """), args -> {
            requireFileWrites(session);
            byte[] bytes = required(args, "text").getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 0 || bytes.length > 65536) throw ApiException.badRequest("文本文件须为 1–65536 字节");
            return json(files.store(UserContext.require(), required(args, "name"), "text/plain", bytes, true));
        }));
        registerHostTool(engine, new EmbedAiChat.Tool("ims_update_my_avatar",
                "Publish an attached image as the current user's avatar after explicit user request", schema("""
                {"type":"object","properties":{"id":{"type":"string"}},"required":["id"],
                 "additionalProperties":false}
                """), args -> {
            requireFileWrites(session);
            SysFile file = session.attachments.get(required(args, "id"));
            if (file == null || !IMAGES.contains(file.contentType) || file.data.length > 5 * 1024 * 1024) {
                throw ApiException.badRequest("请选择本轮 5MB 以内的 PNG、JPEG、WebP 或 GIF 图片");
            }
            return json(profiles.saveAvatar(UserContext.require(), file.name, file.contentType, file.data));
        }));
        registerHostTool(engine, new EmbedAiChat.Tool(
                "ims_list_models",
                "List data models the current user can access",
                schema("""
                        {"type":"object","properties":{},"additionalProperties":false}
                        """),
                args -> json(metaService.nav(UserContext.require()))));

        registerHostTool(engine, new EmbedAiChat.Tool(
                "ims_model_schema",
                "Get field definitions of a model (types, required, enum options)",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"}},
                         "required":["model"],
                         "additionalProperties":false}
                        """),
                args -> json(metaService.schema(UserContext.require(), required(args, "model")))));

        registerHostTool(engine, new EmbedAiChat.Tool(
                "ims_query",
                "Query rows of a model with pagination and optional keyword",
                schema("""
                        {"type":"object",
                         "properties":{
                           "model":{"type":"string"},
                           "keyword":{"type":"string"},
                           "page":{"type":"integer","minimum":1},
                           "size":{"type":"integer","minimum":1,"maximum":50}
                         },
                         "required":["model"],
                         "additionalProperties":false}
                        """),
                args -> {
                    Map<String, String> params = new LinkedHashMap<>();
                    if (args.has("keyword")) {
                        params.put("kw", args.get("keyword").getAsString());
                    }
                    if (args.has("page")) {
                        params.put("page", args.get("page").getAsString());
                    }
                    if (args.has("size")) {
                        params.put("size", args.get("size").getAsString());
                    }
                    return json(crud.page(UserContext.require(), required(args, "model"), params));
                }));

        registerHostTool(engine, new EmbedAiChat.Tool(
                "ims_get",
                "Get one row by primary key",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"},"id":{"type":"string","description":"UUID"}},
                         "required":["model","id"],
                         "additionalProperties":false}
                        """),
                args -> json(crud.get(UserContext.require(), required(args, "model"), required(args, "id")))));

        registerHostTool(engine, new EmbedAiChat.Tool(
                "ims_create",
                "Create a row. Field names come from ims_model_schema",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"},"values":{"type":"object"}},
                         "required":["model","values"],
                         "additionalProperties":false}
                        """),
                args -> json(crud.create(UserContext.require(), required(args, "model"), valuesOf(args)))));

        registerHostTool(engine, new EmbedAiChat.Tool(
                "ims_update",
                "Update a row by primary key; only provided fields change",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"},"id":{"type":"string","description":"UUID"},"values":{"type":"object"}},
                         "required":["model","id","values"],
                         "additionalProperties":false}
                        """),
                args -> json(crud.update(UserContext.require(), required(args, "model"),
                        required(args, "id"), valuesOf(args)))));

        registerHostTool(engine, new EmbedAiChat.Tool(
                "ims_delete",
                "Delete a row by primary key. Requires delete permission",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"},"id":{"type":"string","description":"UUID"}},
                         "required":["model","id"],
                         "additionalProperties":false}
                        """),
                args -> {
                    crud.delete(UserContext.require(), required(args, "model"), required(args, "id"));
                    return json(Map.of("deleted", true));
                }));

        registerHostTool(engine, new EmbedAiChat.Tool(
                "ims_stats",
                "Count rows per model visible to the current user",
                schema("""
                        {"type":"object","properties":{},"additionalProperties":false}
                        """),
                args -> {
                    Map<String, Object> counts = new LinkedHashMap<>();
                    for (Map<String, Object> item : metaService.nav(UserContext.require())) {
                        String key = String.valueOf(item.get("key"));
                        counts.put(key, crud.count(UserContext.require(), key));
                    }
                    return json(counts);
                }));
    }

    private static void registerHostTool(EmbedAiChat.Engine engine, EmbedAiChat.Tool tool) {
        engine.registerTool(new EmbedAiChat.Tool(tool.name(), tool.description(), tool.schema(), args -> {
            try {
                return tool.handler().call(args);
            } catch (ApiException denied) {
                if (denied.status() == HttpStatus.FORBIDDEN) {
                    throw new EmbedAiChat.ToolPermissionDenied("操作已拦截。" + denied.getMessage());
                }
                throw denied;
            }
        }));
    }

    private static ToolAccess requiredAccess(String name) {
        return switch (name) {
            case "assistant_reply", "assistant_end", "skill_list", "skill_activate",
                 "ims_list_models", "ims_model_schema", "ims_query", "ims_get", "ims_stats" -> ToolAccess.READ_ONLY;
            case "ims_create", "ims_upload_text_file", "ims_attach_record_file" -> ToolAccess.ADD;
            case "ims_update", "ims_update_my_avatar" -> ToolAccess.ADD_EDIT;
            default -> ToolAccess.FULL;
        };
    }

    private static String toolDenial(ToolAccess current, ToolAccess required) {
        return current.allows(required) ? null
                : "操作已拦截。当前 AI 权限为「" + current.label() + "」，此操作需要「" + required.label() + "」";
    }

    private static void requireAccess(Session session, ToolAccess required) {
        String denial = toolDenial(session.access, required);
        if (denial != null) throw new EmbedAiChat.ToolPermissionDenied(denial);
    }

    private static void requireFileWrites(Session session) {
        if (!session.fileWrites) throw new EmbedAiChat.ToolPermissionDenied("本轮未授权文件写入，操作已拦截");
    }

    private static List<String> toolNames(EmbedAiChat.Engine engine) {
        List<String> names = new ArrayList<>();
        engine.tools().forEach(item -> names.add(item.getAsJsonObject().get("name").getAsString()));
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> valuesOf(JsonObject args) {
        if (!args.has("values") || !args.get("values").isJsonObject()) {
            throw new IllegalArgumentException("缺少参数 values");
        }
        return GSON.fromJson(args.get("values"), Map.class);
    }

    private static String required(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("缺少参数 " + name);
        }
        return args.get(name).getAsString();
    }

    private static JsonElement json(Object value) {
        return GSON.toJsonTree(value);
    }

    private static JsonObject schema(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static void closeQuietly(EmbedAiChat.McpClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // 关闭失败不覆盖原始错误
            }
        }
    }

    private Path workDir() {
        return Path.of(props.getWorkDir()).toAbsolutePath().normalize();
    }

    /**
     * 单个用户的会话。
     */
    private static final class Session {

        private final EmbedAiChat.Engine engine;
        private final String modelId;
        private final String revision;
        private final boolean admin;
        private final boolean externalMcp;
        private ToolAccess access = ToolAccess.READ_ONLY;
        private boolean fileWrites;
        private Map<String, SysFile> attachments = Map.of();
        private final List<EmbedAiChat.McpClient> mcpClients = new ArrayList<>();
        private final List<Map<String, Object>> mcpStatus = new ArrayList<>();
        private final List<String> skills = new ArrayList<>();
        private final List<String> skillErrors = new ArrayList<>();

        private Session(EmbedAiChat.Engine engine, AiModel model, boolean admin, boolean externalMcp) {
            this.externalMcp = externalMcp;
            this.admin = admin;
            this.engine = engine;
            this.modelId = model.id;
            this.revision = model.revision;
        }

        private boolean matches(AiModel model, LoginUser user) {
            return model != null && modelId.equals(model.id)
                    && java.util.Objects.equals(revision, model.revision) && admin == user.isAdmin();
        }

        private void close() {
            for (EmbedAiChat.McpClient client : mcpClients) {
                closeQuietly(client);
            }
            mcpClients.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static Object toJava(JsonElement element) {
        return element == null ? List.of() : GSON.fromJson(element, Object.class);
    }
}
