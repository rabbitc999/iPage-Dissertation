package com.rabbitc.ims.config;

import com.rabbitc.ipage.jdbc.ConnectionProvider;
import com.rabbitc.ipage.jdbc.ModelSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * ipage 会话装配。
 * <p>
 * 会话本身不持有连接，每次操作按需从数据源借还，因此可以做成单例被所有请求共享。
 * 方言由 {@link ModelSession#sql(ConnectionProvider)} 在启动时探测一次并固化。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Configuration
public class IpageConfig {

    /**
     * 构建全局 SQL 会话。
     *
     * @param dataSource Spring 管理的数据源
     * @return 会话
     */
    @Bean
    public ModelSession modelSession(DataSource dataSource) {
        return ModelSession.sql(ConnectionProvider.of(dataSource));
    }
}
