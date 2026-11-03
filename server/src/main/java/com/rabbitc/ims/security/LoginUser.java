package com.rabbitc.ims.security;

import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.DataScope;

import java.util.Set;

/**
 * 当前登录用户的权限快照（每次请求从库里重建）。
 * <p>
 * 主键与部门外键都是 UUID 字符串；{@code level} 取所有角色等级的最大值，
 * {@code dataScope} 取所有角色里最宽的那个——即「一个人挂多个角色时取并集」。
 *
 * @param id          用户主键（UUID）
 * @param username    登录账号
 * @param name        姓名
 * @param avatar      头像地址（可为 {@code null}）
 * @param level       权限等级，取所有角色等级的最大值
 * @param permissions 权限点编码集合
 * @param dataScope   数据范围，取所有角色中最宽的那个
 * @param deptId      所属部门（UUID）
 * @param tokenVersion 令牌版本
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public record LoginUser(String id, String username, String name, String avatar, int level,
                        Set<String> permissions, DataScope dataScope, String deptId,
                        int tokenVersion) {

    /**
     * 是否拥有某个权限点；超级管理员默认拥有一切。
     *
     * @param code 权限编码
     * @return 拥有返回 {@code true}
     */
    public boolean has(String code) {
        return level >= ModelPermission.SUPER_ADMIN.level() || permissions.contains(code);
    }

    /**
     * 是否达到某个模型权限等级。
     *
     * @param required 需要的等级
     * @return 满足返回 {@code true}
     */
    public boolean reaches(ModelPermission required) {
        return required.level() != Integer.MAX_VALUE && level >= required.level();
    }

    /**
     * 是否管理员（等级 ≥ 管理员）。
     *
     * @return 是返回 {@code true}
     */
    public boolean isAdmin() {
        return level >= ModelPermission.ADMIN.level();
    }
}
