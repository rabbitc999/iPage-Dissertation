package com.rabbitc.ims.service;

import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ims.event.AiConfigChangedEvent;
import com.rabbitc.ims.model.AiMcpServer;
import com.rabbitc.ims.model.AiSkill;
import com.rabbitc.ims.security.LoginUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP / Skill 配置变更钩子：任何一次保存或删除都会让 AI 会话重建。
 * <p>
 * 不用重启服务，也不用点「重载」——在页面上改完 MCP 命令，下一句话就生效。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class AiConfigHook implements ModelHook {

    private final ApplicationEventPublisher publisher;

    /**
     * 构造钩子。
     *
     * @param publisher 事件发布器
     */
    public AiConfigHook(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public boolean supports(Class<?> modelClass) {
        return modelClass == AiMcpServer.class || modelClass == AiSkill.class;
    }

    @Override
    public void afterCreate(ModelMeta meta, Object entity, Map<String, Object> body, LoginUser user) {
        publisher.publishEvent(new AiConfigChangedEvent("新增 " + meta.name()));
    }

    @Override
    public void afterUpdate(ModelMeta meta, Object entity, Object before,
                            Map<String, Object> body, LoginUser user) {
        publisher.publishEvent(new AiConfigChangedEvent("修改 " + meta.name()));
    }

    @Override
    public void beforeDelete(ModelMeta meta, Object entity, LoginUser user) {
        publisher.publishEvent(new AiConfigChangedEvent("删除 " + meta.name()));
    }
}
