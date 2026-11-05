package com.rabbitc.ims.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 本机运行参数。供应商、密钥与模块开关由数据库管理。 */
@ConfigurationProperties(prefix = "ims.ai")
public class AiProperties {
    private boolean available = true;
    private int maxTokens = 2048;
    /** 单次请求的上下文上限（tokens）；默认按 300K 窗口的大模型设置，模型更小请调低。 */
    private int contextBudget = 300000;
    private String workDir = "./data/ai";

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getContextBudget() {
        return contextBudget;
    }

    public void setContextBudget(int contextBudget) {
        this.contextBudget = contextBudget;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }
}
