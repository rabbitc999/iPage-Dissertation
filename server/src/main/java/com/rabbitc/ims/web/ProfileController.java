package com.rabbitc.ims.web;

import com.rabbitc.ipage.exception.CheckerException;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import com.rabbitc.ims.model.SysFile;
import com.rabbitc.ims.model.User;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.security.UserContext;
import com.rabbitc.ims.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 个人中心：头像上传与清除。
 * <p>
 * 头像走「文件表 + 引用」的组合：先落一条 {@link SysFile}（IMAGE byte[] 形态进 BLOB），
 * 再把 {@code /api/files/{id}/raw} 这个相对链接写进 {@code sys_user.avatar}。
 * 这样行数据里只有短字符串，列表查询不会拖出大字段。
 * <p>
 * 只允许登录用户改<b>自己的</b>头像；管理员替他人改头像走通用用户编辑接口。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileController.class);

    /** 头像比一般文件更小：5MB 足够任何合理图片。 */
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;

    private final ModelSession sql;
    private final AuditService audit;

    /**
     * 构造控制器。
     *
     * @param sql   SQL 会话
     * @param audit 审计服务
     */
    public ProfileController(ModelSession sql, AuditService audit) {
        this.sql = sql;
        this.audit = audit;
    }

    /**
     * 上传并设置当前用户的头像。multipart 字段名固定为 {@code file}。
     *
     * @param file 图片文件
     * @return 新的头像地址
     */
    @PostMapping("/avatar")
    public Map<String, Object> uploadAvatar(@RequestParam("file") MultipartFile file) {
        LoginUser user = UserContext.require();
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择头像图片");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw ApiException.badRequest("头像不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("头像必须是图片文件");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (Exception e) {
            LOG.warn("读取头像内容失败", e);
            throw ApiException.badRequest("读取上传内容失败");
        }
        return saveAvatar(user, file.getOriginalFilename(), contentType, data);
    }

    /** 已确认的头像更新复用个人中心的发布与审计逻辑。 */
    public Map<String, Object> saveAvatar(LoginUser user, String name, String contentType, byte[] data) {
        SysFile record = new SysFile();
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

        String url = "/api/files/" + record.id + "/raw";
        updateAvatar(user.id(), url);
        audit.record(user, "上传", "头像", user.id(), record.name);
        return Map.of("avatarUrl", url);
    }

    /**
     * 清除当前用户的头像。
     *
     * @return 空头像地址
     */
    @DeleteMapping("/avatar")
    public Map<String, Object> clearAvatar() {
        LoginUser user = UserContext.require();
        updateAvatar(user.id(), null);
        audit.record(user, "删除", "头像", user.id(), null);
        return Map.of("avatarUrl", "");
    }

    /**
     * 把新的头像地址写回用户行。
     */
    private void updateAvatar(String userId, String url) {
        Optional<User> found = sql.selectOne(Query.of(User.class).where("id", userId));
        User target = found.orElseThrow(() -> ApiException.notFound("用户不存在"));
        target.avatar = url;
        try {
            sql.update(target);
        } catch (CheckerException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    /**
     * 清洗文件名（与 FileController 相同的规则）。
     */
    private static String sanitizeName(String original) {
        if (original == null || original.isBlank()) {
            return "头像";
        }
        String name = original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\\t]", " ").trim();
        return name.isEmpty() ? "头像" : name;
    }
}
