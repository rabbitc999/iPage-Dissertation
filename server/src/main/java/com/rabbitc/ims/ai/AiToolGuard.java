package com.rabbitc.ims.ai;

import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.DataScope;
import com.rabbitc.ims.enums.ToolAccess;
import com.rabbitc.ims.security.AuthService;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.web.ApiException;
import org.springframework.stereotype.Service;

/** 工具执行前重新鉴权，防止旧会话、权限撤销和外部 MCP 进程绕过员工权限。 */
@Service
public class AiToolGuard {
    private final AuthService auth;
    private final AiConfiguration configuration;

    public AiToolGuard(AuthService auth, AiConfiguration configuration) {
        this.auth = auth;
        this.configuration = configuration;
    }

    /** 校验会话归属和令牌版本，返回实时权限快照供业务服务执行。 */
    public LoginUser current(String ownerId) {
        LoginUser request = UserContext.get();
        if (request == null || !ownerId.equals(request.id())) {
            throw ApiException.forbidden("当前员工身份与助手会话不一致");
        }
        LoginUser current = auth.load(ownerId);
        if (current.tokenVersion() != request.tokenVersion()) {
            throw ApiException.forbidden("登录状态已失效，请重新登录");
        }
        return current;
    }

    /** AI 档位只是操作上限，不能替代员工身份、模型授权和业务权限。 */
    public void require(String ownerId, String modelId, ToolAccess access, ToolAccess required, boolean external) {
        LoginUser current = current(ownerId);
        configuration.requireMcpAccess(current, modelId, access, external ? ToolAccess.FULL : required);
        if (external && !canUseExternalMcp(current)) {
            throw ApiException.forbidden("外部 MCP 无法继承员工权限，仅允许全部数据范围的超级管理员使用");
        }
    }

    /** 未映射业务权限的外部进程不得向普通员工或受限管理员开放。 */
    public static boolean canUseExternalMcp(LoginUser user) {
        return user != null && user.reaches(ModelPermission.SUPER_ADMIN) && user.dataScope() == DataScope.ALL;
    }
}
