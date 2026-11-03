package com.rabbitc.ims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 信息管理系统服务端入口。
 * <p>
 * 项目结构：
 * <ul>
 *     <li>{@code model}：用注解描述表的模型类，ipage 据此建表、生成 SQL；</li>
 *     <li>{@code meta}：模型注册表与前端渲染所需的 schema；</li>
 *     <li>{@code service}：通用 CRUD（含权限、数据范围、审计）；</li>
 *     <li>{@code security}：无状态令牌 + 基于等级/权限点的鉴权；</li>
 *     <li>{@code ai}：可选的 AI 会话，把 CRUD 暴露成受权限约束的工具。</li>
 * </ul>
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ImsApplication {

    /**
     * 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ImsApplication.class, args);
    }
}
