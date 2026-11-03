package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.annotation.ValidatorPattern;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.RecordStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户。
 * <p>
 * 两个字段刻意不加 {@link ModelField}：
 * <ul>
 *     <li>{@code password}：明文密码只在请求体内出现一次，由 {@code UserHook} 哈希后写入
 *     {@code passwordHash}，不参与 SQL 生成；</li>
 *     <li>{@code roleIds}：角色多选，由服务端同步到 {@code sys_user_role} 关联表。</li>
 * </ul>
 * 它们不在模型元数据里，因此不会被自动建表、也不会被通用 CRUD 直接写库；
 * 前端通过 {@code MetaService} 追加的虚拟字段拿到表单项。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "用户", table = "sys_user", icon = "fa-users",
        look = ModelPermission.ADMIN, edit = ModelPermission.ADMIN, delete = ModelPermission.SUPER_ADMIN,
        text = "${name}")
public class User {

    /** 主键。 */
    @ModelField(column = "user_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 登录账号。 */
    @ModelField(column = "user_name", text = "账号", order = 2)
    @ValidatorNotNull(message = "账号不能为空")
    @ValidatorPattern(regexp = "[A-Za-z0-9_]{3,32}", message = "账号必须是 3–32 位字母、数字或下划线")
    public String username;

    /** 密码哈希，永远不出现在接口响应里。 */
    @ModelField(column = "password_hash", text = "密码", order = 3, look = false,
            editPermission = ModelPermission.FORBIDDEN)
    public String passwordHash;

    /** 姓名。 */
    @ModelField(column = "display_name", text = "姓名", order = 4)
    @ValidatorNotNull(message = "姓名不能为空")
    @ValidatorLength(max = 32, message = "姓名最长 32 个字符")
    public String name;

    /** 部门外键。 */
    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_ID, byModel = Department.class,
            text = "部门", order = 5)
    public String deptId;

    /** 部门对象。 */
    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_MODEL, byModel = Department.class,
            text = "部门", order = 6)
    public Department dept;

    /** 手机号。 */
    @ModelField(column = "phone", text = "手机号", order = 7)
    public String phone;

    /** 邮箱。 */
    @ModelField(column = "email", text = "邮箱", order = 8)
    public String email;

    /** 账号状态。 */
    @ModelField(column = "user_status", type = FieldType.ENUM, byEnum = RecordStatus.class,
            text = "状态", order = 9)
    public RecordStatus status = RecordStatus.ENABLED;

    /** 令牌版本：改密码、重置密码、停用账号时自增，使旧令牌立即失效。 */
    @ModelField(column = "token_version", type = FieldType.NUMBER, text = "令牌版本", order = 10,
            look = false, editPermission = ModelPermission.FORBIDDEN)
    public Integer tokenVersion;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 11, look = false)
    public LocalDateTime createdAt;

    /** 明文密码，只在表单提交时出现，不落库。 */
    public String password;

    /** 角色主键集合，由服务端同步到关联表。 */
    public List<String> roleIds;

    @Override
    public String toString() {
        return name == null ? username : name;
    }
}
