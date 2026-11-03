package com.rabbitc.ims.service;

import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ims.security.LoginUser;

import java.util.Map;

/**
 * 模型扩展点：在通用 CRUD 的固定流程上挂模型专属规则。
 * <p>
 * 通用流程负责权限、数据范围、审计这些所有模型都一样的事；钩子只处理差异：
 * 用户保存时要哈希密码、同步角色，删除时要拦住「删自己」和「删掉最后一个超管」。
 * 新增模型不需要动通用代码，实现本接口即可。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public interface ModelHook {

    /**
     * 是否处理该模型。
     *
     * @param modelClass 模型类
     * @return 处理返回 {@code true}
     */
    boolean supports(Class<?> modelClass);

    /**
     * 新增前。
     *
     * @param meta   元数据
     * @param entity 待写入实体
     * @param body   请求体
     * @param user   操作人
     */
    default void beforeCreate(ModelMeta meta, Object entity, Map<String, Object> body, LoginUser user) {
    }

    /**
     * 新增后（主键已回填）。
     *
     * @param meta   元数据
     * @param entity 已写入实体
     * @param body   请求体
     * @param user   操作人
     */
    default void afterCreate(ModelMeta meta, Object entity, Map<String, Object> body, LoginUser user) {
    }

    /**
     * 修改前。
     *
     * @param meta   元数据
     * @param entity 待写入实体
     * @param before 修改前快照
     * @param body   请求体
     * @param user   操作人
     */
    default void beforeUpdate(ModelMeta meta, Object entity, Object before,
                              Map<String, Object> body, LoginUser user) {
    }

    /**
     * 修改后。
     *
     * @param meta   元数据
     * @param entity 已写入实体
     * @param before 修改前快照
     * @param body   请求体
     * @param user   操作人
     */
    default void afterUpdate(ModelMeta meta, Object entity, Object before,
                             Map<String, Object> body, LoginUser user) {
    }

    /**
     * 删除前。
     *
     * @param meta   元数据
     * @param entity 待删除实体
     * @param user   操作人
     */
    default void beforeDelete(ModelMeta meta, Object entity, LoginUser user) {
    }
}
