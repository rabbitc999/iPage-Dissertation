package com.rabbitc.ims.seed;

import com.rabbitc.ims.config.ImsProperties;
import com.rabbitc.ims.model.College;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.sql.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 论文域演示数据装载。
 * <p>
 * 只在同时满足三个条件时执行：
 * <ol>
 *     <li>配置打开了 {@code ims.seed-thesis}（默认关闭，H2 演练 profile 打开）；</li>
 *     <li>表结构已经由 iPage 建好；</li>
 *     <li>院系表为空——已有数据（例如从原库迁移过来的正式数据）一律不碰。</li>
 * </ol>
 * 这样「空库查询恒为空」不会再发生，而正式库也不会被内置数据污染。
 *
 * @author 小兔糖糖OvO
 * @since 1.0
 */
@Component
@Order(20)
public class ThesisDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ThesisDataSeeder.class);

    /** 内置数据脚本（与 dissertation-refactor/seed-postgres.sql 同源）。 */
    private static final String SCRIPT = "seed/thesis-data.sql";

    private final DataSource dataSource;
    private final ModelSession sql;
    private final ImsProperties properties;

    /**
     * 构造装载器。
     *
     * @param dataSource 数据源
     * @param sql        SQL 会话
     * @param properties 应用配置
     */
    public ThesisDataSeeder(DataSource dataSource, ModelSession sql, ImsProperties properties) {
        this.dataSource = dataSource;
        this.sql = sql;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSeedThesis()) {
            return;
        }
        long colleges = sql.count(Query.of(College.class));
        if (colleges > 0) {
            LOG.info("[ims] 论文域已有数据（院系 {} 条），跳过内置数据导入", colleges);
            return;
        }
        LOG.info("[ims] 论文域为空，开始导入内置演示数据…");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SCRIPT));
        populator.setSqlScriptEncoding("UTF-8");
        try {
            DatabasePopulatorUtils.execute(populator, dataSource);
        } catch (RuntimeException e) {
            throw new IllegalStateException("导入内置论文数据失败，请检查 " + SCRIPT, e);
        }
        LOG.info("[ims] 内置论文数据导入完成：院系 {} 条，学生 {} 条，论文 {} 条",
                sql.count(Query.of(College.class)),
                sql.count(Query.of(com.rabbitc.ims.model.Student.class)),
                sql.count(Query.of(com.rabbitc.ims.model.Paper.class)));
    }
}
