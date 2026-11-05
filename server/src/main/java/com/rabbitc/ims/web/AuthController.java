package com.rabbitc.ims.web;

import com.rabbitc.ims.security.AuthService;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录、当前用户与密码。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    /**
     * 构造控制器。
     *
     * @param auth 认证服务
     */
    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /**
     * 登录请求。
     *
     * @param username 账号
     * @param password 密码
     */
    public record LoginRequest(String username, String password) {
    }

    /**
     * 修改密码请求。
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    public record PasswordRequest(String oldPassword, String newPassword) {
    }

    /**
     * 登录。
     *
     * @param request 请求体
     * @return 令牌与用户信息
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        AuthService.AuthResult result = auth.login(request.username(), request.password());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", result.token());
        body.put("user", view(result.user()));
        return body;
    }

    /**
     * 当前登录用户。
     *
     * @return 用户信息
     */
    @GetMapping("/me")
    public Map<String, Object> me() {
        return view(UserContext.require());
    }

    /**
     * 退出登录。
     *
     * @return 空响应
     */
    @PostMapping("/logout")
    public Map<String, Object> logout() {
        auth.logout(UserContext.require());
        return Map.of("ok", true);
    }

    /**
     * 修改自己的密码。
     *
     * @param request 请求体
     * @return 新令牌
     */
    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody PasswordRequest request) {
        String token = auth.changePassword(UserContext.require(), request.oldPassword(), request.newPassword());
        return Map.of("token", token);
    }

    /**
     * 用户信息视图：不包含密码哈希与令牌版本。
     *
     * @param user 用户
     * @return 视图
     */
    static Map<String, Object> view(LoginUser user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.id());
        view.put("username", user.username());
        view.put("name", user.name());
        view.put("avatar", user.avatar());
        view.put("level", user.level());
        view.put("dataScope", user.dataScope().name());
        view.put("isAdmin", user.isAdmin());
        List<String> permissions = user.permissions().stream().sorted().toList();
        view.put("permissions", permissions);
        return view;
    }
}
