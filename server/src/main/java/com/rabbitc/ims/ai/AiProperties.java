package com.rabbitc.ims.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 本机运行参数。供应商、密钥与模块开关由数据库管理。 */
@ConfigurationProperties(prefix = "ims.ai")
public class AiProperties {
    private boolean available = true;
    private int maxTokens = 2048;
    private int contextBudget = 16000;
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
