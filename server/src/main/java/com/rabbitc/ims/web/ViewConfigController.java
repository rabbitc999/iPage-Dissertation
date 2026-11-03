package com.rabbitc.ims.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.model.ViewConfig;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.support.Ids;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 用户级界面配置：列显隐、每页条数。
 * <p>
 * 这里不经过通用 CRUD，因为「配置只属于当前用户」这条规则需要在服务端硬编码：
 * 用户只能读写自己的行，管理员也不能通过改 URL 去改别人的配置。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api/views")
public class ViewConfigController {

    /** 配置列是 VARCHAR(255)，留一点余量。 */
    private static final int MAX_CONFIG_LENGTH = 240;

    private final ModelSession sql;
    private final ObjectMapper mapper;

    /**
     * 构造控制器。
     *
     * @param sql    SQL 会话
     * @param mapper JSON 转换器
     */
    public ViewConfigController(ModelSession sql, ObjectMapper mapper) {
        this.sql = sql;
        this.mapper = mapper;
    }

    /**
     * 读取当前用户对某个模型的界面配置。
     *
     * @param modelKey 模型 key
     * @return 配置；没有时返回空对象
     */
    @GetMapping("/{modelKey}")
    public Map<String, Object> get(@PathVariable String modelKey) {
        Optional<ViewConfig> existing = find(UserContext.require(), modelKey);
        if (existing.isEmpty() || existing.get().config == null || existing.get().config.isBlank()) {
            return Map.of("config", Map.of());
        }
        try {
            Map<String, Object> config = mapper.readValue(existing.get().config, new TypeReference<>() {
            });
            return Map.of("config", config);
        } catch (Exception e) {
            return Map.of("config", Map.of());
        }
    }

    /**
     * 保存当前用户对某个模型的界面配置。
     *
     * @param modelKey 模型 key
     * @param body     配置
     * @return 保存后的配置
     */
    @PutMapping("/{modelKey}")
    public Map<String, Object> save(@PathVariable String modelKey, @RequestBody Map<String, Object> body) {
        LoginUser user = UserContext.require();
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw ApiException.badRequest("配置无法序列化");
        }
        if (json.length() > MAX_CONFIG_LENGTH) {
            throw ApiException.badRequest("配置过长，请减少自定义列");
        }
        Optional<ViewConfig> existing = find(user, modelKey);
        try {
            if (existing.isPresent()) {
                ViewConfig config = existing.get();
                config.config = json;
                config.updatedAt = LocalDateTime.now();
                sql.update(config);
            } else {
                ViewConfig config = new ViewConfig();
                // 主键是 UUID，插入前由应用侧赋值
                Ids.assignIfAbsent(config);
                config.ownerId = user.id();
                config.modelKey = modelKey;
                config.config = json;
                config.updatedAt = LocalDateTime.now();
                sql.insert(config);
            }
        } catch (com.rabbitc.ipage.exception.CheckerException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config", body);
        return result;
    }

    private Optional<ViewConfig> find(LoginUser user, String modelKey) {
        return sql.selectOne(Query.of(ViewConfig.class)
                .where("ownerId", user.id())
                .where("modelKey", modelKey));
    }
}
