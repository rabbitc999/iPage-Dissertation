package com.rabbitc.ims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自身的配置项。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ConfigurationProperties(prefix = "ims")
public class ImsProperties {

    /** 令牌签名密钥；生产环境务必用环境变量覆盖。 */
    private String secret = "ipage-ims-dev-secret-please-change-in-production";

    /** 令牌有效期（小时）。 */
    private int tokenTtlHours = 12;

    /** 启动时是否写入演示数据（库里已有用户则跳过）。 */
    private boolean seed = true;

    /** 是否在启动时按模型自动建表 / 补列。 */
    private boolean syncSchema = true;

    /**
     * 签名密钥。
     *
     * @return 密钥
     */
    public String getSecret() {
        return secret;
    }

    /**
     * 设置签名密钥。
     *
     * @param secret 密钥
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * 令牌有效期。
     *
     * @return 小时数
     */
    public int getTokenTtlHours() {
        return tokenTtlHours;
    }

    /**
     * 设置令牌有效期。
     *
     * @param tokenTtlHours 小时数
     */
    public void setTokenTtlHours(int tokenTtlHours) {
        this.tokenTtlHours = tokenTtlHours;
    }

    /**
     * 是否写入演示数据。
     *
     * @return 需要返回 {@code true}
     */
    public boolean isSeed() {
        return seed;
    }

    /**
     * 设置是否写入演示数据。
     *
     * @param seed 是否需要
     */
    public void setSeed(boolean seed) {
        this.seed = seed;
    }

    /**
     * 是否同步表结构。
     *
     * @return 需要返回 {@code true}
     */
    public boolean isSyncSchema() {
        return syncSchema;
    }

    /**
     * 设置是否同步表结构。
     *
     * @param syncSchema 是否需要
     */
    public void setSyncSchema(boolean syncSchema) {
        this.syncSchema = syncSchema;
    }
}
