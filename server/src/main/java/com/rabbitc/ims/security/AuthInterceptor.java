package com.rabbitc.ims.security;

import com.rabbitc.ims.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 鉴权拦截器：解析令牌 → 装配用户快照 → 校验注解。
 * <p>
 * 只在 {@code /api/**} 上生效，登录接口与健康检查除外。校验失败抛 {@link ApiException}，
 * 由全局异常处理器统一转成 JSON，不会返回登录页。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokens;
    private final AuthService auth;

    /**
     * 构造拦截器。
     *
     * @param tokens 令牌服务
     * @param auth   认证服务
     */
    public AuthInterceptor(TokenService tokens, AuthService auth) {
        this.tokens = tokens;
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = bearer(request);
        TokenService.Claim claim = tokens.verify(token)
                .orElseThrow(() -> ApiException.unauthorized("请先登录"));
        LoginUser user = auth.load(claim.userId());
        if (user.tokenVersion() != claim.version()) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录");
        }
        UserContext.set(user);
        checkAnnotations(handler, user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private static void checkAnnotations(Object handler, LoginUser user) {
        if (!(handler instanceof HandlerMethod method)) {
            return;
        }
        RequireLevel level = method.getMethodAnnotation(RequireLevel.class);
        if (level == null) {
            level = method.getBeanType().getAnnotation(RequireLevel.class);
        }
        if (level != null && !user.reaches(level.value())) {
            throw ApiException.forbidden("权限不足，需要 " + level.value() + " 及以上等级");
        }
        RequirePermission permission = method.getMethodAnnotation(RequirePermission.class);
        if (permission == null) {
            permission = method.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (permission != null) {
            boolean any = Arrays.stream(permission.value()).anyMatch(user::has);
            if (!any) {
                throw ApiException.forbidden("权限不足，缺少权限点：" + String.join(" / ", permission.value()));
            }
        }
    }
}
