package com.rabbitc.ims.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitc.ims.security.RequireLevel;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.service.LogRetentionService;
import com.rabbitc.ipage.meta.ModelPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅超级管理员可查看或修改运行日志的保留策略。 */
@RestController
@RequestMapping("/api/system/log-settings")
@RequireLevel(ModelPermission.SUPER_ADMIN)
public class LogSettingsController {
    private final LogRetentionService retention;

    public LogSettingsController(LogRetentionService retention) {
        this.retention = retention;
    }

    public record Update(JsonNode retentionDays) {}

    @GetMapping
    public LogRetentionService.Settings settings() {
        return retention.settings();
    }

    @PutMapping
    public LogRetentionService.Settings save(@RequestBody Update update) {
        JsonNode value = update.retentionDays();
        Integer days = value != null && value.isIntegralNumber() && value.canConvertToInt()
                ? value.intValue() : null;
        return retention.save(UserContext.require(), days);
    }
}
