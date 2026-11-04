package com.rabbitc.ims.ai;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.event.AiConfigChangedEvent;
import com.rabbitc.ims.meta.MetaService;
import com.rabbitc.ims.model.AiMcpServer;
import com.rabbitc.ims.model.AiSkill;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.service.CrudService;
import com.rabbitc.ims.web.ApiException;
import io.embedai.EmbedAiChat;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 会话管理：把「模型 + 工具 + MCP + Skill」装配成每个用户一个的引擎实例。
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
public class AiEngineManager {

    private static final Logger LOG = LoggerFactory.getLogger(AiEngineManager.class);
    private static final Gson GSON = new Gson();

    private final AiProperties props;
    private final CrudService crud;
    private final MetaService metaService;
    private final ModelSession sql;

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
    public AiEngineManager(AiProperties props, CrudService crud, MetaService metaService, ModelSession sql) {
        this.props = props;
        this.crud = crud;
        this.metaService = metaService;
        this.sql = sql;
    }

    /**
     * 启动时把打进 jar 的示例 Skill 释放到运行目录，用户可以直接改。
     */
    @PostConstruct
    public void prepareWorkDir() {
        if (!props.isEnabled()) {
            return;
        }
        Path target = workDir().resolve("skills/ims-assistant/SKILL.md");
        if (Files.exists(target)) {
            return;
        }
        ClassPathResource resource = new ClassPathResource("ai/skills/ims-assistant/SKILL.md");
        if (!resource.exists()) {
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
            LOG.info("[ims] 已释放内置 Skill 到 {}", target);
        } catch (Exception e) {
            LOG.warn("[ims] 释放内置 Skill 失败：{}", e.getMessage());
        }
    }

    /**
     * AI 是否可用：既要开关打开，也要有密钥。
     *
     * @return 可用返回 {@code true}
     */
    public boolean ready() {
        return props.isEnabled() && props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    /**
     * 当前状态。
     *
     * @param user 当前用户
     * @return 状态
     */
    public Map<String, Object> status(LoginUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", props.isEnabled());
        body.put("ready", ready());
        body.put("provider", props.getProvider());
        body.put("model", props.getModel());
        body.put("hasKey", props.getApiKey() != null && !props.getApiKey().isBlank());
        body.put("allowTools", props.isAllowTools());
        if (!props.isEnabled()) {
            body.put("reason", "AI 未启用：设置环境变量 IMS_AI_ENABLED=true 与 IMS_AI_KEY 后重启");
            body.put("tools", List.of());
            body.put("mcp", List.of());
            body.put("skills", List.of());
            return body;
        }
        if (!ready()) {
            body.put("reason", "缺少 API Key：设置环境变量 IMS_AI_KEY 后重启");
            body.put("tools", List.of());
            body.put("mcp", List.of());
            body.put("skills", List.of());
            return body;
        }
        Session session = sessionFor(user);
        synchronized (session) {
            body.put("tools", toolNames(session.engine));
            body.put("mcp", session.mcpStatus);
            body.put("skills", session.skills);
            body.put("skillErrors", session.skillErrors);
        }
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

    /** 按顺序转发本轮消息事件，工具仍以当前用户身份同步执行。 */
    public Map<String, Object> chat(LoginUser user, String message, boolean allowTools,
                                    java.util.function.Consumer<JsonObject> events) {
        if (!ready()) {
            throw ApiException.notImplemented("AI 未启用或缺少 API Key，请查看服务端配置");
        }
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("请输入内容");
        }
        Session session = sessionFor(user);
        synchronized (session) {
            session.engine.allowTools = allowTools && props.isAllowTools();
            int auditStart = session.engine.exportHistory().getAsJsonArray("audit").size();
            String reply;
            try {
                reply = session.engine.send(message, events);
            } catch (Exception e) {
                LOG.warn("[ims] AI 调用失败：{}", e.getMessage());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "模型调用失败：" + e.getMessage());
            }
            JsonObject archive = session.engine.exportHistory();
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

    /**
     * 导出当前用户的历史归档。
     *
     * @param user 当前用户
     * @return 归档
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> history(LoginUser user) {
        Session existing = sessions.get(user.id());
        if (existing == null) {
            return Map.of("version", 1, "messages", List.of());
        }
        synchronized (existing) {
            return GSON.fromJson(existing.engine.exportHistory(), Map.class);
        }
    }

    /**
     * 清空当前用户的对话（不重建工具）。
     *
     * @param user 当前用户
     */
    public void reset(LoginUser user) {
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
    public void invalidateAll() {
        generation.incrementAndGet();
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

    private Session sessionFor(LoginUser user) {
        Session session = sessions.computeIfAbsent(user.id(), id -> build(user));
        if (session.generation != generation.get()) {
            session.close();
            Session rebuilt = build(user);
            sessions.put(user.id(), rebuilt);
            return rebuilt;
        }
        return session;
    }

    private Session build(LoginUser user) {
        EmbedAiChat.Engine engine = new EmbedAiChat.Engine();
        engine.config.provider = props.getProvider();
        engine.config.baseUrl = props.getBaseUrl();
        engine.config.model = props.getModel();
        engine.config.key = props.getApiKey();
        engine.config.maxTokens = props.getMaxTokens();
        engine.config.contextBudget = props.getContextBudget();
        engine.allowTools = props.isAllowTools();

        registerTools(engine, user);
        Session session = new Session(engine, generation.get());
        loadSkills(session);
        attachMcp(session);
        return session;
    }

    private void loadSkills(Session session) {
        List<AiSkill> skills = sql.selectList(Query.of(AiSkill.class).where("enabled", true));
        for (AiSkill skill : skills) {
            try {
                String name = session.engine.loadSkill(Path.of(skill.directory));
                session.skills.add(name);
                if (Boolean.TRUE.equals(skill.autoActivate)) {
                    session.engine.activateSkill(name);
                }
            } catch (Exception e) {
                session.skillErrors.add(skill.name + "：" + e.getMessage());
                LOG.warn("[ims] Skill 加载失败（{}）：{}", skill.directory, e.getMessage());
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
     * @param user   当前用户
     */
    private void registerTools(EmbedAiChat.Engine engine, LoginUser user) {
        engine.registerTool(new EmbedAiChat.Tool(
                "ims_list_models",
                "List data models the current user can access",
                schema("""
                        {"type":"object","properties":{},"additionalProperties":false}
                        """),
                args -> json(metaService.nav(user))));

        engine.registerTool(new EmbedAiChat.Tool(
                "ims_model_schema",
                "Get field definitions of a model (types, required, enum options)",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"}},
                         "required":["model"],
                         "additionalProperties":false}
                        """),
                args -> json(metaService.schema(user, required(args, "model")))));

        engine.registerTool(new EmbedAiChat.Tool(
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
                    return json(crud.page(user, required(args, "model"), params));
                }));

        engine.registerTool(new EmbedAiChat.Tool(
                "ims_get",
                "Get one row by primary key",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"},"id":{"type":"string","description":"UUID"}},
                         "required":["model","id"],
                         "additionalProperties":false}
                        """),
                args -> json(crud.get(user, required(args, "model"), required(args, "id")))));

        engine.registerTool(new EmbedAiChat.Tool(
                "ims_create",
                "Create a row. Field names come from ims_model_schema",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"},"values":{"type":"object"}},
                         "required":["model","values"],
                         "additionalProperties":false}
                        """),
                args -> json(crud.create(user, required(args, "model"), valuesOf(args)))));

        engine.registerTool(new EmbedAiChat.Tool(
                "ims_update",
                "Update a row by primary key; only provided fields change",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"},"id":{"type":"string","description":"UUID"},"values":{"type":"object"}},
                         "required":["model","id","values"],
                         "additionalProperties":false}
                        """),
                args -> json(crud.update(user, required(args, "model"),
                        required(args, "id"), valuesOf(args)))));

        engine.registerTool(new EmbedAiChat.Tool(
                "ims_delete",
                "Delete a row by primary key. Requires delete permission",
                schema("""
                        {"type":"object",
                         "properties":{"model":{"type":"string"},"id":{"type":"string","description":"UUID"}},
                         "required":["model","id"],
                         "additionalProperties":false}
                        """),
                args -> {
                    crud.delete(user, required(args, "model"), required(args, "id"));
                    return json(Map.of("deleted", true));
                }));

        engine.registerTool(new EmbedAiChat.Tool(
                "ims_stats",
                "Count rows per model visible to the current user",
                schema("""
                        {"type":"object","properties":{},"additionalProperties":false}
                        """),
                args -> {
                    Map<String, Object> counts = new LinkedHashMap<>();
                    for (Map<String, Object> item : metaService.nav(user)) {
                        String key = String.valueOf(item.get("key"));
                        counts.put(key, crud.count(user, key));
                    }
                    return json(counts);
                }));
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
        private final long generation;
        private final List<EmbedAiChat.McpClient> mcpClients = new ArrayList<>();
        private final List<Map<String, Object>> mcpStatus = new ArrayList<>();
        private final List<String> skills = new ArrayList<>();
        private final List<String> skillErrors = new ArrayList<>();

        private Session(EmbedAiChat.Engine engine, long generation) {
            this.engine = engine;
            this.generation = generation;
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
