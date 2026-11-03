package com.rabbitc.ims.service;

import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ims.model.AuditLog;
import com.rabbitc.ims.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计日志。
 * <p>
 * 写日志本身不应该让业务失败：这里吞掉异常并记录到应用日志，避免「审计表写不进去导致整个新增回滚」。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Service
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private static final int DETAIL_LIMIT = 180;

    private final ModelSession sql;

    /**
     * 构造服务。
     *
     * @param sql SQL 会话
     */
    public AuditService(ModelSession sql) {
        this.sql = sql;
    }

    /**
     * 记录一条操作日志。
     *
     * @param user   操作人
     * @param action 动作
     * @param model  模型名称
     * @param target 目标主键
     * @param detail 详情
     */
    public void record(LoginUser user, String action, String model, Object target, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.actorId = user == null ? null : user.id();
            log.actorName = user == null ? "anonymous" : user.name();
            log.action = action;
            log.model = model;
            log.targetId = target == null ? "" : String.valueOf(target);
            log.detail = truncate(detail);
            log.ip = clientIp();
            log.createdAt = LocalDateTime.now();
            sql.insert(log);
        } catch (Exception e) {
            LOG.warn("[ims] 审计日志写入失败：{}", e.getMessage());
        }
    }

    private static String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        String single = detail.replaceAll("\\s+", " ").trim();
        return single.length() <= DETAIL_LIMIT ? single : single.substring(0, DETAIL_LIMIT) + "…";
    }

    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
