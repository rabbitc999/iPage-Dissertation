package com.rabbitc.ims.web;

import com.rabbitc.ipage.exception.CheckerException;
import com.rabbitc.ipage.jdbc.IpagDataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一错误体：{@code {"message": "..."}}。
 * <p>
 * ipage 的校验异常（{@link CheckerException}）来自模型注解与 {@code checker} 方法，
 * 直接作为 400 返回给表单；数据库异常只回一句通用提示，细节留在服务端日志里。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常。
     *
     * @param e 异常
     * @return 响应
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return body(e.status(), e.getMessage());
    }

    /**
     * 模型校验失败。
     *
     * @param e 异常
     * @return 400
     */
    @ExceptionHandler(CheckerException.class)
    public ResponseEntity<Map<String, Object>> handleChecker(CheckerException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 请求体解析失败（非法 JSON、编码不对、类型不匹配）。
     *
     * @param e 异常
     * @return 400
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        LOG.debug("[ims] 请求体解析失败：{}", e.getMessage());
        return body(HttpStatus.BAD_REQUEST, "请求体不是合法的 JSON 或字符编码不是 UTF-8");
    }

    /**
     * 参数错误（字段类型转换、非法枚举名等）。
     *
     * @param e 异常
     * @return 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegal(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage() == null ? "参数不合法" : e.getMessage());
    }

    /**
     * 数据访问失败。
     *
     * @param e 异常
     * @return 500
     */
    @ExceptionHandler(IpagDataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleSql(IpagDataAccessException e) {
        LOG.error("[ims] 数据库操作失败：{}", e.getMessage(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "数据库操作失败，请查看服务端日志");
    }

    /**
     * 静态资源不存在（浏览器自动请求 favicon、旧缓存里的 js 等）。
     * <p>
     * 这类请求不是业务错误：返回 404 即可，不能落到兜底处理器被记成 500 ——
     * 否则每打开一个页面都会在日志里刷一条 ERROR，真正的故障反而被淹掉。
     *
     * @param e 异常
     * @return 404
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMissingResource(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        LOG.debug("[ims] 静态资源不存在：{}", e.getResourcePath());
        return body(HttpStatus.NOT_FOUND, "资源不存在");
    }

    /**
     * 兜底。
     *
     * @param e 异常
     * @return 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        LOG.error("[ims] 未处理的异常", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        body.put("status", status.value());
        return ResponseEntity.status(status).body(body);
    }
}
