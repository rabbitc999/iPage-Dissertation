package com.rabbitc.ims.web;

import com.rabbitc.ims.ai.AiConfiguration;
import com.rabbitc.ims.meta.MetaService;
import com.rabbitc.ims.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 元数据接口：侧边菜单与表单/表格 schema。
 * <p>
 * 前端不写死任何字段名与权限判断，全部从这里拿。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    private final MetaService metaService;
    private final AiConfiguration ai;

    /**
     * 构造控制器。
     *
     * @param metaService 元数据服务
     */
    public MetaController(MetaService metaService, AiConfiguration ai) {
        this.ai = ai;
        this.metaService = metaService;
    }

    /**
     * 当前用户可见的菜单。
     *
     * @return 菜单项
     */
    @GetMapping("/nav")
    public List<Map<String, Object>> nav() {
        return metaService.nav(UserContext.require());
    }

    /** 即使部署裁剪了 AI，也向前端声明入口不可用。 */
    @GetMapping("/ai/module")
    public Map<String, Boolean> aiModule() {
        return Map.of("enabled", ai.enabled(), "available", ai.available());
    }

    /**
     * 模型 schema。
     *
     * @param key 模型 key
     * @return schema
     */
    @GetMapping("/models/{key}/schema")
    public Map<String, Object> schema(@PathVariable String key) {
        return metaService.schema(UserContext.require(), key);
    }

    /**
     * 健康检查（免登录）。
     *
     * @return 状态
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }
}
