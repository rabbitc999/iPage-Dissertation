package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.TeacherEducation;
import com.rabbitc.ims.enums.TeacherPost;

import java.time.LocalDateTime;

/**
 * 教师。
 * <p>
 * 迁移自 {@code dissertation.teacher}。原表把表名前缀写进了每个列名
 * （{@code teacher_name} / {@code teacher_post} …），这里统一去掉前缀；
 * 职称与学历从自由文本改为枚举（原库实测职称有 10 种写法、学历有 5 种）。
 * <p>
 * 新增 {@code teacherNo}（工号）：原表没有任何稳定的业务标识，同名教师无法区分。
 * {@code qq / wechat / mobile} 属个人敏感信息，只对管理员可见（字段级 {@code look}）。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "教师", table = "th_teacher", icon = "fa-chalkboard-teacher",
        look = ModelPermission.LEAST, edit = ModelPermission.TEACHER, delete = ModelPermission.ADMIN,
        text = "${name}")
public class Teacher {

    /** 主键。 */
    @ModelField(column = "teacher_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 工号，全局唯一（业务补录，可为空）。 */
    @ModelField(column = "teacher_no", text = "工号", order = 2)
    @ValidatorLength(max = 20, message = "工号最长 20 个字符")
    public String teacherNo;

    /** 姓名。 */
    @ModelField(column = "name", text = "姓名", order = 3)
    @ValidatorNotNull(message = "姓名不能为空")
    @ValidatorLength(max = 30, message = "姓名最长 30 个字符")
    public String name;

    /** 职称。 */
    @ModelField(column = "post", type = FieldType.ENUM, byEnum = TeacherPost.class,
            text = "职称", order = 4)
    public TeacherPost post = TeacherPost.NONE;

    /** 学历。 */
    @ModelField(column = "education", type = FieldType.ENUM, byEnum = TeacherEducation.class,
            text = "学历", order = 5)
    public TeacherEducation education = TeacherEducation.NONE;

    /** QQ 号（个人敏感信息）。 */
    @ModelField(column = "qq", text = "QQ号", order = 6,
            lookPermission = ModelPermission.ADMIN)
    @ValidatorLength(max = 20, message = "QQ号最长 20 个字符")
    public String qq;

    /** 微信号（个人敏感信息）。 */
    @ModelField(column = "wechat", text = "微信号", order = 7,
            lookPermission = ModelPermission.ADMIN)
    @ValidatorLength(max = 20, message = "微信号最长 20 个字符")
    public String wechat;

    /** 手机号（个人敏感信息）。 */
    @ModelField(column = "mobile", text = "手机号", order = 8,
            lookPermission = ModelPermission.ADMIN)
    @ValidatorLength(max = 20, message = "手机号最长 20 个字符")
    public String mobile;

    /** 备注。 */
    @ModelField(column = "remark", text = "备注", order = 9)
    @ValidatorLength(max = 255, message = "备注最长 255 个字符")
    public String remark;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 20, look = false)
    public LocalDateTime createdAt;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 21, look = false)
    public LocalDateTime updatedAt;

    @Override
    public String toString() {
        return name == null ? "教师" : name;
    }
}
