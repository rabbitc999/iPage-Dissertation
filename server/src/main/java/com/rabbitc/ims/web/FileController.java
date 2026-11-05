package com.rabbitc.ims.web;

import com.rabbitc.ipage.exception.CheckerException;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.model.SysFile;
import com.rabbitc.ims.ai.AiConfiguration;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 文件上传 / 下载 / 删除接口。
 * <p>
 * 三个端点对应三种使用姿势：
 * <ul>
 *     <li>{@code POST /api/files}：multipart 上传，登录用户可用，返回 {@code url}
 *     字段（{@code /api/files/{id}/raw}），可直接填进 IMAGE 类型字段；</li>
 *     <li>{@code GET /api/files/{id}/raw}：内联读取，<b>不做登录校验</b>——
 *     {@code <img>} 标签带不上 Authorization 头，头像、品牌图必须裸地址可访问；
 *     文件主键是不可猜测的 UUID，内容不可变，风险可控；</li>
 *     <li>{@code GET /api/files/{id}/download}：强制附件下载，需要登录；</li>
 * </ul>
 * 删除仅限上传者本人或管理员。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger LOG = LoggerFactory.getLogger(FileController.class);

    /** 与 application.yml 的 multipart 上限保持一致，双保险。 */
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    /** 文件内容不可变（没有更新端点），允许浏览器缓存一天。 */
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final ModelSession sql;
    private final AuditService audit;
    private final AiConfiguration ai;

    /**
     * 构造控制器。
     *
     * @param sql   SQL 会话
     * @param audit 审计服务
     */
    public FileController(ModelSession sql, AuditService audit, AiConfiguration ai) {
        this.ai = ai;
        this.sql = sql;
        this.audit = audit;
    }

    /**
     * 上传文件。multipart 字段名固定为 {@code file}。
     *
     * @param file 上传的文件
     * @return 文件元数据与访问地址
     */
    @PostMapping
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "false") boolean chatPrivate) {
        return uploadFor(UserContext.require(), file, chatPrivate);
    }

    /** 业务附件事务复用已有上传校验与审计。 */
    public Map<String, Object> uploadFor(LoginUser user, MultipartFile file, boolean chatPrivate) {
        if (chatPrivate && !user.has("ai:use")) {
            throw ApiException.forbidden("没有 AI 使用权限");
        }
        if (chatPrivate) ai.requireEnabled();
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw ApiException.badRequest("文件超过 10MB 上限");
        }
        String name = sanitizeName(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (Exception e) {
            LOG.warn("读取上传内容失败", e);
            throw ApiException.badRequest("读取上传内容失败");
        }

        return store(user, name, contentType, data, chatPrivate);
    }

    /** 同一文件存储入口也用于助手生成的附件。 */
    public Map<String, Object> store(LoginUser user, String name, String contentType, byte[] data, boolean chatPrivate) {
        SysFile record = new SysFile();
        record.chatPrivate = chatPrivate;
        record.name = sanitizeName(name);
        record.contentType = contentType;
        record.size = (long) data.length;
        record.data = data;
        record.createBy = user.id();
        record.createdAt = LocalDateTime.now();
        try {
            sql.insert(record);
        } catch (CheckerException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        audit.record(user, "上传", "文件", record.id, name);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.id);
        result.put("name", record.name);
        result.put("contentType", record.contentType);
        result.put("size", record.size);
        result.put("url", "/api/files/" + record.id + (chatPrivate ? "/download" : "/raw"));
        return result;
    }

    /**
     * 内联读取文件内容：图片场景直接给 {@code <img>} 用。
     * 公开端点（见 {@code WebConfig} 的排除名单），只暴露内容本身。
     *
     * @param id 文件主键
     * @return 字节内容
     */
    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> raw(@PathVariable String id) {
        SysFile record = require(id);
        if (Boolean.TRUE.equals(record.chatPrivate)) {
            throw ApiException.notFound("文件不存在");
        }
        return bytes(record, false);
    }

    /**
     * 附件下载：带 {@code Content-Disposition: attachment}，需要登录。
     *
     * @param id 文件主键
     * @return 字节内容
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        SysFile record = require(id);
        if (Boolean.TRUE.equals(record.chatPrivate) && !UserContext.require().id().equals(record.createBy)) {
            throw ApiException.notFound("文件不存在");
        }
        return bytes(record, true);
    }

    /**
     * 删除文件，仅上传者本人或管理员可用。
     *
     * @param id 文件主键
     * @return 空对象
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        LoginUser user = UserContext.require();
        SysFile record = require(id);
        if (!user.isAdmin() && !user.id().equals(record.createBy)) {
            throw ApiException.forbidden("只能删除自己上传的文件");
        }
        sql.delete(record);
        audit.record(user, "删除", "文件", id, record.name);
        return Map.of("ok", true);
    }

    /**
     * 按主键取文件记录，不存在时抛 404。
     */
    /** 按当前用户校验对话附件的归属。 */
    public SysFile chatAttachment(LoginUser user, String id) {
        SysFile record = require(id);
        if (!Boolean.TRUE.equals(record.chatPrivate) || !user.id().equals(record.createBy)) {
            throw ApiException.notFound("附件不存在");
        }
        return record;
    }

    /** 仅供已通过所属业务记录授权的附件下载调用。 */
    public ResponseEntity<byte[]> downloadLinkedFile(String id) {
        return bytes(require(id), true);
    }

    private SysFile require(String id) {
        Optional<SysFile> found = sql.selectOne(Query.of(SysFile.class).where("id", id));
        SysFile record = found.orElse(null);
        if (record == null || record.data == null) {
            throw ApiException.notFound("文件不存在或已被删除");
        }
        return record;
    }

    /**
     * 组装字节响应：内容类型原样回写，缓存与 Content-Disposition 按场景区分。
     */
    private ResponseEntity<byte[]> bytes(SysFile record, boolean attachment) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(record.contentType));
        headers.setCacheControl(Boolean.TRUE.equals(record.chatPrivate)
                ? CacheControl.noStore() : CacheControl.maxAge(CACHE_TTL).cachePublic());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentDisposition(attachment
                ? ContentDisposition.attachment().filename(record.name, StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(record.name, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(record.data);
    }

    /**
     * 清洗原始文件名：去掉路径部分与非法字符，避免落库内容和响应头被注入。
     */
    private static String sanitizeName(String original) {
        if (original == null || original.isBlank()) {
            return "未命名文件";
        }
        String name = original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\\t]", " ").trim();
        if (name.isEmpty()) {
            return "未命名文件";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
