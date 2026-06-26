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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static com.dingdong.cultivation.CultivationConstants.*;

@Slf4j
@Component
public class MarriageTool {

    private final DbManager dbManager;
    private volatile boolean tableReady;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public MarriageTool(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    private void ensureTable() {
        if (tableReady) return;
        synchronized (this) {
            if (tableReady) return;
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement cu = conn.prepareStatement(cultivationUsersDdl())) {
                    cu.execute();
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS marriages (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "group_id INTEGER NOT NULL DEFAULT 0," +
                     "user1_id INTEGER NOT NULL," +
                     "user1_name TEXT DEFAULT ''," +
                     "user2_id INTEGER NOT NULL," +
                     "user2_name TEXT DEFAULT ''," +
                     "status TEXT NOT NULL DEFAULT 'pending'," +
                     "proposed_at TEXT NOT NULL DEFAULT ''," +
                     "married_at TEXT DEFAULT ''," +
                     "divorced_at TEXT DEFAULT '')")) {
                stmt.execute();

                try (PreparedStatement idx1 = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_marriages_user1 ON marriages(group_id, user1_id, status)")) {
                    idx1.execute();
                }
                try (PreparedStatement idx2 = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_marriages_user2 ON marriages(group_id, user2_id, status)")) {
                    idx2.execute();
                }
                try (PreparedStatement idx3 = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_marriages_user2name ON marriages(group_id, user2_name, status)")) {
                    idx3.execute();
                }

                tableReady = true;
                } catch (Exception e) {
                    log.error("Failed to create marriages table", e);
                }
            } catch (Exception e) {
                log.error("Failed to create marriages table", e);
            }
        }
    }

    private String now() {
        return LocalDateTime.now().format(ISO_FMT);
    }

    @Tool(
        name = "marry_propose",
        description = "向某人求婚。当用户说\"嫁给我\"\"和我结婚\"\"我要娶\"\"我要嫁给\"\"求婚\"\"结婚\"\"我们结婚吧\"时使用。\n" +
                      "需要提供求婚者ID、目标对象ID和群ID。求婚发出后需要对方同意。"
    )
    public String propose(
        @ToolParam(value = "user_id", description = "求婚者用户ID", required = true) String userIdStr,
        @ToolParam(value = "target_id", description = "被求婚者用户ID（@的那个人）", required = true) String targetIdStr,
        @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "求婚者昵称", required = false) String userName,
        @ToolParam(value = "target_name", description = "被求婚者昵称", required = false) String targetName
    ) {
        long userId, targetId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 求婚者ID格式错误"; }
        try { targetId = Long.parseLong(targetIdStr.trim()); } catch (NumberFormatException e) { return "❌ 目标用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        if (userId == targetId) {
            return "💔 不能和自己结婚啊！自恋也要有限度...";
        }

        ensureTable();
        String today = LocalDate.now().format(DATE_FMT);
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);
        String tName = (targetName != null && !targetName.isBlank()) ? targetName : String.valueOf(targetId);

        try (Connection conn = dbManager.getConnection()) {
            MarriageInfo existing = getActiveMarriage(conn, userId, groupId);
            if (existing != null) {
                return "💔 " + uName + " 你已经和 " + existing.partnerName + " 结婚了！\n想换人？先离婚再说吧~ 说\"离婚\"即可。";
            }

            MarriageInfo targetExisting = getActiveMarriage(conn, targetId, groupId);
            if (targetExisting != null) {
                return "💔 " + tName + " 已经和 " + targetExisting.partnerName + " 结婚了！\n你还是换个目标吧~";
            }

            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM marriages WHERE group_id = ? AND user1_id = ? AND user2_id = ? AND status = 'pending'")) {
                checkStmt.setLong(1, groupId);
                checkStmt.setLong(2, userId);
                checkStmt.setLong(3, targetId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    return "💍 你已经向 " + tName + " 求过婚了！等待对方回应中...\n\n💡 对方说\"我愿意\"即可接受求婚。";
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO marriages (group_id, user1_id, user1_name, user2_id, user2_name, status, proposed_at) " +
                    "VALUES (?, ?, ?, ?, ?, 'pending', ?)")) {
                stmt.setLong(1, groupId);
                stmt.setLong(2, userId);
                stmt.setString(3, uName);
                stmt.setLong(4, targetId);
                stmt.setString(5, tName);
                stmt.setString(6, today);
                stmt.executeUpdate();
            }

            return "💍 " + uName + " 向 " + tName + " 求婚了！💍\n\n" +
                   "━━━━━━━━━━━━━━\n" +
                   "💒 在这个神圣的时刻...\n" +
                   "💕 " + uName + " 深情地对 " + tName + " 说：\n" +
                   "\"愿意和我在一起吗？\"\n" +
                   "━━━━━━━━━━━━━━\n\n" +
                   "💡 " + tName + "，说\"/接受求婚\"来接受求婚！\n" +
                   "⏰ 求婚有效期24小时，过期自动作废。";
        } catch (Exception e) {
            log.error("Propose failed", e);
            return "❌ 求婚失败，民政局系统故障...";
        }
    }

    @Tool(
        name = "marry_accept",
        description = "接受求婚。当用户说\"我愿意\"\"同意结婚\"\"接受求婚\"\"嫁了\"\"娶了\"时使用。\n" +
                      "需要提供接受者用户ID和群ID。"
    )
    public String accept(
        @ToolParam(value = "user_id", description = "接受者用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "接受者昵称", required = false) String userName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        String today = LocalDate.now().format(DATE_FMT);
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            PendingProposal proposal = null;

            // 1. 先通过 user2_id 查（OneBot 渠道，targetId 是真实用户ID）
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, user1_id, user1_name FROM marriages " +
                    "WHERE group_id = ? AND user2_id = ? AND status = 'pending' " +
                    "ORDER BY id DESC LIMIT 1")) {
                stmt.setLong(1, groupId);
                stmt.setLong(2, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    proposal = new PendingProposal(rs.getLong("id"), rs.getLong("user1_id"), rs.getString("user1_name"));
                }
            }

            // 2. 再通过 user2_name 查（QQ 官方渠道后备，user2_id 是名字hash）
            if (proposal == null && uName != null && !uName.isBlank()) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT id, user1_id, user1_name FROM marriages " +
                        "WHERE group_id = ? AND user2_name = ? AND status = 'pending' " +
                        "ORDER BY id DESC LIMIT 1")) {
                    stmt.setLong(1, groupId);
                    stmt.setString(2, uName);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        proposal = new PendingProposal(rs.getLong("id"), rs.getLong("user1_id"), rs.getString("user1_name"));
                    }
                }
            }

            if (proposal == null) {
                return "🤔 " + uName + "，目前没有人向你求婚哦~\n\n💡 想结婚的话，让喜欢的人说\"嫁给我\"或\"和我结婚\"吧！";
            }

            MarriageInfo existing = getActiveMarriage(conn, userId, groupId);
            if (existing != null) {
                return "💔 " + uName + " 你已经和 " + existing.partnerName + " 结婚了！\n先离婚再说吧~";
            }

            MarriageInfo proposerExisting = getActiveMarriage(conn, proposal.user1Id, groupId);
            if (proposerExisting != null) {
                try (PreparedStatement cancelStmt = conn.prepareStatement(
                        "UPDATE marriages SET status = 'cancelled' WHERE id = ?")) {
                    cancelStmt.setLong(1, proposal.id);
                    cancelStmt.executeUpdate();
                }
                return "💔 求婚者 " + proposal.user1Name + " 已经和别人结婚了！\n求婚自动作废...";
            }

            // 更新为已婚状态，同时修正 user2_id（QQ官方渠道下初始是名字hash，接受后更新为真实ID）
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE marriages SET status = 'married', married_at = ?, user2_id = ? WHERE id = ?")) {
                stmt.setString(1, today);
                stmt.setLong(2, userId);
                stmt.setLong(3, proposal.id);
                stmt.executeUpdate();
            }

            return "🎉 恭喜 " + proposal.user1Name + " ❤️ " + uName + " 喜结连理！🎉\n\n" +
                   "━━━━━━━━━━━━━━\n" +
                   "💒 婚礼进行中...\n" +
                   "💐 鲜花、掌声、祝福！\n" +
                   "💍 交换戒指...\n" +
                   "💋 你可以亲吻新娘/新郎了！\n" +
                   "━━━━━━━━━━━━━━\n\n" +
                   "📅 结婚日期：" + today + "\n" +
                   "💕 祝你们百年好合，永结同心！\n\n" +
                   "💡 查看CP状态：说\"我的CP\"\n" +
                   "💔 离婚：说\"离婚\"（三思而后行！）";
        } catch (Exception e) {
            log.error("Accept proposal failed", e);
            return "❌ 接受求婚失败，民政局系统故障...";
        }
    }

    @Tool(
        name = "marry_divorce",
        description = "离婚。当用户说\"离婚\"\"我要离婚\"\"离了吧\"\"不过了\"时使用。\n" +
                      "需要提供用户ID和群ID。离婚后双方恢复单身。"
    )
    public String divorce(
        @ToolParam(value = "user_id", description = "发起离婚的用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "发起者昵称", required = false) String userName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        String today = LocalDate.now().format(DATE_FMT);
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            MarriageInfo existing = getActiveMarriage(conn, userId, groupId);
            if (existing == null) {
                return "🤔 " + uName + " 你还没结婚呢！离什么婚~\n\n💡 想结婚的话，说\"嫁给我\"向心仪的人求婚吧！";
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE marriages SET status = 'divorced', divorced_at = ? WHERE id = ?")) {
                stmt.setString(1, today);
                stmt.setLong(2, existing.id);
                stmt.executeUpdate();
            }

            return "💔 " + uName + " 和 " + existing.partnerName + " 离婚了...\n\n" +
                   "━━━━━━━━━━━━━━\n" +
                   "📅 结婚日期：" + existing.marriedAt + "\n" +
                   "💔 离婚日期：" + today + "\n" +
                   "━━━━━━━━━━━━━━\n\n" +
                   "😢 缘分已尽，各自安好。\n" +
                   "💡 重新开始？说\"嫁给我\"寻找新的缘分吧！";
        } catch (Exception e) {
            log.error("Divorce failed", e);
            return "❌ 离婚失败，民政局系统故障...";
        }
    }

    @Tool(
        name = "marry_cp_status",
        description = "查看CP/婚姻状态。当用户说\"我的CP\"\"CP状态\"\"我和谁结婚了\"\"查CP\"\"看看CP\"时使用。\n" +
                      "如果不提供target_id则查看发起者自己的CP。"
    )
    public String cpStatus(
        @ToolParam(value = "user_id", description = "查看的用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
        @ToolParam(value = "target_id", description = "要查询的目标用户ID（可选，不提供则查自己）", required = false) String targetIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        long queryId = userId;
        if (targetIdStr != null && !targetIdStr.isBlank()) {
            try { queryId = Long.parseLong(targetIdStr.trim()); } catch (NumberFormatException e) { return "❌ 目标用户ID格式错误"; }
        }

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(queryId);
        boolean isSelf = queryId == userId;

        try (Connection conn = dbManager.getConnection()) {
            MarriageInfo info = getActiveMarriage(conn, queryId, groupId);
            if (info == null) {
                if (isSelf) {
                    return "💔 " + uName + " 你还是单身呢~\n\n💡 想脱单？说\"嫁给我\"向心仪的人求婚吧！";
                } else {
                    return "💔 " + uName + " 目前是单身状态~\n\n💡 喜欢TA？说\"嫁给我\"试试！";
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("💕 ").append(uName).append(" 的CP状态\n\n");
            sb.append("━━━━━━━━━━━━━━\n");
            sb.append("💍 伴侣：").append(info.partnerName).append("\n");
            sb.append("📅 结婚日期：").append(info.marriedAt).append("\n");
            sb.append("💒 婚龄：约 ").append(info.daysMarried).append(" 天\n");
            sb.append("💕 状态：").append(info.daysMarried >= 365 ? "💎 钻石婚！" :
                                    info.daysMarried >= 100 ? "👑 百年好合！" :
                                    info.daysMarried >= 30 ? "🌟 蜜月期已过，依然甜蜜" :
                                    "🎉 新婚燕尔！").append("\n");
            sb.append("━━━━━━━━━━━━━━");

            // 修仙联动：道侣加成信息
            try {
                PreparedStatement cuStmt = conn.prepareStatement(
                    "SELECT realm, sub_level FROM cultivation_users WHERE user_id = ? AND group_id = ?");
                cuStmt.setLong(1, queryId);
                cuStmt.setLong(2, groupId);
                ResultSet cuRs = cuStmt.executeQuery();
                if (cuRs.next()) {
                    sb.append("\n\n⚡ 道侣加成：修炼效率+10%（被动持续）");
                    sb.append("\n💡 说\"双修\"与道侣一起修炼（24h冷却）");
                }
            } catch (Exception ignored) {}

            return sb.toString();
        } catch (Exception e) {
            log.error("CP status failed", e);
            return "❌ 查询失败，民政局系统故障...";
        }
    }

    @Tool(
        name = "marry_stats",
        description = "查看本群婚姻统计。当用户说\"婚姻统计\"\"结婚排行榜\"\"本群CP\"\"群内CP\"时使用。"
    )
    public String stats(
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr
    ) {
        long groupId;
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        try (Connection conn = dbManager.getConnection()) {
            int totalMarriages = 0, activeMarriages = 0, totalDivorces = 0;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT status, COUNT(*) as cnt FROM marriages WHERE group_id = ? GROUP BY status")) {
                stmt.setLong(1, groupId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String status = rs.getString("status");
                    int cnt = rs.getInt("cnt");
                    switch (status) {
                        case "married" -> activeMarriages = cnt;
                        case "divorced" -> totalDivorces = cnt;
                    }
                    totalMarriages += cnt;
                }
            }

            if (totalMarriages == 0) {
                return "💔 本群还没有人结过婚...\n\n💡 说\"嫁给我\"来创造第一对CP吧！";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("💒 本群婚姻统计\n\n");
            sb.append("💍 当前CP：").append(activeMarriages).append(" 对\n");
            sb.append("💔 累计离婚：").append(totalDivorces).append(" 次\n");

            double divorceRate = totalMarriages > 0 ? (double) totalDivorces / (activeMarriages + totalDivorces) * 100 : 0;
            sb.append("📊 离婚率：").append(String.format("%.1f", divorceRate)).append("%\n");

            if (divorceRate > 50) {
                sb.append("\n😱 离婚率偏高，大家且行且珍惜！");
            } else if (divorceRate < 10 && activeMarriages > 0) {
                sb.append("\n💕 本群婚姻幸福指数极高！");
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT user1_name, user2_name, married_at FROM marriages " +
                    "WHERE group_id = ? AND status = 'married' ORDER BY married_at DESC LIMIT 5")) {
                stmt.setLong(1, groupId);
                ResultSet rs = stmt.executeQuery();
                boolean hasAny = false;
                while (rs.next()) {
                    if (!hasAny) {
                        sb.append("\n\n💕 最近的CP：\n");
                        hasAny = true;
                    }
                    sb.append("• ").append(rs.getString("user1_name"))
                      .append(" ❤️ ").append(rs.getString("user2_name"))
                      .append("（").append(rs.getString("married_at")).append("）\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Marriage stats failed", e);
            return "❌ 统计失败，民政局系统故障...";
        }
    }

    @Tool(
        name = "dual_cultivate",
        description = "道侣双修。当用户说\"双修\"\"道侣双修\"\"一起修炼\"时使用。24h冷却，双方获得修为。"
    )
    public String dualCultivate(
        @ToolParam(value = "user_id", description = "发起者用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "发起者昵称", required = false) String userName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            MarriageInfo info = getActiveMarriage(conn, userId, groupId);
            if (info == null) {
                return "🤔 " + uName + " 你还没有道侣！先结婚再说\"双修\"吧~";
            }

            long partnerId = info.partnerId;
            String partnerName = info.partnerName;

            boolean selfCultivator = false, partnerCultivator = false;
            int selfRoot = 10, partnerRoot = 10;
            int selfRealmIdx = 0, partnerRealmIdx = 0;
            int selfSubLevel = 1, partnerSubLevel = 1;
            String selfDualTime = null, partnerDualTime = null;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT root_bone, realm, sub_level, last_dual_cultivate_time FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                selfCultivator = rs.next();
                if (selfCultivator) {
                    selfRoot = rs.getInt("root_bone");
                    selfRealmIdx = getRealmIndex(rs.getString("realm"));
                    selfSubLevel = rs.getInt("sub_level");
                    selfDualTime = rs.getString("last_dual_cultivate_time");
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT root_bone, realm, sub_level, last_dual_cultivate_time FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, partnerId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                partnerCultivator = rs.next();
                if (partnerCultivator) {
                    partnerRoot = rs.getInt("root_bone");
                    partnerRealmIdx = getRealmIndex(rs.getString("realm"));
                    partnerSubLevel = rs.getInt("sub_level");
                    partnerDualTime = rs.getString("last_dual_cultivate_time");
                }
            }

            if (!selfCultivator || !partnerCultivator) {
                StringBuilder sb = new StringBuilder("🤔 双修需要双方都是修仙者！\n");
                if (!selfCultivator) sb.append("• ").append(uName).append(" 尚未开启修仙\n");
                if (!partnerCultivator) sb.append("• ").append(partnerName).append(" 尚未开启修仙\n");
                sb.append("💡 说\"修仙\"开启修仙之路！");
                return sb.toString();
            }

            // CD检查：任一方未冷却则阻止
            long remainMinutes = 0;
            for (String dt : new String[]{selfDualTime, partnerDualTime}) {
                if (dt != null && !dt.isEmpty()) {
                    try {
                        LocalDateTime lastTime = LocalDateTime.parse(dt, ISO_FMT);
                        long mins = ChronoUnit.MINUTES.between(lastTime, LocalDateTime.now());
                        long remain = 1440 - mins; // 24h = 1440min
                        if (remain > 0 && remain > remainMinutes) remainMinutes = remain;
                    } catch (Exception ignored) {}
                }
            }
            if (remainMinutes > 0) {
                long hours = remainMinutes / 60;
                long mins = remainMinutes % 60;
                String remainStr = hours > 0 ? hours + "小时" + mins + "分钟" : mins + "分钟";
                return "⏳ 双修冷却中...还需等待 " + remainStr + "\n💡 双修CD为24小时，耐心等待吧~";
            }

            int avgRoot = (selfRoot + partnerRoot) / 2;
            int totalSubLevels = (selfRealmIdx * 4 + selfSubLevel) + (partnerRealmIdx * 4 + partnerSubLevel);
            int gain = avgRoot * 3 + totalSubLevels * 2;

            String dualTime = now();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE cultivation_users SET cultivation = cultivation + ?, last_dual_cultivate_time = ? WHERE user_id = ? AND group_id = ?")) {
                stmt.setInt(1, gain);
                stmt.setString(2, dualTime);
                stmt.setLong(3, userId);
                stmt.setLong(4, groupId);
                stmt.executeUpdate();
                stmt.setInt(1, gain);
                stmt.setString(2, dualTime);
                stmt.setLong(3, partnerId);
                stmt.setLong(4, groupId);
                stmt.executeUpdate();
            }

            return "💕 " + uName + " 与道侣 " + partnerName + " 双修！\n\n"
                + "━━━━━━━━━━━━━━\n"
                + "🌸 灵力交融，心神相通...\n"
                + "💎 双方各获得 +" + gain + " 修为\n"
                + "🦴 根骨均值：" + avgRoot + "\n"
                + "📊 境界层数和：" + totalSubLevels + "\n"
                + "━━━━━━━━━━━━━━\n\n"
                + "⏰ 双修冷却：24小时";
        } catch (Exception e) {
            log.error("dual_cultivate failed", e);
            return "❌ 双修失败，灵力紊乱...";
        }
    }

    // ========= 内部 =========

    private record PendingProposal(long id, long user1Id, String user1Name) {}

    private record MarriageInfo(long id, String partnerName, long partnerId, String marriedAt, long daysMarried) {}

    private MarriageInfo getActiveMarriage(Connection conn, long userId, long groupId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, user1_id, user1_name, user2_id, user2_name, married_at FROM marriages " +
                "WHERE group_id = ? AND status = 'married' AND (user1_id = ? OR user2_id = ?)")) {
            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);
            stmt.setLong(3, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                long id = rs.getLong("id");
                long u1 = rs.getLong("user1_id");
                long u2 = rs.getLong("user2_id");
                String partnerName = (u1 == userId) ? rs.getString("user2_name") : rs.getString("user1_name");
                long partnerId = (u1 == userId) ? u2 : u1;
                String marriedAt = rs.getString("married_at");
                long daysMarried = 0;
                if (marriedAt != null && !marriedAt.isEmpty()) {
                    try {
                        daysMarried = LocalDate.now().toEpochDay() - LocalDate.parse(marriedAt).toEpochDay();
                    } catch (Exception ignored) {}
                }
                return new MarriageInfo(id, partnerName, partnerId, marriedAt, daysMarried);
            }
        }
        return null;
    }
}
