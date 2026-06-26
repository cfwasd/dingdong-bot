package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import com.dingdong.core.scheduler.DbManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 漂流瓶工具。扔瓶/捞瓶，SQLite持久化，群聊娱乐用。
 */
@Slf4j
@Component
public class DriftingBottleTool {

    private final DbManager dbManager;
    private volatile boolean tableReady;

    public DriftingBottleTool(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    private void ensureTable() {
        if (tableReady) return;
        synchronized (this) {
            if (tableReady) return;
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS drifting_bottles (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "content TEXT NOT NULL," +
                     "sender_name TEXT DEFAULT '匿名'," +
                     "created_at INTEGER NOT NULL," +
                     "picked INTEGER DEFAULT 0," +
                     "picked_at INTEGER DEFAULT 0)")) {
                stmt.execute();
                tableReady = true;
            } catch (Exception e) {
                log.error("Failed to create drifting_bottles table", e);
            }
        }
    }

    @Tool(
        name = "throw_bottle",
        description = "扔漂流瓶。当用户说\"扔漂流瓶\"\"扔瓶子\"\"丢漂流瓶\"\"写漂流瓶\"时使用。\n" +
                      "把一段话装进漂流瓶扔进大海，等待有缘人捞起。"
    )
    public String throwBottle(
        @ToolParam(value = "content", description = "漂流瓶内容（一句话或一段话）", required = true) String content
    ) {
        if (content == null || content.isBlank()) {
            return "🍾 漂流瓶不能是空的呀！写点什么吧~";
        }
        if (content.length() > 500) {
            return "🍾 漂流瓶内容太长啦，控制在500字以内吧~";
        }

        ensureTable();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO drifting_bottles (content, sender_name, created_at) VALUES (?, '匿名瓶友', ?)")) {
            stmt.setString(1, content.trim());
            stmt.setLong(2, System.currentTimeMillis() / 1000);
            stmt.executeUpdate();
            return "🍾 漂流瓶已扔进大海！等待有缘人捞起~ 🌊\n\n📝 内容：" + content.trim();
        } catch (Exception e) {
            log.error("Throw bottle failed", e);
            return "🍾 漂流瓶扔失败了...海风太大，再试一次？";
        }
    }

    @Tool(
        name = "pick_bottle",
        description = "捞漂流瓶。当用户说\"捞漂流瓶\"\"捞瓶子\"\"捡漂流瓶\"\"打捞漂流瓶\"\"看看漂流瓶\"时使用。\n" +
                      "从大海中随机捞起一个未被捞过的漂流瓶。"
    )
    public String pickBottle() {
        ensureTable();
        try (Connection conn = dbManager.getConnection()) {
            // 统计未捞瓶子
            int total;
            try (PreparedStatement countStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM drifting_bottles WHERE picked = 0")) {
                ResultSet rs = countStmt.executeQuery();
                total = rs.next() ? rs.getInt(1) : 0;
            }

            if (total == 0) {
                return "🌊 海面上没有漂流瓶了...\n\n💡 你可以扔一个！说\"扔漂流瓶\"试试~";
            }

            // 随机捞一个
            int offset = ThreadLocalRandom.current().nextInt(total);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, content, sender_name, created_at FROM drifting_bottles WHERE picked = 0 LIMIT 1 OFFSET ?")) {
                stmt.setInt(1, offset);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    long id = rs.getLong("id");
                    String content = rs.getString("content");
                    String sender = rs.getString("sender_name");
                    long createdAt = rs.getLong("created_at");

                    // 标记为已捞
                    try (PreparedStatement updateStmt = conn.prepareStatement(
                            "UPDATE drifting_bottles SET picked = 1, picked_at = ? WHERE id = ?")) {
                        updateStmt.setLong(1, System.currentTimeMillis() / 1000);
                        updateStmt.setLong(2, id);
                        updateStmt.executeUpdate();
                    }

                    java.time.Instant instant = java.time.Instant.ofEpochSecond(createdAt);
                    String timeStr = instant.atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));

                    return "🍾 你捞到了一个漂流瓶！\n\n" +
                           "━━━━━━━━━━━━━━\n" +
                           "📝 " + content + "\n" +
                           "━━━━━━━━━━━━━━\n" +
                           "👤 " + sender + "\n" +
                           "🕐 " + timeStr + "\n\n" +
                           "🌊 还剩 " + (total - 1) + " 个瓶子在海面上漂着...";
                }
            }

            return "🌊 瓶子刚被别人捞走了...再试一次？";
        } catch (Exception e) {
            log.error("Pick bottle failed", e);
            return "🌊 捞瓶子的时候被海浪打翻了...再试一次？";
        }
    }

    @Tool(
        name = "bottle_stats",
        description = "查看漂流瓶统计。当用户说\"漂流瓶统计\"\"有多少漂流瓶\"\"瓶子数量\"时使用。"
    )
    public String stats() {
        ensureTable();
        try (Connection conn = dbManager.getConnection()) {
            int total, unpicked, picked;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) AS total, SUM(CASE WHEN picked = 0 THEN 1 ELSE 0 END) AS unpicked FROM drifting_bottles")) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    total = rs.getInt("total");
                    unpicked = rs.getInt("unpicked");
                    picked = total - unpicked;
                } else {
                    total = unpicked = picked = 0;
                }
            }

            if (total == 0) {
                return "🌊 大海里还没有漂流瓶...\n\n💡 说\"扔漂流瓶\"来扔第一个！";
            }

            return "🍾 漂流瓶统计：\n\n" +
                   "📊 总瓶子数：" + total + "\n" +
                   "🌊 海上漂着：" + unpicked + "\n" +
                   "✅ 已被捞起：" + picked;
        } catch (Exception e) {
            log.error("Bottle stats failed", e);
            return "🍾 统计失败...海浪太大了。";
        }
    }
}
