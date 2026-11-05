package com.rabbitc.ims.ai;

import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.enums.ToolAccess;
import com.rabbitc.ims.security.AuthService;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.web.ApiException;
import io.embedai.EmbedAiChat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 回归工具执行边界：会话归属、实时撤权、数据范围及外部工具伪装均不可绕过。 */
class AiToolGuardTest {
    private final AuthService auth = mock(AuthService.class);
    private final AiConfiguration config = mock(AiConfiguration.class);
    private final AiToolGuard guard = new AiToolGuard(auth, config);

    private LoginUser user(String id, int level, DataScope scope, int version) {
        return new LoginUser(id, id, id, null, level, Set.of("ai:use"), scope, "dept", version);
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test void missingOrForeignIdentityIsDenied() {
        assertThrows(ApiException.class, () -> guard.current("owner"));
        UserContext.set(user("other", 4, DataScope.ALL, 0));
        assertThrows(ApiException.class, () -> guard.current("owner"));
        verifyNoInteractions(auth);
    }

    @Test void tokenRevocationIsDenied() {
        UserContext.set(user("owner", 4, DataScope.ALL, 0));
        when(auth.load("owner")).thenReturn(user("owner", 4, DataScope.ALL, 1));
        assertThrows(ApiException.class, () -> guard.current("owner"));
    }

    @Test void disabledAccountIsDenied() {
        UserContext.set(user("owner", 4, DataScope.ALL, 0));
        when(auth.load("owner")).thenThrow(ApiException.forbidden("账号已停用"));
        assertThrows(ApiException.class, () -> guard.current("owner"));
    }

    @Test void everyCallReloadsEmployeePermissions() {
        LoginUser old = user("owner", 4, DataScope.ALL, 0);
        LoginUser reduced = user("owner", 1, DataScope.SELF, 0);
        UserContext.set(old);
        when(auth.load("owner")).thenReturn(old, reduced);
        assertEquals(old, guard.current("owner"));
        assertEquals(reduced, guard.current("owner"));
        verify(auth, times(2)).load("owner");
    }

    @Test void externalMcpRequiresUnrestrictedSuperAdmin() {
        assertFalse(AiToolGuard.canUseExternalMcp(user("staff", 1, DataScope.SELF, 0)));
        assertFalse(AiToolGuard.canUseExternalMcp(user("manager", 2, DataScope.DEPT, 0)));
        assertFalse(AiToolGuard.canUseExternalMcp(user("admin", 3, DataScope.ALL, 0)));
        assertFalse(AiToolGuard.canUseExternalMcp(user("super", 4, DataScope.DEPT, 0)));
        assertFalse(AiToolGuard.canUseExternalMcp(user("super", 4, DataScope.SELF, 0)));
        assertFalse(AiToolGuard.canUseExternalMcp(user("super", 4, null, 0)));
        assertTrue(AiToolGuard.canUseExternalMcp(user("super", 4, DataScope.ALL, 0)));
    }

    @Test void externalToolAlwaysRequiresFullEvenWithReadToolName() {
        LoginUser current = user("owner", 4, DataScope.ALL, 0);
        UserContext.set(current);
        when(auth.load("owner")).thenReturn(current);
        guard.require("owner", "model", ToolAccess.FULL, ToolAccess.READ_ONLY, true);
        verify(config).requireMcpAccess(current, "model", ToolAccess.FULL, ToolAccess.FULL);
    }

    @Test void modelRevocationIsDeniedForInternalToolsToo() {
        LoginUser current = user("owner", 1, DataScope.SELF, 0);
        UserContext.set(current);
        when(auth.load("owner")).thenReturn(current);
        doThrow(ApiException.forbidden("模型授权已撤销")).when(config)
                .requireMcpAccess(current, "model", ToolAccess.READ_ONLY, ToolAccess.READ_ONLY);
        assertThrows(ApiException.class, () -> guard.require("owner", "model",
                ToolAccess.READ_ONLY, ToolAccess.READ_ONLY, false));
    }

    @Test void demotionStopsRegisteredExternalToolBeforeHandlerRuns() throws Exception {
        LoginUser old = user("owner", 4, DataScope.ALL, 0);
        UserContext.set(old);
        when(auth.load("owner")).thenReturn(old, user("owner", 1, DataScope.SELF, 0));
        EmbedAiChat.Engine engine = new EmbedAiChat.Engine();
        AtomicBoolean executed = new AtomicBoolean();
        engine.registerTool(new EmbedAiChat.Tool("external_read", "test",
                EmbedAiChat.object("type", "object"), args -> {
                    executed.set(true);
                    return EmbedAiChat.object("ok", true);
                }));
        engine.toolDenial = name -> {
            try {
                guard.require("owner", "model", ToolAccess.FULL, ToolAccess.READ_ONLY, true);
                return null;
            } catch (ApiException denied) { return denied.getMessage(); }
        };
        engine.allowTools = true;
        engine.invoke("external_read", EmbedAiChat.object());
        executed.set(false);
        assertThrows(EmbedAiChat.ToolPermissionDenied.class,
                () -> engine.invoke("external_read", EmbedAiChat.object()));
        assertFalse(executed.get());
    }
}
