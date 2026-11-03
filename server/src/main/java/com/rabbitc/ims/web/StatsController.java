package com.rabbitc.ims.web;

import com.rabbitc.ipage.enums.SortType;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.enums.TicketStatus;
import com.rabbitc.ims.meta.MetaService;
import com.rabbitc.ims.meta.ModelRegistry;
import com.rabbitc.ims.model.AuditLog;
import com.rabbitc.ims.model.Ticket;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.service.CrudService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台统计。
 * <p>
 * 所有数字都走与列表接口同一套权限与数据范围逻辑，不会出现「列表看不到、统计却数得出来」的信息泄漏。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api")
public class StatsController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final CrudService crud;
    private final ModelRegistry registry;
    private final MetaService metaService;
    private final ModelSession sql;

    /**
     * 构造控制器。
     *
     * @param crud       CRUD 服务
     * @param registry   模型注册表
     * @param metaService 元数据服务
     * @param sql        SQL 会话
     */
    public StatsController(CrudService crud, ModelRegistry registry, MetaService metaService, ModelSession sql) {
        this.crud = crud;
        this.registry = registry;
        this.metaService = metaService;
        this.sql = sql;
    }

    /**
     * 工作台数据。
     *
     * @return 卡片、工单分布与最近审计
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        LoginUser user = UserContext.require();
        Map<String, Object> body = new LinkedHashMap<>();

        List<Map<String, Object>> cards = new ArrayList<>();
        for (String key : registry.exposedKeys()) {
            ModelMeta meta = registry.requireMeta(key);
            if (!metaService.canView(user, meta, key)) {
                continue;
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("key", key);
            card.put("name", meta.name());
            card.put("icon", meta.icon());
            card.put("count", crud.count(user, key));
            cards.add(card);
        }
        body.put("cards", cards);
        body.put("tickets", ticketStats(user));
        body.put("recent", recentAudit(user));
        return body;
    }

    private Map<String, Object> ticketStats(LoginUser user) {
        Map<String, Object> stats = new LinkedHashMap<>();
        ModelMeta meta = registry.requireMeta("ticket");
        if (!metaService.canView(user, meta, "ticket")) {
            return stats;
        }
        for (TicketStatus status : TicketStatus.values()) {
            Query query = Query.of(meta, sql.dialect());
            query.where("status", status.name());
            if (!user.isAdmin() && user.dataScope() == com.rabbitc.ims.enums.DataScope.DEPT) {
                if (user.deptId() == null) {
                    stats.put(status.name(), 0L);
                    continue;
                }
                query.where("deptId", user.deptId());
            } else if (!user.isAdmin() && user.dataScope() == com.rabbitc.ims.enums.DataScope.SELF) {
                query.where("createBy", user.id());
            }
            stats.put(status.name(), sql.count(query));
        }
        return stats;
    }

    private List<Map<String, Object>> recentAudit(LoginUser user) {
        if (!user.isAdmin()) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        List<AuditLog> logs = sql.selectList(Query.of(AuditLog.class)
                .orderBy("id", SortType.DESC)
                .page(1, 6));
        for (AuditLog log : logs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", log.id);
            item.put("action", log.action);
            item.put("model", log.model);
            item.put("actor", log.actorName);
            item.put("detail", log.detail);
            item.put("time", log.createdAt == null ? "" : log.createdAt.format(TIME));
            list.add(item);
        }
        return list;
    }
}
