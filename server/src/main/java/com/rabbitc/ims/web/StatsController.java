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
import com.rabbitc.ims.service.AssociationAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
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
    private final AssociationAccess associations;

    /**
     * 构造控制器。
     *
     * @param crud       CRUD 服务
     * @param registry   模型注册表
     * @param metaService 元数据服务
     * @param sql        SQL 会话
     */
    public StatsController(CrudService crud, ModelRegistry registry, MetaService metaService, ModelSession sql, AssociationAccess associations) {
        this.crud = crud;
        this.registry = registry;
        this.metaService = metaService;
        this.sql = sql;
        this.associations = associations;
    }

    /**
     * 工作台数据。
     *
     * @return 卡片、工单分布与最近审计
     */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(required = false) String department) {
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
        if (metaService.canView(user, registry.requireMeta("ticket"), "ticket")) {
            List<Ticket> tickets = crud.ticketsForReport(user, from, to, department);
            Map<String, Long> counts = new LinkedHashMap<>();
            for (TicketStatus status : TicketStatus.values()) {
                counts.put(status.name(), 0L);
            }
            long overdue = 0;
            LocalDate today = LocalDate.now();
            for (Ticket ticket : tickets) {
                if (ticket.status != null) {
                    counts.merge(ticket.status.name(), 1L, Long::sum);
                }
                if ((ticket.status == TicketStatus.OPEN || ticket.status == TicketStatus.PROCESSING)
                        && ticket.dueDate != null && ticket.dueDate.isBefore(today)) {
                    overdue++;
                }
            }
            body.put("tickets", counts);
            body.put("ticketTotal", tickets.size());
            body.put("overdueTickets", overdue);
            body.put("ticketDepartments", associations.departments(user));
        }
        body.put("recent", recentAudit(user));
        return body;
    }

    /** 导出与工作台统计和工单列表相同范围的工单。 */
    @GetMapping("/stats/tickets.csv")
    public ResponseEntity<String> exportTickets(@RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to,
                                                @RequestParam(required = false) String department) {
        List<Ticket> tickets = crud.ticketsForReport(UserContext.require(), from, to, department);
        StringBuilder csv = new StringBuilder("\uFEFF工单ID,标题,状态,优先级,部门,处理人,创建时间,截止日期\r\n");
        for (Ticket ticket : tickets) {
            csv.append(String.join(",", csvCell(ticket.id), csvCell(ticket.title), csvCell(ticket.status),
                    csvCell(ticket.priority), csvCell(ticket.dept == null ? null : ticket.dept.name),
                    csvCell(ticket.assignee == null ? null : ticket.assignee.name),
                    csvCell(ticket.createdAt), csvCell(ticket.dueDate))).append("\r\n");
        }
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=ims-tickets.csv")
                .body(csv.toString());
    }

    private static String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        String stripped = text.stripLeading();
        if ((!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0)
                || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
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
