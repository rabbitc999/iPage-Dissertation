package com.rabbitc.ims.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端入口页。
 * <p>
 * 为什么不让静态资源处理器直接吐 {@code index.html}：前端产物是
 * {@code assets/index-<哈希>.js}，发布新版本后文件名会变，而 {@code index.html} 名字不变。
 * 一旦入口 HTML 按 {@code spring.web.resources.cache.period}（默认 1 小时）被浏览器缓存，
 * 用户就会继续加载**上一版、甚至已经从包里删掉的 chunk**，
 * 表现出来就是「代码改了但页面还是旧的」，或者点到上一版本才有、现在已删除的入口。
 * <p>
 * 所以入口页单独用控制器返回并强制 {@code no-cache}：浏览器每次都回源校验，
 * 而带哈希的 {@code assets/**} 仍然享受长缓存。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Controller
public class SpaEntryController {

    /** 打包进 classpath 的入口页。 */
    private static final Resource INDEX = new ClassPathResource("static/index.html");

    /**
     * 返回入口页，并禁止浏览器缓存。
     *
     * @return 入口 HTML
     */
    @GetMapping({"/", "/index.html"})
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .contentType(MediaType.TEXT_HTML)
                .body(INDEX);
    }
}
