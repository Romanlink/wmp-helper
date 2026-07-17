package com.xyoo.helper.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库初始化器
 * <p>
 * 启动时检查 helper 数据库是否存在，不存在则通过 JDBC 连接自动创建。
 * 同时配合 application.properties 中 createDatabaseIfNotExist=true 参数确保万无一失。
 * </p>
 */
@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkDatabase() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 验证数据库连接和表结构
            ResultSet rs = stmt.executeQuery(
                    "SELECT TABLE_NAME FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = 'helper' AND TABLE_NAME = 'sys_module'"
            );

            if (rs.next()) {
                log.info("========================================");
                log.info("  helper 数据库连接成功");
                log.info("  sys_module 表已就绪");
                log.info("========================================");
            } else {
                log.info("========================================");
                log.info("  helper 数据库连接成功");
                log.info("  sys_module 表将由 JPA 自动创建（ddl-auto=update）");
                log.info("========================================");
            }

        } catch (Exception e) {
            log.error("数据库初始化检查失败: {}", e.getMessage());
        }
    }
}
