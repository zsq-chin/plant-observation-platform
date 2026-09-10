package com.jingxuan;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class FlywayBaselineMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("jingxuan_flyway_test")
            .withUsername("jingxuan_flyway_test")
            .withPassword("jingxuan_flyway_test");

    @BeforeAll
    static void startMysql() {
        MYSQL.start();
    }

    @AfterAll
    static void stopMysql() {
        MYSQL.stop();
    }

    @Test
    void migratesAndValidatesBaselineAndPersistentEventSchemaOnEmptyMysqlDatabase() throws SQLException {
        assertEquals(0, countTables(), "Testcontainers 应提供没有业务表的全新数据库");

        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();

        flyway.migrate();
        flyway.validate();

        MigrationInfo currentMigration = flyway.info().current();
        assertNotNull(currentMigration, "迁移完成后应存在当前版本");
        assertNotNull(currentMigration.getVersion(), "基线迁移应具有版本号");
        assertEquals("11", currentMigration.getVersion().getVersion());
        assertTrue(tableExists("flyway_schema_history"), "Flyway 应记录迁移历史");
        assertTrue(tableExists("sys_user"), "基础身份表应由基线创建");
        assertTrue(tableExists("work"), "作品主表应由基线创建");
        assertTrue(tableExists("student_task"), "最后一批增量脚本应包含在基线中");
        assertTrue(columnExists("sys_notice", "target_scope"), "基线应执行最后一个公告范围增量脚本");
        assertTrue(tableExists("EVENT_PUBLICATION"), "V2 应创建 Spring Modulith 事务事件发布表");
        assertTrue(columnExists("EVENT_PUBLICATION", "STATUS"), "事务事件表应使用 Modulith 2.1 生命周期结构");
        assertTrue(columnExists("EVENT_PUBLICATION", "COMPLETION_ATTEMPTS"), "事务事件表应记录重试次数");
        assertTrue(columnExists("work_attachment", "sha256"), "附件元数据应保存内容 SHA-256 摘要");
        assertFalse(tableExists("work_runtime"), "仅含注释的历史占位脚本应被安全跳过");
        assertEquals(0, countUsers(), "生产 Flyway 基线不得创建默认登录账号");
        assertEquals(3, countClassDictSeeds(), "V6 应为注册阶段写入 1班/2班/3班 班级字典");
        assertTrue(tableExists("sys_region"), "V7 应创建行政区划表");
        assertTrue(tableExists("plant_category"), "V7 应创建植物类别表");
        assertTrue(tableExists("plant_species"), "V7 应创建标准物种表");
        assertTrue(tableExists("plant_observation"), "V7 应创建观察记录表");
        assertTrue(tableExists("plant_photo"), "V7 应创建观察照片表");
        assertTrue(tableExists("plant_field_definition"), "V8 应创建动态描述项定义表");
        assertTrue(tableExists("plant_field_value"), "V8 应创建动态描述值表");
        assertTrue(tableExists("plant_review"), "V9 应创建教师审核历史表");
        assertTrue(tableExists("plant_comment"), "V9 应创建评论表");
        assertTrue(tableExists("plant_rating"), "V9 应创建星级评价表");
        assertEquals(34, countRegionProvinces(), "V10 应写入 34 个省级行政区种子");
        assertEquals(4, countActiveCategories(), "V10 应写入 4 个植物类别种子");
        assertTrue(columnExists("sys_region", "center_lng"), "V11 应增加行政区可视化中心字段");
        assertTrue(columnExists("sys_region", "center_lat"), "V11 应增加行政区可视化中心字段");
        assertTrue(columnExists("sys_region", "min_lng"), "V11 应增加行政区范围字段");
    }

    private static int countClassDictSeeds() throws SQLException {
        String sql = "SELECT COUNT(*) FROM sys_dict WHERE dict_type = 'class' AND deleted = 0";
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static int countRegionProvinces() throws SQLException {
        String sql = "SELECT COUNT(*) FROM sys_region WHERE region_level = 'PROVINCE' AND deleted = 0";
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static int countActiveCategories() throws SQLException {
        String sql = "SELECT COUNT(*) FROM plant_category WHERE enabled = 1 AND deleted = 0";
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static int countTables() throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?";
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MYSQL.getDatabaseName());
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private static boolean tableExists(String tableName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ?
                """;
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1) == 1;
            }
        }
    }

    private static boolean columnExists(String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """;
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, tableName);
            statement.setString(3, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1) == 1;
            }
        }
    }

    private static int countUsers() throws SQLException {
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM sys_user");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }
}
