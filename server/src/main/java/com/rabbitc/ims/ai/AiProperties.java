package com.rabbitc.ims.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块配置。
 * <p>
 * 密钥只从服务端配置读取，绝不通过接口下发给浏览器；前端只能看到「是否启用」和模型名。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@ConfigurationProperties(prefix = "ims.ai")
public class AiProperties {

    /** 是否启用 AI 会话。 */
    private boolean enabled = false;

    /** 协议：openai（兼容 DeepSeek / Kimi）或 anthropic。 */
    private String provider = "openai";

    /** 接口基础地址。 */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 模型 ID。 */
    private String model = "deepseek-chat";

    /** API Key，仅存在服务端内存。 */
    private String apiKey = "";

    /** 单次回答的输出上限。 */
    private int maxTokens = 2048;

    /** 输入上下文预算。 */
    private int contextBudget = 16000;

    /** 是否允许模型调用工具（还会与每次请求的开关做与运算）。 */
    private boolean allowTools = true;

    /** 运行目录：内置 Skill 会被复制到这里。 */
    private String workDir = "./data/ai";

    /**
     * 是否启用。
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 协议名。
     *
     * @return 协议
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 设置协议名。
     *
     * @param provider 协议
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 基础地址。
     *
     * @return 地址
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置基础地址。
     *
     * @param baseUrl 地址
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 模型 ID。
     *
     * @return 模型
     */
    public String getModel() {
        return model;
    }

    /**
     * 设置模型 ID。
     *
     * @param model 模型
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * API Key。
     *
     * @return 密钥
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置 API Key。
     *
     * @param apiKey 密钥
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 输出上限。
     *
     * @return token 数
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * 设置输出上限。
     *
     * @param maxTokens token 数
     */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * 上下文预算。
     *
     * @return token 估算值
     */
    public int getContextBudget() {
        return contextBudget;
    }

    /**
     * 设置上下文预算。
     *
     * @param contextBudget token 估算值
     */
    public void setContextBudget(int contextBudget) {
        this.contextBudget = contextBudget;
    }

    /**
     * 是否允许工具调用。
     *
     * @return 允许返回 {@code true}
     */
    public boolean isAllowTools() {
        return allowTools;
    }

    /**
     * 设置是否允许工具调用。
     *
     * @param allowTools 是否允许
     */
    public void setAllowTools(boolean allowTools) {
        this.allowTools = allowTools;
    }

    /**
     * 运行目录。
     *
     * @return 目录
     */
    public String getWorkDir() {
        return workDir;
    }

    /**
     * 设置运行目录。
     *
     * @param workDir 目录
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }
}
