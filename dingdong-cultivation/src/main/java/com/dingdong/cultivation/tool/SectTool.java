package com.dingdong.cultivation.tool;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import com.dingdong.core.scheduler.DbManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class SectTool {

    private final DbManager dbManager;
    private volatile boolean tableReady;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int CREATE_SECT_COST = 500;
    private static final int MIN_REALM_FOR_CREATE = 3;

    public SectTool(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    private void ensureTable() {
        if (tableReady) return;
        synchronized (this) {
            if (tableReady) return;
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS sects (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "group_id INTEGER NOT NULL DEFAULT 0," +
                        "name TEXT NOT NULL," +
                        "leader_id INTEGER NOT NULL," +
                        "leader_name TEXT DEFAULT ''," +
                        "level INTEGER DEFAULT 1," +
                        "contribution INTEGER DEFAULT 0," +
                        "member_count INTEGER DEFAULT 1," +
                        "created_at TEXT DEFAULT '')")) {
                    stmt.execute();
                }
                try (PreparedStatement idx = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_sects_group ON sects(group_id, name)")) {
                    idx.execute();
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS sect_members (" +
                        "sect_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "group_id INTEGER NOT NULL DEFAULT 0," +
                        "user_name TEXT DEFAULT ''," +
                        "joined_at TEXT DEFAULT ''," +
                        "PRIMARY KEY (sect_id, user_id, group_id))")) {
                    stmt.execute();
                }
                tableReady = true;
            } catch (Exception e) {
                log.error("Failed to create sect tables", e);
            }
        }
    }

    private String now() {
        return LocalDateTime.now().format(ISO_FMT);
    }

    private int getRealmIndex(String realm) {
        String[] realms = {"mortal", "lianqi", "zhuji", "jindan", "yuanying", "huashen", "dujie", "dacheng", "zhenxian"};
        for (int i = 0; i < realms.length; i++) {
            if (realms[i].equals(realm)) return i;
        }
        return 0;
    }

    private boolean isCultivator(Connection conn, long userId, long groupId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            return stmt.executeQuery().next();
        }
    }

    private String getUserRealm(Connection conn, long userId, long groupId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT realm FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("realm") : "mortal";
        }
    }

    private int getLevelUpCost(int currentLevel) {
        int[] costs = {0, 0, 500, 1200, 2500, 4500, 7000, 10000, 14000, 20000};
        return currentLevel < costs.length ? costs[currentLevel] : 99999;
    }

    @Tool(name = "create_sect", description = "创建宗门。金丹期以上+500灵石。当用户说\"创建宗门\"\"建立宗门\"\"开宗立派\"时使用。")
    public String createSect(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName,
        @ToolParam(value = "sect_name", description = "宗门名称", required = true) String sectName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        if (sectName == null || sectName.isBlank()) return "❌ 宗门名称不能为空！";
        sectName = sectName.trim();
        if (sectName.length() > 20) return "❌ 宗门名称不能超过20字！";

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            if (!isCultivator(conn, userId, groupId)) {
                return "🤔 " + uName + " 你还没开启修仙之路！";
            }

            String realm = getUserRealm(conn, userId, groupId);
            if (getRealmIndex(realm) < MIN_REALM_FOR_CREATE) {
                return "❌ 创建宗门需要金丹期以上！当前境界不足。";
            }

            int stones = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT spirit_stones FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) stones = rs.getInt("spirit_stones");
            }
            if (stones < CREATE_SECT_COST) {
                return "❌ 灵石不足！需要 " + CREATE_SECT_COST + " 灵石，当前：" + stones;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT s.name FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return "❌ 你已加入宗门「" + rs.getString("name") + "」，不能创建新宗门！";
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id FROM sects WHERE group_id = ? AND name = ?")) {
                stmt.setLong(1, groupId);
                stmt.setString(2, sectName);
                if (stmt.executeQuery().next()) {
                    return "❌ 宗门「" + sectName + "」已存在！换一个名字吧~";
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE cultivation_users SET spirit_stones = spirit_stones - ? WHERE user_id = ? AND group_id = ?")) {
                stmt.setInt(1, CREATE_SECT_COST);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }

            long sectId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO sects (group_id, name, leader_id, leader_name, created_at) VALUES (?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, groupId);
                stmt.setString(2, sectName);
                stmt.setLong(3, userId);
                stmt.setString(4, uName);
                stmt.setString(5, now());
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                sectId = rs.next() ? rs.getLong(1) : -1;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO sect_members (sect_id, user_id, group_id, user_name, joined_at) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.setString(4, uName);
                stmt.setString(5, now());
                stmt.executeUpdate();
            }

            return "🏯 宗门「" + sectName + "」创建成功！\n"
                + "👑 宗主：" + uName + "\n"
                + "💎 花费：" + CREATE_SECT_COST + " 灵石\n"
                + "📊 等级：1级（灵气加成2%）\n\n"
                + "💡 说\"宗门状态\"查看详情\n"
                + "💡 其他人说\"加入宗门 " + sectName + "\"来加入！";
        } catch (Exception e) {
            log.error("create_sect failed", e);
            return "❌ 创建宗门失败...";
        }
    }

    @Tool(name = "join_sect", description = "申请加入宗门。当用户说\"加入宗门\"\"申请入宗\"时使用。")
    public String joinSect(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName,
        @ToolParam(value = "sect_name", description = "宗门名称", required = true) String sectName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            if (!isCultivator(conn, userId, groupId)) {
                return "🤔 " + uName + " 你还没开启修仙之路！";
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT s.name FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return "❌ 你已加入宗门「" + rs.getString("name") + "」！先退出再加入。";
            }

            long sectId = -1;
            String leaderName = "";
            int level = 1;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, leader_name, level FROM sects WHERE group_id = ? AND name = ?")) {
                stmt.setLong(1, groupId);
                stmt.setString(2, sectName);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "❌ 宗门「" + sectName + "」不存在！";
                sectId = rs.getLong("id");
                leaderName = rs.getString("leader_name");
                level = rs.getInt("level");
            }

            int maxMembers = 10 + (level - 1) * 5;
            int memberCount;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM sect_members WHERE sect_id = ?")) {
                stmt.setLong(1, sectId);
                ResultSet rs = stmt.executeQuery();
                memberCount = rs.next() ? rs.getInt(1) : 0;
            }
            if (memberCount >= maxMembers) {
                return "❌ 宗门「" + sectName + "」已满员（" + maxMembers + "人）！";
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO sect_members (sect_id, user_id, group_id, user_name, joined_at) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.setString(4, uName);
                stmt.setString(5, now());
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE sects SET member_count = member_count + 1 WHERE id = ?")) {
                stmt.setLong(1, sectId);
                stmt.executeUpdate();
            }

            return "🎉 " + uName + " 加入了宗门「" + sectName + "」！\n"
                + "👑 宗主：" + leaderName + "\n"
                + "👥 当前人数：" + (memberCount + 1) + "/" + maxMembers;
        } catch (Exception e) {
            log.error("join_sect failed", e);
            return "❌ 加入宗门失败...";
        }
    }

    @Tool(name = "leave_sect", description = "退出宗门。当用户说\"退出宗门\"\"离开宗门\"时使用。")
    public String leaveSect(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            long sectId = -1;
            boolean isLeader = false;
            String sectName = "";

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT sm.sect_id, s.name, s.leader_id FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "🤔 " + uName + " 你还没有加入任何宗门！";
                sectId = rs.getLong("sect_id");
                sectName = rs.getString("name");
                isLeader = rs.getLong("leader_id") == userId;
            }

            if (isLeader) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM sect_members WHERE sect_id = ?")) {
                    stmt.setLong(1, sectId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM sects WHERE id = ?")) {
                    stmt.setLong(1, sectId);
                    stmt.executeUpdate();
                }
                return "💔 宗主 " + uName + " 退出，「" + sectName + "」宗门解散！";
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM sect_members WHERE sect_id = ? AND user_id = ? AND group_id = ?")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE sects SET member_count = member_count - 1 WHERE id = ?")) {
                stmt.setLong(1, sectId);
                stmt.executeUpdate();
            }

            return "👋 " + uName + " 退出了宗门「" + sectName + "」。";
        } catch (Exception e) {
            log.error("leave_sect failed", e);
            return "❌ 退出宗门失败...";
        }
    }

    @Tool(name = "kick_member", description = "踢出宗门成员（宗主专用）。当用户说\"踢出宗门\"\"逐出宗门\"时使用。")
    public String kickMember(
        @ToolParam(value = "user_id", description = "宗主ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "target_id", description = "被踢者ID", required = true) String targetIdStr
    ) {
        long userId, groupId, targetId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }
        try { targetId = Long.parseLong(targetIdStr.trim()); } catch (NumberFormatException e) { return "❌ 目标ID格式错误"; }

        if (userId == targetId) return "🤔 不能踢自己！请使用\"退出宗门\"。";

        ensureTable();

        try (Connection conn = dbManager.getConnection()) {
            long sectId = -1;
            String sectName = "";
            String targetName = "";
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, name FROM sects WHERE group_id = ? AND leader_id = ?")) {
                stmt.setLong(1, groupId);
                stmt.setLong(2, userId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "❌ 你不是任何宗门的宗主！";
                sectId = rs.getLong("id");
                sectName = rs.getString("name");
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT user_name FROM sect_members WHERE sect_id = ? AND user_id = ? AND group_id = ?")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, targetId);
                stmt.setLong(3, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "❌ 该成员不在你的宗门中！";
                targetName = rs.getString("user_name");
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM sect_members WHERE sect_id = ? AND user_id = ? AND group_id = ?")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, targetId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE sects SET member_count = member_count - 1 WHERE id = ?")) {
                stmt.setLong(1, sectId);
                stmt.executeUpdate();
            }

            return "👢 " + targetName + " 被踢出了宗门「" + sectName + "」！";
        } catch (Exception e) {
            log.error("kick_member failed", e);
            return "❌ 踢人失败...";
        }
    }

    @Tool(name = "donate_sect", description = "捐献灵石升级宗门。当用户说\"捐献\"\"宗门捐献\"时使用。")
    public String donateSect(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName,
        @ToolParam(value = "amount", description = "捐献灵石数量", required = true) String amountStr
    ) {
        long userId, groupId;
        int amount;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }
        try { amount = Integer.parseInt(amountStr.trim()); } catch (NumberFormatException e) { return "❌ 数量格式错误"; }

        if (amount <= 0) return "❌ 捐献数量必须大于0！";

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            long sectId = -1;
            String sectName = "";
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT s.id, s.name FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "🤔 " + uName + " 你还没有加入任何宗门！";
                sectId = rs.getLong("id");
                sectName = rs.getString("name");
            }

            int currentStones;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT spirit_stones FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                currentStones = rs.next() ? rs.getInt("spirit_stones") : 0;
            }
            if (currentStones < amount) {
                return "❌ 灵石不足！当前：" + currentStones + "，需要：" + amount;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE cultivation_users SET spirit_stones = spirit_stones - ? WHERE user_id = ? AND group_id = ?")) {
                stmt.setInt(1, amount);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE sects SET contribution = contribution + ? WHERE id = ?")) {
                stmt.setInt(1, amount);
                stmt.setLong(2, sectId);
                stmt.executeUpdate();
            }

            int currentLevel = 1, currentContrib = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT level, contribution FROM sects WHERE id = ?")) {
                stmt.setLong(1, sectId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    currentLevel = rs.getInt("level");
                    currentContrib = rs.getInt("contribution");
                }
            }

            int newLevel = currentLevel;
            for (int lv = currentLevel + 1; lv <= 10; lv++) {
                if (currentContrib >= getLevelUpCost(lv - 1)) newLevel = lv;
                else break;
            }

            if (newLevel > currentLevel) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE sects SET level = ? WHERE id = ?")) {
                    stmt.setInt(1, newLevel);
                    stmt.setLong(2, sectId);
                    stmt.executeUpdate();
                }
                return "💎 " + uName + " 捐献 " + amount + " 灵石！\n🎉 宗门「" + sectName + "」升级至 " + newLevel + " 级！灵气加成 " + (newLevel * 2) + "%！";
            }

            int nextCost = getLevelUpCost(currentLevel);
            return "💎 " + uName + " 捐献 " + amount + " 灵石给宗门「" + sectName + "」！\n"
                + "📊 当前总贡献：" + currentContrib + "\n"
                + "📈 下次升级需要：" + nextCost + " 贡献";
        } catch (Exception e) {
            log.error("donate_sect failed", e);
            return "❌ 捐献失败...";
        }
    }

    @Tool(name = "sect_status", description = "查看宗门信息。当用户说\"宗门状态\"\"宗门信息\"\"查看宗门\"时使用。")
    public String sectStatus(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();

        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT s.* FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "🤔 你还没有加入任何宗门！";

                long sectId = rs.getLong("id");
                StringBuilder sb = new StringBuilder();
                sb.append("🏯 宗门：「").append(rs.getString("name")).append("」\n");
                sb.append("━━━━━━━━━━━━━━\n");
                sb.append("👑 宗主：").append(rs.getString("leader_name")).append("\n");
                sb.append("📊 等级：").append(rs.getInt("level")).append("级（灵气加成").append(rs.getInt("level") * 2).append("%）\n");
                sb.append("💎 总贡献：").append(rs.getInt("contribution")).append("\n");
                sb.append("👥 人数：").append(rs.getInt("member_count")).append("\n");
                sb.append("📅 创建时间：").append(rs.getString("created_at")).append("\n");
                sb.append("━━━━━━━━━━━━━━\n");

                try (PreparedStatement mStmt = conn.prepareStatement(
                        "SELECT user_name FROM sect_members WHERE sect_id = ? ORDER BY joined_at")) {
                    mStmt.setLong(1, sectId);
                    ResultSet mRs = mStmt.executeQuery();
                    sb.append("👥 成员：\n");
                    String leaderName = rs.getString("leader_name");
                    while (mRs.next()) {
                        String mName = mRs.getString("user_name");
                        sb.append("• ").append(mName);
                        if (mName.equals(leaderName)) sb.append(" 👑");
                        sb.append("\n");
                    }
                }

                return sb.toString();
            }
        } catch (Exception e) {
            log.error("sect_status failed", e);
            return "❌ 查询失败...";
        }
    }

    @Tool(name = "sect_ranking", description = "群内宗门排行。当用户说\"宗门排行\"\"宗门排名\"时使用。")
    public String sectRanking(
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr
    ) {
        long groupId;
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT name, leader_name, level, member_count, contribution FROM sects WHERE group_id = ? ORDER BY contribution DESC LIMIT 10")) {
            stmt.setLong(1, groupId);
            ResultSet rs = stmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("🏯 宗门排行榜 TOP 10\n\n");

            int rank = 0;
            boolean hasAny = false;
            while (rs.next()) {
                hasAny = true;
                rank++;
                String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : rank + ".";
                sb.append(medal).append(" ").append(rs.getString("name"))
                  .append(" | Lv.").append(rs.getInt("level"))
                  .append(" | 👑").append(rs.getString("leader_name"))
                  .append(" | 👥").append(rs.getInt("member_count"))
                  .append(" | 💎").append(rs.getInt("contribution")).append("\n");
            }

            if (!hasAny) sb.append("本群还没有宗门...\n💡 金丹期以上说\"创建宗门 名字\"来创建！");

            return sb.toString();
        } catch (Exception e) {
            log.error("sect_ranking failed", e);
            return "❌ 查询失败...";
        }
    }
}
