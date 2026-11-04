package com.rabbitc.ims.service;

import com.rabbitc.ims.event.AiConfigChangedEvent;
import com.rabbitc.ims.meta.ModelRegistry;
import com.rabbitc.ims.model.AiMcpServer;
import com.rabbitc.ims.model.AiModel;
import com.rabbitc.ims.model.AiSkill;
import com.rabbitc.ims.model.Asset;
import com.rabbitc.ims.model.AuditLog;
import com.rabbitc.ims.model.BusinessEvent;
import com.rabbitc.ims.model.Department;
import com.rabbitc.ims.model.Employee;
import com.rabbitc.ims.model.Permission;
import com.rabbitc.ims.model.Role;
import com.rabbitc.ims.model.RecordAttachment;
import com.rabbitc.ims.model.RolePermission;
import com.rabbitc.ims.model.SysFile;
import com.rabbitc.ims.model.SysSetting;
import com.rabbitc.ims.model.Ticket;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.model.UserRole;
import com.rabbitc.ims.model.ViewConfig;
import com.rabbitc.ims.security.AuthService;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.security.PasswordHasher;
import com.rabbitc.ims.seed.DataInitializer;
import com.rabbitc.ims.web.ApiException;
import com.rabbitc.ipage.enums.OperatorType;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.SqlCondition;
import com.rabbitc.ipage.sql.SqlStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 两阶段确认后按外键顺序清空数据库，并在同一事务恢复唯一管理入口。 */
@Service
public class DataResetService {
    private static final Logger LOG = LoggerFactory.getLogger(DataResetService.class);
    private static final String CONFIRMATION = "清空所有数据";
    private static final int EXPIRES_SECONDS = 120;
    private static final List<Class<?>> DELETE_ORDER = List.of(
            RecordAttachment.class, BusinessEvent.class, Ticket.class, Asset.class, Employee.class, ViewConfig.class,
            AiModel.class, AiMcpServer.class, AiSkill.class, SysFile.class, SysSetting.class,
            AuditLog.class, UserRole.class, RolePermission.class, User.class, Department.class,
            Permission.class, Role.class);

    public record Confirmation(String token, int expiresInSeconds) {}
    private record Pending(Confirmation confirmation, int tokenVersion, Instant expiresAt) {}

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final ModelSession sql;
    private final ModelRegistry registry;
    private final DataInitializer initializer;
    private final DataMaintenanceGate gate;
    private final PasswordHasher hasher;
    private final AuthService auth;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final RedisBrandCache brandCache;

    public DataResetService(ModelSession sql, ModelRegistry registry, DataInitializer initializer,
                            DataMaintenanceGate gate, PasswordHasher hasher, AuthService auth,
                            AuditService audit, ApplicationEventPublisher events,
                            ObjectProvider<RedisBrandCache> brandCache) {
        this.sql = sql;
        this.registry = registry;
        this.initializer = initializer;
        this.gate = gate;
        this.hasher = hasher;
        this.auth = auth;
        this.audit = audit;
        this.events = events;
        this.brandCache = brandCache.getIfAvailable();
    }

    public Confirmation verify(LoginUser operator, String password) {
        requireSuperAdmin(operator);
        User account = account(operator.id());
        if (password == null || password.isEmpty() || password.length() > 1024
                || !hasher.matches(password, account.passwordHash)) {
            throw ApiException.badRequest("当前密码不正确");
        }
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        var confirmation = new Confirmation(UUID.randomUUID().toString(), EXPIRES_SECONDS);
        pending.put(operator.id(), new Pending(confirmation, operator.tokenVersion(), now.plusSeconds(EXPIRES_SECONDS)));
        return confirmation;
    }

    public int execute(LoginUser operator, String token, String confirmation) {
        requireSuperAdmin(operator);
        if (!CONFIRMATION.equals(confirmation)) throw ApiException.badRequest("确认文字不匹配");
        var lock = gate.exclusive();
        if (!lock.tryLock()) {
            throw new ApiException(HttpStatus.CONFLICT, "系统还有请求正在处理，请稍后再确认清空");
        }
        try {
            LoginUser current = auth.load(operator.id());
            requireSuperAdmin(current);
            Pending request = pending.get(current.id());
            if (request == null || !request.expiresAt().isAfter(Instant.now())
                    || current.tokenVersion() != request.tokenVersion()
                    || current.tokenVersion() != operator.tokenVersion()
                    || !request.confirmation().token().equals(token)
                    || !pending.remove(current.id(), request)) {
                throw ApiException.badRequest("确认已过期或失效，请重新验证密码");
            }
            if (!new HashSet<>(registry.all()).equals(new HashSet<>(DELETE_ORDER))) {
                throw new ApiException(HttpStatus.CONFLICT, "当前数据库模型与清空顺序不匹配，操作已中止");
            }
            int[] deleted = {0};
            sql.transaction(tx -> {
                User account = account(current.id());
                account.deptId = null;
                account.avatar = null;
                account.phone = null;
                account.email = null;
                account.tokenVersion = current.tokenVersion() + 1;
                if (tx.update(account) != 1) throw new IllegalStateException("无法保留当前超级管理员");
                for (Class<?> type : DELETE_ORDER) {
                    ModelMeta meta = ModelMeta.of(type);
                    String idColumn = tx.dialect().quote(meta.idColumn());
                    SqlCondition condition = type == User.class
                            ? new SqlCondition(idColumn, OperatorType.NE, account.id)
                            : new SqlCondition(idColumn, OperatorType.IS_NOT_NULL, null);
                    deleted[0] += tx.update(SqlStatement.deleteWhere(meta, List.of(condition), tx.dialect()));
                }
                initializer.rebuildAccess(tx, account.id);
                audit.record(current, "清空", "系统数据", null, "删除 " + deleted[0] + " 条记录");
            });
            pending.clear();
            refreshAfterCommit();
            return deleted[0];
        } finally {
            lock.unlock();
        }
    }

    private void refreshAfterCommit() {
        if (brandCache != null) brandCache.invalidate();
        try {
            events.publishEvent(new AiConfigChangedEvent("系统数据已清空"));
        } catch (RuntimeException error) {
            LOG.warn("数据已清空，更新 AI 会话状态失败", error);
        }
    }

    private User account(String id) {
        return sql.findById(User.class, id).orElseThrow(() -> ApiException.unauthorized("账号已失效"));
    }

    private static void requireSuperAdmin(LoginUser user) {
        if (!user.reaches(ModelPermission.SUPER_ADMIN)) throw ApiException.forbidden("仅超级管理员可以清空数据");
    }
}
