package com.dingdong.agent.memory;

import com.dingdong.agent.session.SessionKey;
import com.dingdong.core.scheduler.DbManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 记忆测试数据注入器。
 * 在 {@code dingdong.memory.test-data-enabled=true} 时自动插入模拟记忆数据，
 * 用于本地测试记忆压缩、检索和归纳功能。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "dingdong.memory", name = "test-data-enabled", havingValue = "true")
public class MemoryTestDataInjector {

    private final DbManager dbManager;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public MemoryTestDataInjector(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * 注入测试数据。涵盖多用户、多群、多天的场景。
     */
    public void injectTestData() {
        String today = LocalDate.now().format(DATE_FMT);
        String yesterday = LocalDate.now().minusDays(1).format(DATE_FMT);

        // 用户 A（私聊）— 今天的碎片化记忆
        SessionKey userA = SessionKey.ofPrivate(10001L);
        insertMemory(userA, "用户A叫张三，25岁", "fact", today);
        insertMemory(userA, "用户A喜欢喝冰美式，不加糖", "preference", today);
        insertMemory(userA, "用户A今天讨论了Java并发编程", "topic", today);
        insertMemory(userA, "用户A提到过周末要去爬山", "fact", today);

        // 用户 B（群聊 12345）— 今天的碎片化记忆
        SessionKey userBGroup = SessionKey.ofGroup(10002L, 12345L);
        insertMemory(userBGroup, "用户B是群管理员，ID为10002", "fact", today);
        insertMemory(userBGroup, "用户B讨厌下雨天", "preference", today);
        insertMemory(userBGroup, "群里讨论了Spring Boot 3的新特性", "topic", today);
        insertMemory(userBGroup, "用户B分享了一篇关于微服务架构的文章", "fact", today);
        insertMemory(userBGroup, "用户B表示对Kubernetes很感兴趣", "preference", today);

        // 用户 C（群聊 67890）— 今天的碎片化记忆（大量数据，测试归纳效果）
        SessionKey userCGroup = SessionKey.ofGroup(10003L, 67890L);
        insertMemory(userCGroup, "用户C叫李四，是一名后端工程师", "fact", today);
        insertMemory(userCGroup, "用户C喜欢打游戏，特别是原神", "preference", today);
        insertMemory(userCGroup, "讨论了数据库索引优化策略", "topic", today);
        insertMemory(userCGroup, "用户C提到公司在做技术架构升级", "fact", today);
        insertMemory(userCGroup, "用户C不喜欢吃香菜", "preference", today);
        insertMemory(userCGroup, "讨论了Redis缓存穿透和雪崩的解决方案", "topic", today);
        insertMemory(userCGroup, "用户C养了一只橘猫，名字叫胖虎", "fact", today);
        insertMemory(userCGroup, "用户C最近在学习Rust语言", "preference", today);

        // 用户 A — 昨天的归纳摘要（测试 retrieve 优先返回 summaries）
        insertSummary(userA, yesterday,
                "用户A昨天聊到：他叫张三，25岁，喜欢喝冰美式不加糖，对Java并发编程感兴趣。");

        // 用户 B — 昨天的归纳摘要
        insertSummary(userBGroup, yesterday,
                "用户B昨天在群里聊到：他是群管理员，讨厌下雨天，分享了Spring Boot相关文章。");

        log.info("Test memory data injected: today={}, yesterday={}", today, yesterday);
    }

    private void insertMemory(SessionKey key, String content, String type, String date) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sql = "INSERT INTO memories (id, user_id, group_id, content, type, created_at) VALUES (?, ?, ?, ?, ?, datetime(? || 'T12:00:00'))";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setLong(2, key.userId());
            ps.setLong(3, key.groupId());
            ps.setString(4, content);
            ps.setString(5, type);
            ps.setString(6, date);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to insert test memory for {}: {}", key, e.getMessage());
        }
    }

    private void insertSummary(SessionKey key, String summaryDate, String content) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sql = "INSERT INTO memory_summaries (id, user_id, group_id, summary_date, content) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setLong(2, key.userId());
            ps.setLong(3, key.groupId());
            ps.setString(4, summaryDate);
            ps.setString(5, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to insert test summary for {}: {}", key, e.getMessage());
        }
    }
}
