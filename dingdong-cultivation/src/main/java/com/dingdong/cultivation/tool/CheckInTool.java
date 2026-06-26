package com.dingdong.cultivation.tool;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import com.dingdong.core.scheduler.DbManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CheckInTool {

    private static final int BASE_POINTS = 10;
    private static final int STREAK_BONUS = 3;
    private static final int MAX_STREAK_BONUS = 30;
    private static final int LEADERBOARD_SIZE = 10;

    private static final String[] FORTUNE_BONUS = {
        "大吉", "吉", "中吉", "小吉", "末吉", "凶", "大凶"
    };
    private static final int[] FORTUNE_MULTIPLIER = {3, 2, 1, 1, 1, 0, -1};

    private final DbManager dbManager;
    private volatile boolean tableReady;

    public CheckInTool(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    private void ensureTable() {
        if (tableReady) return;
        synchronized (this) {
            if (tableReady) return;
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS daily_checkin (" +
                     "user_id INTEGER NOT NULL," +
                     "group_id INTEGER NOT NULL DEFAULT 0," +
                     "user_name TEXT DEFAULT ''," +
                     "last_checkin TEXT NOT NULL DEFAULT ''," +
                     "streak INTEGER DEFAULT 0," +
                     "total_points INTEGER DEFAULT 0," +
                     "total_checkins INTEGER DEFAULT 0," +
                     "PRIMARY KEY (user_id, group_id))")) {
                stmt.execute();
                tableReady = true;
            } catch (Exception e) {
                log.error("Failed to create daily_checkin table", e);
            }
        }
    }

    @Tool(
        name = "daily_checkin",
        description = "每日签到。当用户说\"签到\"\"打卡\"\"每日签到\"\"来签到\"时使用。\n" +
                      "每天签到一次获得积分，连续签到有额外加成。签到时会附带今日运势加成。\n" +
                      "需要提供 user_id 和 group_id 来标识签到者（群聊中 group_id 为群号，私聊为0）。"
    )
    public String checkin(
        @ToolParam(value = "user_id", description = "签到用户ID（QQ号）", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户名/昵称", required = false) String userName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        LocalDate today = LocalDate.now();

        try (Connection conn = dbManager.getConnection()) {
            Record rec = getRecord(conn, userId, groupId);
            LocalDate lastDate = rec.lastCheckin;

            if (lastDate != null && lastDate.equals(today)) {
                int fortuneIdx = todayHash(userId, groupId) % FORTUNE_BONUS.length;
                String fortune = FORTUNE_BONUS[fortuneIdx];
                String fortuneEmoji = getFortuneEmoji(fortune);
                return "✅ 今天已经签到过了！\n\n" +
                       "📅 " + today + "\n" +
                       "🔥 连续签到：第 " + rec.streak + " 天\n" +
                       "💰 总积分：" + rec.totalPoints + "\n" +
                       "📊 总签到次数：" + rec.totalCheckins + "\n" +
                       "🔮 今日运势加成：" + fortuneEmoji + " " + fortune;
            }

            int streak;
            if (lastDate != null && lastDate.equals(today.minusDays(1))) {
                streak = rec.streak + 1;
            } else {
                streak = 1;
            }

            int fortuneIdx = todayHash(userId, groupId) % FORTUNE_BONUS.length;
            String fortune = FORTUNE_BONUS[fortuneIdx];
            int fortuneMult = FORTUNE_MULTIPLIER[fortuneIdx];
            String fortuneEmoji = getFortuneEmoji(fortune);

            int streakBonus = Math.min(streak * STREAK_BONUS, MAX_STREAK_BONUS);
            int fortuneBonus = Math.max(-5, fortuneMult * 5);
            int earned = Math.max(1, BASE_POINTS + streakBonus + fortuneBonus);
            int totalPoints = rec.totalPoints + earned;
            int totalCheckins = rec.totalCheckins + 1;

            upsertRecord(conn, userId, groupId, userName, today.toString(), streak, totalPoints, totalCheckins);

            StringBuilder sb = new StringBuilder();
            sb.append("✅ 签到成功！\n\n");
            sb.append("📅 ").append(today).append("\n");
            sb.append("━━━━━━━━━━━━━━\n");
            sb.append("🔥 连续签到：第 ").append(streak).append(" 天\n");
            sb.append("💎 基础签到：+").append(BASE_POINTS).append("\n");
            if (streakBonus > 0) sb.append("🔥 连续加成：+").append(streakBonus).append("\n");
            if (fortuneBonus > 0) {
                sb.append("🔮 运势加成：+").append(fortuneBonus).append(" (").append(fortuneEmoji).append(" ").append(fortune).append(")\n");
            } else if (fortuneBonus < 0) {
                sb.append("💀 运势减成：").append(fortuneBonus).append(" (").append(fortuneEmoji).append(" ").append(fortune).append(")\n");
            }
            sb.append("━━━━━━━━━━━━━━\n");
            sb.append("💰 今日获得：").append(earned).append(" 积分\n");
            sb.append("🏦 总积分：").append(totalPoints).append("\n");
            sb.append("📊 总签到次数：").append(totalCheckins).append("\n");
            sb.append("🔮 今日运势：").append(fortuneEmoji).append(" ").append(fortune);

            // 修仙联动：签到额外修为
            try (Connection conn2 = dbManager.getConnection()) {
                PreparedStatement cuStmt = conn2.prepareStatement(
                    "SELECT root_bone FROM cultivation_users WHERE user_id = ? AND group_id = ?");
                cuStmt.setLong(1, userId);
                cuStmt.setLong(2, groupId);
                ResultSet cuRs = cuStmt.executeQuery();
                if (cuRs.next()) {
                    int rootBone = cuRs.getInt("root_bone");
                    int extraCultivation = rootBone * 2;
                    PreparedStatement updStmt = conn2.prepareStatement(
                        "UPDATE cultivation_users SET cultivation = cultivation + ?, last_checkin_date = ? WHERE user_id = ? AND group_id = ?");
                    updStmt.setInt(1, extraCultivation);
                    updStmt.setString(2, today.toString());
                    updStmt.setLong(3, userId);
                    updStmt.setLong(4, groupId);
                    updStmt.executeUpdate();
                    sb.append("\n\n⚡ 修仙加成：+").append(extraCultivation).append(" 修为（根骨×2）");
                }
            } catch (Exception ignored) {
                // 非修仙者，跳过
            }

            if (streak == 7) sb.append("\n\n🎉 连续签到7天！你是真的猛士！");
            else if (streak == 30) sb.append("\n\n👑 连续签到30天！传奇级别！");
            else if (streak == 100) sb.append("\n\n🏆 连续签到100天！群里没几个人能做到！");

            return sb.toString();
        } catch (Exception e) {
            log.error("Checkin failed", e);
            return "❌ 签到失败，签到本被风吹走了...";
        }
    }

    @Tool(
        name = "checkin_status",
        description = "查看签到状态。当用户说\"签到状态\"\"我的签到\"\"签到记录\"\"签到排名\"\"签到排行榜\"时使用。"
    )
    public String status(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户名/昵称", required = false) String userName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        LocalDate today = LocalDate.now();

        try (Connection conn = dbManager.getConnection()) {
            Record rec = getRecord(conn, userId, groupId);
            boolean checkedToday = rec.lastCheckin != null && rec.lastCheckin.equals(today);

            List<Record> leaderboard = getLeaderboard(conn, groupId, LEADERBOARD_SIZE);
            int rank = -1;
            for (int i = 0; i < leaderboard.size(); i++) {
                if (leaderboard.get(i).userId == userId) { rank = i + 1; break; }
            }

            StringBuilder sb = new StringBuilder();
            String displayName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);
            sb.append("📋 ").append(displayName).append(" 的签到状态\n\n");
            sb.append("📅 今天：").append(checkedToday ? "✅ 已签到" : "❌ 未签到，说\"签到\"来打卡！").append("\n");
            sb.append("🔥 连续签到：").append(rec.streak).append(" 天\n");
            sb.append("💰 总积分：").append(rec.totalPoints).append("\n");
            sb.append("📊 总签到次数：").append(rec.totalCheckins).append("\n");

            if (rank > 0) {
                sb.append("🏅 本群排名：第 ").append(rank).append(" 名\n");
            }

            if (!leaderboard.isEmpty()) {
                sb.append("\n🏆 本群签到排行榜 TOP").append(Math.min(LEADERBOARD_SIZE, leaderboard.size())).append("：\n");
                for (int i = 0; i < leaderboard.size(); i++) {
                    Record r = leaderboard.get(i);
                    String name = r.userName != null && !r.userName.isBlank() ? r.userName : String.valueOf(r.userId);
                    String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : (i + 1) + ".";
                    sb.append(medal).append(" ").append(name)
                      .append(" — ").append(r.totalPoints).append("分")
                      .append(" | 签到").append(r.totalCheckins).append("次");
                    if (r.lastCheckin != null && r.lastCheckin.equals(today)) {
                        sb.append(" ✅");
                    }
                    sb.append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Checkin status failed", e);
            return "❌ 查询失败，签到本找不到了...";
        }
    }

    // ========= 内部 =========

    private static class Record {
        long userId;
        String userName;
        LocalDate lastCheckin;
        int streak;
        int totalPoints;
        int totalCheckins;
    }

    private Record getRecord(Connection conn, long userId, long groupId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT user_name, last_checkin, streak, total_points, total_checkins FROM daily_checkin WHERE user_id = ? AND group_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            ResultSet rs = stmt.executeQuery();
            Record rec = new Record();
            rec.userId = userId;
            if (rs.next()) {
                rec.userName = rs.getString("user_name");
                String last = rs.getString("last_checkin");
                rec.lastCheckin = (last != null && !last.isEmpty()) ? LocalDate.parse(last) : null;
                rec.streak = rs.getInt("streak");
                rec.totalPoints = rs.getInt("total_points");
                rec.totalCheckins = rs.getInt("total_checkins");
            }
            return rec;
        }
    }

    private void upsertRecord(Connection conn, long userId, long groupId, String userName,
                              String date, int streak, int totalPoints, int totalCheckins) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO daily_checkin (user_id, group_id, user_name, last_checkin, streak, total_points, total_checkins) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(user_id, group_id) DO UPDATE SET user_name = excluded.user_name, " +
                "last_checkin = excluded.last_checkin, streak = excluded.streak, " +
                "total_points = excluded.total_points, total_checkins = excluded.total_checkins")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            stmt.setString(3, (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId));
            stmt.setString(4, date);
            stmt.setInt(5, streak);
            stmt.setInt(6, totalPoints);
            stmt.setInt(7, totalCheckins);
            stmt.executeUpdate();
        }
    }

    private List<Record> getLeaderboard(Connection conn, long groupId, int limit) throws Exception {
        List<Record> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT user_id, user_name, last_checkin, streak, total_points, total_checkins " +
                "FROM daily_checkin WHERE group_id = ? ORDER BY total_points DESC LIMIT ?")) {
            stmt.setLong(1, groupId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Record rec = new Record();
                rec.userId = rs.getLong("user_id");
                rec.userName = rs.getString("user_name");
                String last = rs.getString("last_checkin");
                rec.lastCheckin = (last != null && !last.isEmpty()) ? LocalDate.parse(last) : null;
                rec.streak = rs.getInt("streak");
                rec.totalPoints = rs.getInt("total_points");
                rec.totalCheckins = rs.getInt("total_checkins");
                list.add(rec);
            }
        }
        return list;
    }

    private int todayHash(long userId, long groupId) {
        long seed = LocalDate.now().toEpochDay() * 31 + userId * 17 + groupId * 13;
        return (int) (Math.abs(seed) % 10000);
    }

    private String getFortuneEmoji(String fortune) {
        return switch (fortune) {
            case "大吉" -> "🎉";
            case "吉" -> "😊";
            case "中吉" -> "😄";
            case "小吉" -> "🙂";
            case "末吉" -> "😐";
            case "凶" -> "😟";
            case "大凶" -> "😱";
            default -> "";
        };
    }
}
