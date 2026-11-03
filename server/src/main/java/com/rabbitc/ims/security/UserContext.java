package com.rabbitc.ims.security;

/**
 * 请求级用户上下文。
 * <p>
 * 拦截器在 {@code preHandle} 写入、{@code afterCompletion} 清理；线程池复用线程时不会串号。
 * 业务层通过 {@link #require()} 取用户，拿不到就说明这个入口没有经过鉴权拦截器。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 写入当前用户。
     *
     * @param user 用户
     */
    public static void set(LoginUser user) {
        CURRENT.set(user);
    }

    /**
     * 当前用户，未登录时为 {@code null}。
     *
     * @return 用户
     */
    public static LoginUser get() {
        return CURRENT.get();
    }

    /**
     * 当前用户，未登录时抛异常。
     *
     * @return 用户
     */
    public static LoginUser require() {
        LoginUser user = CURRENT.get();
        if (user == null) {
            throw new IllegalStateException("当前请求没有登录用户，请检查接口是否被鉴权拦截器覆盖");
        }
        return user;
    }

    /**
     * 清理，防止线程复用时残留。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
