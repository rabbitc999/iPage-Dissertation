package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * 专业。
 * <p>
 * 迁移自 {@code dissertation.profession}。原表只有 (profession_id, department_id,
 * profession_name) 三列且没有「院系内专业名唯一」的约束；这里补上唯一索引（见
 * {@code ThesisConstraints}）。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "专业", table = "th_profession", icon = "fa-graduation-cap",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${name}")
public class Profession {

    /** 主键。 */
    @ModelField(column = "profession_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 所属院系外键。 */
    @ModelField(column = "college_id", type = FieldType.ASSOCIATION_ID, byModel = College.class,
            text = "所属院系", order = 2)
    public String collegeId;

    /** 所属院系对象。 */
    @ModelField(column = "college_id", type = FieldType.ASSOCIATION_MODEL, byModel = College.class,
            text = "所属院系", order = 3)
    public College college;

    /** 专业名称。 */
    @ModelField(column = "name", text = "专业名称", order = 4)
    @ValidatorNotNull(message = "专业名称不能为空")
    @ValidatorLength(max = 50, message = "专业名称最长 50 个字符")
    public String name;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 20, look = false)
    public LocalDateTime createdAt;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 21, look = false)
    public LocalDateTime updatedAt;

    @Override
    public String toString() {
        return name == null ? "专业" : name;
    }
}
