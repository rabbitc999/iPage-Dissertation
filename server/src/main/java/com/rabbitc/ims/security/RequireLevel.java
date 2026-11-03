package com.rabbitc.ims.security;

import com.rabbitc.ipage.meta.ModelPermission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求当前用户的权限等级不低于指定值。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireLevel {

    /**
     * 最低等级。
     *
     * @return 等级
     */
    ModelPermission value();
}
