package com.rabbitc.ims.ai;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.rabbitc.ims.event.AiConfigChangedEvent;
import com.rabbitc.ims.model.Department;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.enums.RecordStatus;
import com.rabbitc.ims.enums.ToolAccess;
import com.rabbitc.ims.model.AiModel;
import com.rabbitc.ims.model.SysSetting;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.service.AuditService;
import com.rabbitc.ims.web.ApiException;
import com.rabbitc.ipage.exception.CheckerException;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** AI 模块开关和模型目录。对外响应只包含配置元数据。 */
@Service
public class AiConfiguration {
    private static final Gson JSON = new Gson();
    private static final KeyAccess DEFAULT_ACCESS = new KeyAccess(AccessMode.ALL, Set.of(), Set.of());
    private static final KeyAccess INVALID_ACCESS = new KeyAccess(AccessMode.ALLOW, Set.of(), Set.of());
    private static final String ENABLED = "aiAssistantEnabled";
    private final AiProperties properties;
    private final ModelSession sql;
    private final AiKeyVault vault;
    private final AuditService audit;
    private final ApplicationEventPublisher publisher;

    public AiConfiguration(AiProperties properties, ModelSession sql, AiKeyVault vault, AuditService audit,
                           ApplicationEventPublisher publisher) {
        this.properties = properties;
        this.sql = sql;
        this.vault = vault;
        this.audit = audit;
        this.publisher = publisher;
    }

    public boolean available() {
        return properties.isAvailable();
    }

    public boolean enabled() {
        return available() && sql.<SysSetting>selectOne(Query.of(SysSetting.class).where("settingKey", ENABLED))
                .map(row -> Boolean.parseBoolean(row.settingValue)).orElse(false);
    }

    public void requireEnabled() {
        if (!enabled()) throw ApiException.notImplemented("智能助手已关闭");
    }

    public List<AiModel> models() {
        return sql.<AiModel>selectList(Query.of(AiModel.class)).stream()
                .sorted(Comparator.comparing((AiModel model) -> model.name).thenComparing(model -> model.id)).toList();
    }

    public boolean current(AiModel model) {
        return enabled() && sql.<AiModel>selectOne(Query.of(AiModel.class).where("id", model.id))
                .map(row -> Boolean.TRUE.equals(row.enabled) && java.util.Objects.equals(row.revision, model.revision))
                .orElse(false);
    }

    public String credential(AiModel model) {
        return vault.decrypt(model.id, model.encryptedKey);
    }

    public enum AccessMode { ALL, ALLOW, DENY }

    public record KeyAccess(AccessMode mode, Set<String> departmentIds, Set<String> userIds) {
        public KeyAccess {
            departmentIds = departmentIds == null ? Set.of() : departmentIds;
            userIds = userIds == null ? Set.of() : userIds;
        }

        boolean permits(LoginUser user) {
            boolean selected = userIds.contains(user.id())
                    || (user.deptId() != null && departmentIds.contains(user.deptId()));
            return mode == AccessMode.ALL || (mode == AccessMode.ALLOW && selected)
                    || (mode == AccessMode.DENY && !selected);
        }
    }

    public boolean canUse(AiModel model, LoginUser user) {
        return user.has("ai:use") && Boolean.TRUE.equals(model.enabled) && model.encryptedKey != null
                && user.level() >= model.minLevel && keyAccess(model).permits(user);
    }

    public static ToolAccess maxToolAccess(AiModel model) {
        return Objects.requireNonNullElse(model.maxToolAccess, ToolAccess.READ_ONLY);
    }

    public ToolAccess requireToolAccess(AiModel model, ToolAccess requested) {
        ToolAccess access = Objects.requireNonNullElse(requested, ToolAccess.READ_ONLY);
        if (!Boolean.TRUE.equals(model.allowTools)) throw ApiException.forbidden("此模型未启用工具");
        ToolAccess max = maxToolAccess(model);
        if (!max.allows(access)) throw ApiException.forbidden("请求的工具权限超过此模型的上限「" + max.label() + "」");
        return access;
    }

    public void requireMcpAccess(LoginUser user, String modelId, ToolAccess requested, ToolAccess required) {
        requireEnabled();
        AiModel model = require(modelId);
        if (!canUse(model, user)) throw ApiException.forbidden("没有使用该 AI 模型的权限");
        ToolAccess access = requireToolAccess(model, requested);
        if (!access.allows(required)) {
            throw ApiException.forbidden("当前 AI 权限为「" + access.label() + "」，此操作需要「" + required.label() + "」");
        }
    }

    private static KeyAccess keyAccess(AiModel model) {
        if (model.keyAccess == null) return DEFAULT_ACCESS;
        try {
            KeyAccess access = JSON.fromJson(new String(model.keyAccess, StandardCharsets.UTF_8), KeyAccess.class);
            return access != null && access.mode() != null ? access : INVALID_ACCESS;
        } catch (JsonParseException error) {
            return INVALID_ACCESS;
        }
    }

    /** 员工授权使用登录用户的 ID。员工档案与登录账号在当前系统中独立。 */
    public Map<String, Object> accessOptions() {
        var departments = sql.<Department>selectList(Query.of(Department.class)).stream()
                .sorted(Comparator.comparing(Department::toString))
                .map(dept -> Map.of("id", dept.id, "label", dept.name + "（" + dept.code + "）")).toList();
        var users = sql.<User>selectList(Query.of(User.class).where("status", RecordStatus.ENABLED)).stream()
                .sorted(Comparator.comparing(user -> user.username))
                .map(user -> Map.of("id", user.id, "label", user.name + "（" + user.username + "）")).toList();
        return Map.of("departments", departments, "users", users);
    }

    private byte[] validateAccess(KeyAccess access) {
        if (access.mode() == null) throw ApiException.badRequest("请选择密钥使用范围");
        if (access.mode() == AccessMode.ALL) return null;
        var departments = access.departmentIds();
        var users = access.userIds();
        int size = departments.size() + users.size();
        if (size == 0 || size > 2000) throw ApiException.badRequest("请选择 1–2000 个部门或员工账号");
        if (departments.stream().anyMatch(Objects::isNull) || users.stream().anyMatch(Objects::isNull)) {
            throw ApiException.badRequest("授权对象无效");
        }
        if ((!departments.isEmpty() && sql.count(Query.of(Department.class).in("id", departments)) != departments.size())
                || (!users.isEmpty() && sql.count(Query.of(User.class).in("id", users)) != users.size())) {
            throw ApiException.badRequest("所选部门或员工账号已不存在，请重新选择");
        }
        return JSON.toJson(access).getBytes(StandardCharsets.UTF_8);
    }

    public record ModelInput(String name, String modelId, String provider, String baseUrl, String apiKey,
                             boolean enabled, boolean vision, boolean allowTools, int minLevel, String keyName, KeyAccess keyAccess, ToolAccess maxToolAccess) {}

    public static Map<String, Object> view(AiModel model) {
        return Map.ofEntries(Map.entry("id", model.id), Map.entry("name", model.name),
                Map.entry("modelId", model.modelId), Map.entry("provider", model.provider),
                Map.entry("baseUrl", model.baseUrl), Map.entry("enabled", model.enabled),
                Map.entry("vision", model.vision), Map.entry("allowTools", model.allowTools),
                Map.entry("maxToolAccess", maxToolAccess(model)),
                Map.entry("minLevel", model.minLevel), Map.entry("hasKey", model.encryptedKey != null),
                Map.entry("keyName", Objects.requireNonNullElse(model.keyName, "默认密钥")),
                Map.entry("keyAccess", keyAccess(model)));
    }

    public synchronized Map<String, Object> save(LoginUser user, String id, ModelInput input) {
        AiModel model = id == null ? new AiModel() : require(id);
        String provider = required(input.provider(), "协议");
        if (!Set.of("openai", "anthropic").contains(provider)) throw ApiException.badRequest("不支持该协议");
        String url = baseUrl(input.baseUrl());
        String key = input.apiKey() == null ? "" : input.apiKey().trim();
        if (key.length() > 4096 || key.chars().anyMatch(c -> c <= 32 || c >= 127) || key.equals("sk-")) {
            throw ApiException.badRequest("请输入完整的供应商 API Key，支持带 sk- 前缀和普通令牌。请去掉 Bearer 和空格");
        }
        if (key.isEmpty() && (model.encryptedKey == null || !url.equals(model.baseUrl) || !provider.equals(model.provider))) {
            throw ApiException.badRequest("新增模型或更改服务地址、协议时，请输入 API Key");
        }
        if (input.minLevel() < 0 || input.minLevel() > 4) throw ApiException.badRequest("角色等级必须在 0–4 之间");
        model.name = required(input.name(), "显示名称");
        if (input.keyName() != null) {
            model.keyName = required(input.keyName(), "密钥名称");
        }
        if (input.keyAccess() != null) model.keyAccess = validateAccess(input.keyAccess());
        model.modelId = required(input.modelId(), "供应商模型 ID");
        model.provider = provider;
        model.baseUrl = url;
        model.enabled = input.enabled();
        model.vision = input.vision();
        model.allowTools = input.allowTools();
        if (input.maxToolAccess() != null) model.maxToolAccess = input.maxToolAccess();
        model.minLevel = input.minLevel();
        if (id == null) model.id = UUID.randomUUID().toString();
        model.revision = UUID.randomUUID().toString();
        if (!key.isEmpty()) model.encryptedKey = vault.encrypt(model.id, key);
        sql.transaction(tx -> {
            try {
                if (id == null) tx.insert(model);
                else if (tx.update(model) != 1) throw ApiException.notFound("模型不存在或已被删除");
                audit.record(user, id == null ? "新增" : "修改", "AI 模型", model.id, null);
            } catch (CheckerException e) {
                throw ApiException.badRequest("模型配置保存失败");
            }
        });
        changed("模型配置更新");
        return view(model);
    }

    public synchronized void delete(LoginUser user, String id) {
        AiModel model = require(id);
        sql.transaction(tx -> {
            tx.delete(model);
            audit.record(user, "删除", "AI 模型", id, null);
        });
        changed("模型已删除");
    }

    public synchronized void setEnabled(LoginUser user, boolean enabled) {
        sql.transaction(tx -> {
            var found = tx.<SysSetting>selectOne(Query.of(SysSetting.class).where("settingKey", ENABLED));
            SysSetting row = found.orElseGet(SysSetting::new);
            row.settingKey = ENABLED;
            row.settingValue = Boolean.toString(enabled);
            row.updatedAt = LocalDateTime.now();
            try {
                if (found.isPresent()) tx.update(row);
                else tx.insert(row);
                audit.record(user, enabled ? "启用" : "关闭", "智能助手", null, null);
            } catch (CheckerException e) {
                throw ApiException.badRequest("AI 模块状态保存失败");
            }
        });
        changed("AI 模块状态变更");
    }

    private AiModel require(String id) {
        return sql.<AiModel>selectOne(Query.of(AiModel.class).where("id", id))
                .orElseThrow(() -> ApiException.notFound("模型不存在"));
    }

    private void changed(String reason) {
        publisher.publishEvent(new AiConfigChangedEvent(reason));
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank() || value.trim().length() > 255) {
            throw ApiException.badRequest(label + "须为 1–255 个字符");
        }
        return value.trim();
    }

    private static String baseUrl(String value) {
        String url = required(value, "服务地址").replaceAll("/+$", "");
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && Set.of("localhost", "127.0.0.1", "[::1]").contains(host)))) return url;
        } catch (IllegalArgumentException ignored) {
            // 不回显输入 URL，以免泄漏意外粘贴的凭据。
        }
        throw ApiException.badRequest("服务地址须为 HTTPS，或本机回环 HTTP 地址");
    }
}
