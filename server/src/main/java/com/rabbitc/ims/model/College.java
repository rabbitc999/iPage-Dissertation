package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * 院系（学院）。
 * <p>
 * 迁移自 {@code dissertation.department}。原表叫 department，与本项目已有的
 * {@link Department}（行政/业务部门，表 {@code ims_department}）同名但语义不同：
 * 这里表示教学组织上的「学院」，是专业的上游，故独立成 {@code th_college}，
 * 不与被员工、资产复用的部门表混用。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "院系", table = "th_college", icon = "fa-university",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${name}")
public class College {

    /** 主键。 */
    @ModelField(column = "college_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 院系名称，全局唯一。 */
    @ModelField(column = "name", text = "院系名称", order = 2)
    @ValidatorNotNull(message = "院系名称不能为空")
    @ValidatorLength(max = 50, message = "院系名称最长 50 个字符")
    public String name;

    /** 备注。 */
    @ModelField(column = "remark", text = "备注", order = 3)
    @ValidatorLength(max = 200, message = "备注最长 200 个字符")
    public String remark;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 20, look = false)
    public LocalDateTime createdAt;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 21, look = false)
    public LocalDateTime updatedAt;

    @Override
    public String toString() {
        return name == null ? "院系" : name;
    }
}
