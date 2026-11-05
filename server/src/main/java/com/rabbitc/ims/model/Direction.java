package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;

import java.time.LocalDateTime;

/**
 * 研究方向。
 * <p>
 * 迁移自 {@code dissertation.direction}。原表 73 行里混进了 9 行「专业名 / 说明性文字」
 * （如「可带专业（去重后）」「计算机科学与技术」），且「网络工程」重复两条，
 * 因为当时没有任何唯一约束。这里加唯一索引，脏数据在导入阶段就清掉。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ModelClass(name = "研究方向", table = "th_direction", icon = "fa-compass",
        look = ModelPermission.LEAST, edit = ModelPermission.NORMAL, delete = ModelPermission.ADMIN,
        text = "${name}")
public class Direction {

    /** 主键。 */
    @ModelField(column = "direction_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    /** 方向名称，全局唯一。 */
    @ModelField(column = "name", text = "方向名称", order = 2)
    @ValidatorNotNull(message = "方向名称不能为空")
    @ValidatorLength(max = 50, message = "方向名称最长 50 个字符")
    public String name;

    /** 创建时间。 */
    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 20, look = false)
    public LocalDateTime createdAt;

    /** 更新时间。 */
    @ModelField(column = "updated_at", type = FieldType.DATE, text = "更新时间", order = 21, look = false)
    public LocalDateTime updatedAt;

    @Override
    public String toString() {
        return name == null ? "研究方向" : name;
    }
}
