package com.rabbitc.ims.web;

import com.rabbitc.ims.ai.AiConfiguration;
import com.rabbitc.ims.security.RequireLevel;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ipage.meta.ModelPermission;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理员可在助手关闭时继续管理模型及模块开关。 */
@RestController
@RequestMapping("/api/ai/config")
@ConditionalOnProperty(prefix = "ims.ai", name = "available", havingValue = "true", matchIfMissing = true)
@RequireLevel(ModelPermission.ADMIN)
public class AiConfigurationController {
    private final AiConfiguration config;

    public AiConfigurationController(AiConfiguration config) {
        this.config = config;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("enabled", config.enabled(), "models", config.models().stream().map(AiConfiguration::view).toList());
    }

    @GetMapping("/access-options")
    public Map<String, Object> accessOptions() {
        return config.accessOptions();
    }

    public record ModuleInput(Boolean enabled) {}

    @PutMapping("/module")
    public Map<String, Boolean> module(@RequestBody ModuleInput input) {
        if (input.enabled() == null) throw ApiException.badRequest("请选择模块开关状态");
        config.setEnabled(UserContext.require(), input.enabled());
        return Map.of("enabled", input.enabled());
    }

    @PostMapping("/models")
    public Map<String, Object> create(@RequestBody AiConfiguration.ModelInput input) {
        return config.save(UserContext.require(), null, input);
    }

    @PutMapping("/models/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody AiConfiguration.ModelInput input) {
        return config.save(UserContext.require(), id, input);
    }

    @DeleteMapping("/models/{id}")
    public Map<String, Boolean> delete(@PathVariable String id) {
        config.delete(UserContext.require(), id);
        return Map.of("ok", true);
    }
}
