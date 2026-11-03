package com.rabbitc.ims.web;

import com.rabbitc.ipage.exception.CheckerException;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.model.SysSetting;
import com.rabbitc.ims.security.RequirePermission;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 系统图像设置接口。
 * <p>
 * 两组端点、两种受众：
 * <ul>
 *     <li>{@code GET/PUT /api/settings}：管理端，需要 {@code system:setting:manage}
 *     权限点；GET 返回全部键值，PUT 只接受键白名单内的键，逐个 upsert；</li>
 *     <li>{@code GET /api/brand}：公开端点（见 {@code WebConfig} 排除名单），给登录页、
 *     侧边栏取站点名与品牌图，只返回白名单键，未配置的键返回默认值。</li>
 * </ul>
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
public class SettingController {

    /** 允许写入的设置键及默认值；键白名单同时挡住任意键注入。 */
    private static final Map<String, String> DEFINED_KEYS = new LinkedHashMap<>();

    static {
        DEFINED_KEYS.put("siteName", "iPage 管理系统");
        DEFINED_KEYS.put("logoUrl", "");
        DEFINED_KEYS.put("loginImageUrl", "");
    }

    private final ModelSession sql;
    private final AuditService audit;

    /**
     * 构造控制器。
     *
     * @param sql   SQL 会话
     * @param audit 审计服务
     */
    public SettingController(ModelSession sql, AuditService audit) {
        this.sql = sql;
        this.audit = audit;
    }

    /**
     * 读取全部系统设置（管理端）。
     *
     * @return 键值表
     */
    @RequirePermission("system:setting:manage")
    @GetMapping("/api/settings")
    public Map<String, Object> all() {
        Map<String, Object> result = new LinkedHashMap<>();
        DEFINED_KEYS.forEach((key, defaultValue) -> result.put(key, defaultValue));
        findAll().forEach(setting -> result.put(setting.settingKey, setting.settingValue));
        return result;
    }

    /**
     * 保存系统设置（管理端）。只接受键白名单内的键，值必须是字符串。
     *
     * @param body 键值表
     * @return 保存后的完整键值表
     */
    @RequirePermission("system:setting:manage")
    @PutMapping("/api/settings")
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            if (!DEFINED_KEYS.containsKey(key)) {
                throw ApiException.badRequest("未知设置项：" + key);
            }
            if (entry.getValue() != null && !(entry.getValue() instanceof String)) {
                throw ApiException.badRequest("设置值必须是字符串：" + key);
            }
            String value = entry.getValue() == null ? "" : (String) entry.getValue();
            if (value.length() > 255) {
                throw ApiException.badRequest("设置值过长，大图请通过文件接口上传后引用链接");
            }
            upsert(key, value);
        }
        audit.record(UserContext.require(), "修改", "系统设置",
                null, body.keySet().toString());
        return all();
    }

    /**
     * 公开品牌信息：登录页与侧边栏展示用，只含白名单键。
     *
     * @return 站点名与图像地址
     */
    @GetMapping("/api/brand")
    public Map<String, Object> brand() {
        Map<String, Object> result = new LinkedHashMap<>();
        DEFINED_KEYS.forEach((key, defaultValue) -> result.put(key, defaultValue));
        findAll().forEach(setting -> result.put(setting.settingKey, setting.settingValue));
        return result;
    }

    /**
     * 键值 upsert：存在则更新，不存在则插入（主键由 ipage 自动补 UUID）。
     */
    private void upsert(String key, String value) {
        Optional<SysSetting> found = sql.selectOne(Query.of(SysSetting.class)
                .where("settingKey", key));
        try {
            if (found.isPresent()) {
                SysSetting setting = found.get();
                setting.settingValue = value;
                setting.updatedAt = LocalDateTime.now();
                sql.update(setting);
            } else {
                SysSetting setting = new SysSetting();
                setting.settingKey = key;
                setting.settingValue = value;
                setting.updatedAt = LocalDateTime.now();
                sql.insert(setting);
            }
        } catch (CheckerException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    /**
     * 读取全部设置行：设置行总数固定很小，直接全量查询。
     */
    private List<SysSetting> findAll() {
        return sql.selectList(Query.of(SysSetting.class));
    }
}
