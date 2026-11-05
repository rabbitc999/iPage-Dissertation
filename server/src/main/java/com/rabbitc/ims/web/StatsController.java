package com.rabbitc.ims.web;

import com.rabbitc.ipage.enums.SortType;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.meta.MetaService;
import com.rabbitc.ims.model.AuditLog;
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
 * 卡片完全由模型注册表推导：新增一个 {@code @ModelClass} 就多一张卡，不需要改这里。
 * 所有数字都走与列表接口同一套权限与数据范围逻辑，
 * 不会出现「列表看不到、统计却数得出来」的信息泄漏。
 * <p>
 * 原本还有一块工单专属的「状态分布 + 逾期数 + 报表导出」，随工单模型一起移除。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api")
public class StatsController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final CrudService crud;
    private final MetaService metaService;
    private final ModelSession sql;

    /**
     * 构造控制器。
     *
     * @param crud        CRUD 服务
     * @param metaService 元数据服务
     * @param sql         SQL 会话
     */
    public StatsController(CrudService crud, MetaService metaService, ModelSession sql) {
        this.crud = crud;
        this.metaService = metaService;
        this.sql = sql;
    }

    /**
     * 工作台数据。
     *
     * @return 卡片与最近审计
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        LoginUser user = UserContext.require();
        Map<String, Object> body = new LinkedHashMap<>();

        List<Map<String, Object>> cards = new ArrayList<>();
        // 直接复用侧边栏的判定：卡片与菜单永远一致（含 AI 关闭时隐藏 MCP/Skill 这类规则）
        for (Map<String, Object> item : metaService.nav(user)) {
            String key = String.valueOf(item.get("key"));
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("key", key);
            card.put("name", item.get("name"));
            card.put("icon", item.get("icon"));
            card.put("count", crud.count(user, key));
            cards.add(card);
        }
        body.put("cards", cards);
        body.put("recent", recentAudit(user));
        return body;
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
