package com.dingdong.cultivation.tool;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import com.dingdong.core.scheduler.DbManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class PillShopTool {

    private final DbManager dbManager;

    private static final Map<String, PillInfo> PILLS = new LinkedHashMap<>();
    static {
        PILLS.put("培元丹", new PillInfo("培元丹", 50, "修为+小", "服用后获得根骨×境界系数×3的修为"));
        PILLS.put("筑基丹", new PillInfo("筑基丹", 200, "修为+中", "服用后获得根骨×境界系数×8的修为"));
        PILLS.put("渡劫丹", new PillInfo("渡劫丹", 300, "渡劫辅助", "渡劫时临时气运+20%（单次渡劫有效）"));
        PILLS.put("疗伤丹", new PillInfo("疗伤丹", 150, "疗伤", "重伤立即恢复"));
        PILLS.put("还魂丹", new PillInfo("还魂丹", 1000, "免死", "渡劫失败免转世（仅重伤掉境）"));
    }

    public PillShopTool(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    @Tool(name = "pill_shop", description = "查看丹药商店。当用户说\"丹药商店\"\"有什么丹药\"\"买丹药\"时使用。")
    public String pillShop() {
        StringBuilder sb = new StringBuilder();
        sb.append("💊 丹药商店\n\n");
        sb.append("━━━━━━━━━━━━━━\n");
        for (PillInfo pill : PILLS.values()) {
            sb.append("• ").append(pill.name).append(" — ").append(pill.price).append("灵石\n");
            sb.append("  ").append(pill.description).append("\n\n");
        }
        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("💡 购买方式：说\"购买 丹药名\"\n");
        sb.append("💡 例如：\"购买 培元丹\"");
        return sb.toString();
    }

    @Tool(name = "buy_pill", description = "购买丹药。当用户说\"购买 丹药名\"\"买丹药\"时使用。")
    public String buyPill(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName,
        @ToolParam(value = "pill_name", description = "丹药名称", required = true) String pillName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        PillInfo pill = PILLS.get(pillName);
        if (pill == null) {
            StringBuilder hint = new StringBuilder("❌ 没有「" + pillName + "」这种丹药！\n\n可选丹药：\n");
            for (String name : PILLS.keySet()) hint.append("• ").append(name).append("\n");
            return hint.toString();
        }

        try (Connection conn = dbManager.getConnection()) {
            int realmIdx = 0, rootBone = 10;
            String realm = "mortal";
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT realm, root_bone FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "🤔 " + uName + " 你还没开启修仙之路！说\"修仙\"开始吧~";
                realm = rs.getString("realm");
                rootBone = rs.getInt("root_bone");
                realmIdx = getRealmIndex(realm);
            }

            int stones;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT spirit_stones FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                stones = rs.next() ? rs.getInt("spirit_stones") : 0;
            }

            if (stones < pill.price) {
                return "❌ 灵石不足！\n丹药价格：" + pill.price + " 灵石\n你的灵石：" + stones;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE cultivation_users SET spirit_stones = spirit_stones - ? WHERE user_id = ? AND group_id = ?")) {
                stmt.setInt(1, pill.price);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }

            String effect;
            double[] coeffs = {1.0, 1.2, 1.5, 1.8, 2.2, 2.6, 3.0, 3.5, 4.0};
            switch (pill.name) {
                case "培元丹" -> {
                    int gain = (int) (rootBone * coeffs[realmIdx] * 3);
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE cultivation_users SET cultivation = cultivation + ? WHERE user_id = ? AND group_id = ?")) {
                        stmt.setInt(1, gain);
                        stmt.setLong(2, userId);
                        stmt.setLong(3, groupId);
                        stmt.executeUpdate();
                    }
                    effect = "修为+" + gain;
                }
                case "筑基丹" -> {
                    int gain = (int) (rootBone * coeffs[realmIdx] * 8);
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE cultivation_users SET cultivation = cultivation + ? WHERE user_id = ? AND group_id = ?")) {
                        stmt.setInt(1, gain);
                        stmt.setLong(2, userId);
                        stmt.setLong(3, groupId);
                        stmt.executeUpdate();
                    }
                    effect = "修为+" + gain;
                }
                case "渡劫丹" -> effect = "渡劫时临时气运+20%（在下次渡劫时自动生效）";
                case "疗伤丹" -> {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE cultivation_users SET is_injured = 0, injury_until = '' WHERE user_id = ? AND group_id = ?")) {
                        stmt.setLong(1, userId);
                        stmt.setLong(2, groupId);
                        stmt.executeUpdate();
                    }
                    effect = "重伤已恢复！";
                }
                case "还魂丹" -> effect = "渡劫失败时自动触发，免转世（仅重伤掉境）";
                default -> effect = "丹药已使用";
            }

            return "💊 " + uName + " 购买了「" + pill.name + "」！\n"
                + "💎 花费：" + pill.price + " 灵石\n"
                + "✨ 效果：" + effect + "\n"
                + "💰 剩余灵石：" + (stones - pill.price);
        } catch (Exception e) {
            log.error("buy_pill failed", e);
            return "❌ 购买失败，丹炉炸了...";
        }
    }

    private int getRealmIndex(String realm) {
        String[] realms = {"mortal", "lianqi", "zhuji", "jindan", "yuanying", "huashen", "dujie", "dacheng", "zhenxian"};
        for (int i = 0; i < realms.length; i++) {
            if (realms[i].equals(realm)) return i;
        }
        return 0;
    }

    private record PillInfo(String name, int price, String type, String description) {}
}
