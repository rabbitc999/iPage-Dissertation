package com.rabbitc.ims.web;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：带 HTTP 状态码，由全局异常处理器转成统一错误体。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    /**
     * 构造异常。
     *
     * @param status  HTTP 状态
     * @param message 给前端看的中文说明
     */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * HTTP 状态。
     *
     * @return 状态
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 400：参数或业务规则不满足。
     *
     * @param message 说明
     * @return 异常
     */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 401：未登录或令牌失效。
     *
     * @param message 说明
     * @return 异常
     */
    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    /**
     * 403：已登录但权限不足。
     *
     * @param message 说明
     * @return 异常
     */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    /**
     * 404：资源不存在。
     *
     * @param message 说明
     * @return 异常
     */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    /**
     * 501：功能在当前部署中不可用（例如未启用 AI）。
     *
     * @param message 说明
     * @return 异常
     */
    public static ApiException notImplemented(String message) {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, message);
    }
}
