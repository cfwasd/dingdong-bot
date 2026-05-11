package com.napcat.core.scheduler;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite 连接管理器。
 * 数据库文件路径可由构造函数传入，默认 napcat_data/napcat.db。
 * 
 * 注意：SQLite 支持多线程访问，但写操作需要串行化。
 * 本实现采用"每次获取新连接"的策略，配合 busy_timeout 实现并发控制。
 * 使用 DELETE 模式避免 WAL 模式的锁文件问题。
 */
@Slf4j
public class DbManager {

    private final String dbPath;
    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile boolean initialized = false;

    public DbManager() {
        this("napcat_data/napcat.db");
    }

    public DbManager(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * 初始化：创建目录、验证环境。
     */
    public void init() {
        if (initialized) {
            log.debug("Database already initialized, skipping");
            return;
        }
        
        writeLock.lock();
        try {
            if (initialized) {
                return;
            }
            
            doInit();
            initialized = true;
            
        } finally {
            writeLock.unlock();
        }
    }
    
    private void doInit() {
        int maxRetries = 5;
        long baseWaitMs = 2000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                java.io.File dbFile = new java.io.File(dbPath);
                java.io.File parentDir = dbFile.getParentFile();
                
                if (parentDir != null && !parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        log.error("Failed to create database directory: {}", parentDir.getAbsolutePath());
                        throw new RuntimeException("Cannot create database directory: " + parentDir.getAbsolutePath());
                    }
                    log.info("Created database directory: {}", parentDir.getAbsolutePath());
                }
                
                if (!parentDir.canWrite()) {
                    log.error("Database directory is not writable: {}", parentDir.getAbsolutePath());
                    throw new RuntimeException("Database directory is not writable: " + parentDir.getAbsolutePath());
                }

                // 诊断信息：检查文件状态
                if (dbFile.exists()) {
                    log.info("Database file exists: size={} bytes, canRead={}, canWrite={}", 
                            dbFile.length(), dbFile.canRead(), dbFile.canWrite());
                } else {
                    log.info("Database file does not exist, will create new one");
                }

                String jdbcUrl = buildJdbcUrl();
                
                // 测试连接是否正常（不执行额外的 PRAGMA）
                try (Connection testConn = DriverManager.getConnection(jdbcUrl)) {
                    // 验证连接是否可用
                    testConn.isValid(2);
                    log.debug("SQLite connection test successful");
                }
                
                log.info("SQLite database opened successfully: {} (DELETE mode enabled)", dbPath);
                return;
                
            } catch (SQLException e) {
                String errorMsg = e.getMessage();
                log.error("SQL error on attempt {}: {}", attempt, errorMsg);
                
                if (errorMsg != null && (errorMsg.contains("SQLITE_BUSY") || errorMsg.contains("locked"))) {
                    long waitTime = baseWaitMs * attempt;
                    log.warn("Database is locked. Waiting {}ms before retry... Attempt {}/{}", 
                            waitTime, attempt, maxRetries);
                    
                    // 最后一次重试前清理可能的锁文件
                    if (attempt == maxRetries) {
                        log.error("Final attempt: cleaning up lock files and forcing connection");
                        tryCleanupLockFiles();
                        
                        // Windows 特殊处理：删除并重建空文件
                        forceRecreateDatabaseFile();
                    }
                    
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for database lock", ie);
                    }
                } else {
                    log.error("Failed to initialize SQLite database: {}", errorMsg);
                    throw new RuntimeException("Failed to open SQLite database: " + dbPath, e);
                }
            }
        }
        
        log.error("Failed to open database after {} retries.", maxRetries);
        log.error("Please check:");
        log.error("1. No other process is using the database (check Task Manager for java.exe)");
        log.error("2. Antivirus software is not blocking access");
        log.error("3. File permissions are correct");
        log.error("4. Try manually deleting the database file: {}", dbPath);
        throw new RuntimeException("Failed to open SQLite database after " + maxRetries + 
                " retries. The database file is locked or corrupted.");
    }
    
    /**
     * 强制重建数据库文件（Windows 特殊处理）
     */
    private void forceRecreateDatabaseFile() {
        java.io.File dbFile = new java.io.File(dbPath);
        java.io.File walFile = new java.io.File(dbPath + "-wal");
        java.io.File shmFile = new java.io.File(dbPath + "-shm");
        java.io.File journalFile = new java.io.File(dbPath + "-journal");
        
        // 删除所有相关文件
        deleteFileIfExists(walFile);
        deleteFileIfExists(shmFile);
        deleteFileIfExists(journalFile);
        
        // 如果主文件存在且大小为 0，删除后重建
        if (dbFile.exists() && dbFile.length() == 0) {
            if (dbFile.delete()) {
                log.info("Deleted empty database file: {}", dbFile.getAbsolutePath());
                try {
                    if (dbFile.createNewFile()) {
                        log.info("Created fresh database file: {}", dbFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    log.warn("Failed to recreate database file: {}", e.getMessage());
                }
            }
        }
    }
    
    private void deleteFileIfExists(java.io.File file) {
        if (file.exists()) {
            if (file.delete()) {
                log.info("Deleted lock file: {}", file.getAbsolutePath());
            } else {
                log.warn("Failed to delete lock file: {}", file.getAbsolutePath());
            }
        }
    }
    
    /**
     * 构建 JDBC URL（使用 DELETE 模式）
     */
    private String buildJdbcUrl() {
        // 使用完整的绝对路径，避免相对路径问题
        java.io.File dbFile = new java.io.File(dbPath);
        String absolutePath = dbFile.getAbsolutePath();
        return "jdbc:sqlite:" + absolutePath + "?journal_mode=DELETE&busy_timeout=30000&foreign_keys=ON&synchronous=NORMAL";
    }

    /**
     * 尝试清理锁文件
     */
    private void tryCleanupLockFiles() {
        java.io.File walFile = new java.io.File(dbPath + "-wal");
        java.io.File shmFile = new java.io.File(dbPath + "-shm");
        
        if (walFile.exists()) {
            if (walFile.delete()) {
                log.info("Cleaned up WAL lock file: {}", walFile.getAbsolutePath());
            } else {
                log.warn("Failed to delete WAL lock file: {}", walFile.getAbsolutePath());
            }
        }
        
        if (shmFile.exists()) {
            if (shmFile.delete()) {
                log.info("Cleaned up SHM lock file: {}", shmFile.getAbsolutePath());
            } else {
                log.warn("Failed to delete SHM lock file: {}", shmFile.getAbsolutePath());
            }
        }
    }
    
    /**
     * 静默关闭连接（用于异常处理时清理资源）
     */
    private void closeConnectionSilently(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                log.debug("Failed to close connection silently: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取数据库连接（线程安全）。
     * 每次调用返回新的连接，调用者负责关闭。
     * 
     * @return 新的 Connection 对象，使用后必须关闭
     */
    public Connection getConnection() {
        if (!initialized) {
            init();
        }
        
        try {
            return DriverManager.getConnection(buildJdbcUrl());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get SQLite connection", e);
        }
    }

    /**
     * 获取写锁保护的连接。
     * 用于需要独占写操作的场景（如迁移、建表）。
     * 
     * @return 新的 Connection 对象，使用后必须关闭
     */
    public Connection getConnectionForWrite() {
        writeLock.lock();
        try {
            return getConnection();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 关闭数据库管理器（已废弃，因为不再维护单例连接）。
     */
    @Deprecated
    public void close() {
        log.info("SQLite database manager closed: {}", dbPath);
    }

    public String getDbPath() {
        return dbPath;
    }
}
