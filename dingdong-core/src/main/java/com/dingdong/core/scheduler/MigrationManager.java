package com.dingdong.core.scheduler;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 版本化的 Schema 迁移管理器。
 * 通过 schema_version 表记录当前版本，启动时按序执行迁移。
 */
@Slf4j
public class MigrationManager {

    private final DbManager dbManager;
    private final List<Migration> migrations = new ArrayList<>();

    public MigrationManager(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * 注册一个迁移。
     * @param version 版本号（从 1 开始递增）
     * @param name 迁移描述
     * @param sql DDL SQL 语句
     */
    public void register(int version, String name, String sql) {
        migrations.add(new Migration(version, name, sql));
    }

    /**
     * 执行所有未应用的迁移。
     */
    public void migrate() {
        Connection conn = null;
        try {
            conn = dbManager.getConnectionForWrite();
            conn.setAutoCommit(false);  // 开启事务
            
            ensureSchemaVersionTable(conn);
            int currentVersion = getCurrentVersion(conn);

            for (Migration m : migrations) {
                if (m.version > currentVersion) {
                    log.info("Running migration {}: {}", m.version, m.name);
                    try (Statement stmt = conn.createStatement()) {
                        for (String s : m.sql.split(";")) {
                            s = s.trim();
                            if (!s.isEmpty()) {
                                stmt.execute(s);
                            }
                        }
                    }
                    updateVersion(conn, m.version);
                    log.info("Migration {} completed", m.version);
                    currentVersion = m.version;
                }
            }
            
            conn.commit();  // 提交事务
            log.info("All migrations completed successfully");
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();  // 回滚事务
                    log.error("Migration failed, rolled back", e);
                } catch (SQLException ex) {
                    log.error("Failed to rollback migration", ex);
                }
            }
            throw new RuntimeException("Migration failed", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Failed to close connection", e);
                }
            }
        }
    }

    private void ensureSchemaVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at TEXT DEFAULT (datetime('now')))");
        }
    }

    private int getCurrentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
            if (rs.next()) {
                int v = rs.getInt(1);
                return rs.wasNull() ? 0 : v;
            }
        }
        return 0;
    }

    private void updateVersion(Connection conn, int version) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO schema_version (version) VALUES (" + version + ")");
        }
    }

    /**
     * 校验并确保表中存在指定列。若缺失则通过 ALTER TABLE ADD COLUMN 补充。
     * 用于已有数据库的表结构向前兼容（列级）。
     */
    public void ensureColumn(String tableName, String columnName, String columnDef) {
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            Set<String> existingColumns = new HashSet<>();
            while (rs.next()) {
                existingColumns.add(rs.getString("name"));
            }
            if (!existingColumns.contains(columnName)) {
                String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef;
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute(sql);
                    log.info("Added missing column: {}.{} ({})", tableName, columnName, columnDef);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to ensure column {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }

    public static class Migration {
        final int version;
        final String name;
        final String sql;

        Migration(int version, String name, String sql) {
            this.version = version;
            this.name = name;
            this.sql = sql;
        }
    }
}
