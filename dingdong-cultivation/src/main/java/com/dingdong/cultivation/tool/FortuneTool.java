package com.dingdong.cultivation.tool;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class FortuneTool {

    private static final String[] LEVELS = {"大吉", "吉", "中吉", "小吉", "末吉", "凶", "大凶"};
    private static final int[] LEVEL_WEIGHTS = {10, 20, 15, 20, 15, 12, 8};

    private static final List<String> GOOD_THINGS = List.of(
            "适合摸鱼", "适合学习", "适合社交", "适合运动", "适合吃火锅",
            "适合打游戏", "适合早睡", "适合出门", "适合购物", "适合写代码",
            "适合听音乐", "适合看电影", "适合整理房间", "适合约朋友", "适合喝奶茶",
            "适合散步", "适合看书", "适合做饭", "适合拍照", "适合发呆"
    );

    private static final List<String> BAD_THINGS = List.of(
            "不宜加班", "不宜做重大决定", "不宜借钱", "不宜和老板顶嘴",
            "不宜熬夜", "不宜暴饮暴食", "不宜冲动消费", "不宜迟到",
            "不宜在群里发言", "不宜立flag", "不宜Flag", "不宜跟人吵架",
            "不宜买股票", "不宜吃辣", "不宜早起"
    );

    private static final List<String> LUCKY_COLORS = List.of(
            "🔴 红色", "🟠 橙色", "🟡 黄色", "🟢 绿色", "🔵 蓝色",
            "🟣 紫色", "⚫ 黑色", "⚪ 白色", "🟤 棕色", "🩷 粉色"
    );

    private static final List<String> LUCKY_FOODS = List.of(
            "火锅", "烧烤", "麻辣烫", "寿司", "披萨", "汉堡", "拉面",
            "奶茶", "咖啡", "水果沙拉", "炸鸡", "饺子", "蛋糕", "冰淇淋",
            "螺蛳粉", "麻辣烫", "煎饼果子", "黄焖鸡", "酸菜鱼"
    );

    private static final List<String> ADVICES = List.of(
            "今天适合低调行事，闷声发大财",
            "出门记得带伞，以防万一",
            "今天运气不错，可以试试一直想做的事",
            "注意身体，别太累了",
            "多喝水，少看手机",
            "今天适合跟老朋友联系一下",
            "保持微笑，好运自然来",
            "别想太多，开心就好",
            "今天适合给自己放个假",
            "遇事不要慌，先发个表情包",
            "今天适合存钱，不适合花钱",
            "小心脚下的路，别摔了",
            "今天适合学点新东西",
            "早点睡觉，明天又是新的一天"
    );

    @Tool(
        name = "daily_fortune",
        description = "查询今日运势/每日运势/抽签。当用户说\"今天运势怎样\"\"帮我抽签\"\"今日运势\"\"运气如何\"时使用。\n" +
                      "可以指定用户标识来为特定人生成运势，不指定则随机生成。"
    )
    public String fortune(
        @ToolParam(value = "name", description = "要查运势的人的名字或昵称（可选），不填则随机生成") String name
    ) {
        try {
            LocalDate today = LocalDate.now();
            String seed = name != null && !name.isBlank() ? name : "random_" + today;

            long seedValue = (today.toEpochDay() * 31 + seed.hashCode()) & 0x7FFFFFFF;
            int hash = (int) (seedValue % 10000);

            int levelIdx = weightedRandom(hash);
            String level = LEVELS[levelIdx];

            int score = Math.min(100, Math.max(1, (hash * 7 + levelIdx * 13) % 100 + 1));

            String good1 = GOOD_THINGS.get((hash + today.getDayOfMonth()) % GOOD_THINGS.size());
            String good2 = GOOD_THINGS.get((hash * 3 + today.getDayOfMonth() + 7) % GOOD_THINGS.size());
            String bad1 = BAD_THINGS.get((hash + today.getDayOfMonth() + 3) % BAD_THINGS.size());
            String bad2 = BAD_THINGS.get((hash * 2 + today.getDayOfMonth() + 5) % BAD_THINGS.size());

            String luckyColor = LUCKY_COLORS.get((hash + 1) % LUCKY_COLORS.size());
            String luckyFood = LUCKY_FOODS.get((hash + 2) % LUCKY_FOODS.size());

            String advice = ADVICES.get((hash + today.getDayOfMonth()) % ADVICES.size());

            String displayName = (name != null && !name.isBlank()) ? name : "你";
            StringBuilder sb = new StringBuilder();
            sb.append("🔮 ").append(displayName).append(" 的今日运势\n");
            sb.append("📅 ").append(today).append("\n");
            sb.append("━━━━━━━━━━━━━━\n");
            sb.append("运势：").append(level).append(" ").append(getLevelEmoji(level)).append("\n");
            sb.append("指数：").append(getScoreBar(score)).append(" ").append(score).append("/100\n");
            sb.append("━━━━━━━━━━━━━━\n");
            sb.append("✅ 宜：").append(good1).append("、").append(good2).append("\n");
            sb.append("❌ 忌：").append(bad1).append("、").append(bad2).append("\n");
            sb.append("🎨 幸运色：").append(luckyColor).append("\n");
            sb.append("🍽️ 幸运食物：").append(luckyFood).append("\n");
            sb.append("━━━━━━━━━━━━━━\n");
            sb.append("💬 ").append(advice);

            return sb.toString();
        } catch (Exception e) {
            log.error("Fortune failed", e);
            return "🔮 运势查询失败，可能是今天太特殊了...";
        }
    }

    private int weightedRandom(int hash) {
        int val = Math.abs(hash) % 100;
        int cumulative = 0;
        for (int i = 0; i < LEVEL_WEIGHTS.length; i++) {
            cumulative += LEVEL_WEIGHTS[i];
            if (val < cumulative) return i;
        }
        return 0;
    }

    private String getLevelEmoji(String level) {
        return switch (level) {
            case "大吉" -> "🎉🎊";
            case "吉" -> "😊";
            case "中吉" -> "😄";
            case "小吉" -> "🙂";
            case "末吉" -> "😐";
            case "凶" -> "😟";
            case "大凶" -> "😱";
            default -> "";
        };
    }

    private String getScoreBar(int score) {
        int filled = score / 10;
        int empty = 10 - filled;
        return "▓".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
    }
}
