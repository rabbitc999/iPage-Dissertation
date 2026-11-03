package com.rabbitc.ims.security;

import com.rabbitc.ipage.meta.ModelPermission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求当前用户拥有其中任意一个权限点。
 * <p>
 * 超级管理员不需要显式配置权限点即可通过。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission {

    /**
     * 权限点编码。
     *
     * @return 编码列表，满足任意一个即可
     */
    String[] value();
}
