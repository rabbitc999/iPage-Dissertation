package com.rabbitc.ims.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.meta.MetaService;
import com.rabbitc.ims.meta.ModelRegistry;
import com.rabbitc.ims.model.AuditLog;
import com.rabbitc.ims.service.AssociationAccess;
import com.rabbitc.ims.service.AuditService;
import com.rabbitc.ims.service.CrudService;
import com.rabbitc.ims.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 在隔离 H2 库验证日志权限同时约束 schema 和真实 CRUD，角色等级不能代替授权。 */
class AuditLogPermissionsTest {
    private ModelSession sql;
    private MetaService meta;
    private CrudService crud;
    private final ModelMeta logMeta = ModelMeta.of(AuditLog.class);

    @BeforeEach void setup() throws Exception {
        String url = "jdbc:h2:mem:audit_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        sql = ModelSession.sql(() -> DriverManager.getConnection(url));
        sql.synchronizeSchema(AuditLog.class);
        ModelRegistry registry = mock(ModelRegistry.class);
        doReturn(AuditLog.class).when(registry).require("auditLog");
        when(registry.requireMeta("auditLog")).thenReturn(logMeta);
        AssociationAccess associations = new AssociationAccess(sql);
        meta = new MetaService(sql, registry, associations, null);
        crud = new CrudService(sql, registry, meta, mock(AuditService.class),
                new ObjectMapper(), List.of(), associations);
    }

    private LoginUser user(int level, String... permissions) {
        return new LoginUser("u", "test", "test", null, level, Set.of(permissions), DataScope.ALL, null, 0);
    }

    private AuditLog log() throws Exception {
        AuditLog log = new AuditLog();
        log.action = "TEST";
        sql.insert(log);
        return log;
    }

    @Test void staffManagerAndAdminNeedExplicitGrants() {
        for (int level : List.of(1, 2, 3)) {
            assertFalse(meta.canView(user(level), logMeta, "auditLog"));
            assertFalse(meta.canDelete(user(level), logMeta, "auditLog"));
            assertTrue(meta.canView(user(level, "system:audit:view"), logMeta, "auditLog"));
            assertFalse(meta.canDelete(user(level, "system:audit:view"), logMeta, "auditLog"));
            assertTrue(meta.canDelete(user(level, "system:audit:delete"), logMeta, "auditLog"));
        }
    }

    @Test void schemaShowsIndependentDeleteButtonPermission() {
        var viewer = user(1, "system:audit:view");
        assertEquals(Map.of("view", true, "create", false, "edit", false, "delete", false),
                meta.schema(viewer, "auditLog").get("permissions"));
        var editor = user(1, "system:audit:view", "system:audit:delete");
        assertEquals(Map.of("view", true, "create", false, "edit", false, "delete", true),
                meta.schema(editor, "auditLog").get("permissions"));
    }

    @Test void readAndDeleteAreEnforcedByCrud() throws Exception {
        AuditLog log = log();
        assertThrows(ApiException.class, () -> crud.get(user(3), "auditLog", log.id));
        var viewer = user(1, "system:audit:view");
        assertEquals(log.id, crud.get(viewer, "auditLog", log.id).get("id"));
        assertThrows(ApiException.class, () -> crud.delete(viewer, "auditLog", log.id));
        assertTrue(sql.findById(AuditLog.class, log.id).isPresent());
        crud.delete(user(1, "system:audit:delete"), "auditLog", log.id);
        assertTrue(sql.findById(AuditLog.class, log.id).isEmpty());
    }

    @Test void genericModelGrantsCannotBypassLogPermissionKeys() {
        var user = user(3, "model:auditLog:view", "model:auditLog:delete");
        assertFalse(meta.canView(user, logMeta, "auditLog"));
        assertFalse(meta.canDelete(user, logMeta, "auditLog"));
        assertTrue(meta.canView(user(4), logMeta, "auditLog"));
        assertTrue(meta.canDelete(user(4), logMeta, "auditLog"));
    }
}
