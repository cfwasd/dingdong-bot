package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 群投票/决策工具。当群友纠结吃什么、选什么、做决定时使用。
 * 支持多选项随机选择、抽签分组（如分组开黑、组队游戏）。
 */
@Slf4j
@Component
public class DecisionMakerTool {

    /** 常见的纠结选项库 */
    private static final List<String> FOODS = List.of(
            "火锅", "烧烤", "麻辣烫", "螺蛳粉", "兰州拉面", "沙县小吃",
            "黄焖鸡", "肯德基", "麦当劳", "必胜客", "日料", "韩餐",
            "麻辣香锅", "酸菜鱼", "水煮鱼", "烤鱼", "小龙虾",
            "炒饭", "盖浇饭", "饺子", "馄饨", "炸鸡", "汉堡",
            "奶茶+面包", "米线", "凉皮", "关东煮", "串串香"
    );

    private static final List<String> WEEKEND_ACTIVITIES = List.of(
            "看电影", "打游戏", "逛街", "爬山", "在家躺平",
            "约饭", "K歌", "桌游", "密室逃脱", "剧本杀",
            "图书馆看书", "去健身房", "游泳", "打球", "骑行",
            "野外露营", "逛公园", "看展", "喝咖啡", "玩滑板"
    );

    @Tool(
        name = "make_decision",
        description = "帮你做决定/选择困难症终结者。当用户说\"帮我选\"\"选哪个\"\"纠结\"\"吃什么\"\"周末干嘛\"\"帮我做个决定\"\"抽签决定\"时使用。\n" +
                      "支持：\n" +
                      "- options=\"火锅,烧烤,奶茶\" → 从给定选项中随机选一个\n" +
                      "- mode=\"food\" → 从内置美食库随机推荐\n" +
                      "- mode=\"activity\" → 从内置周末活动库随机推荐\n" +
                      "- mode=\"choose\" 或指定选项 → 从多个选项中选一个\n" +
                      "- count=2 → 选出多个选项（如选前3个）"
    )
    public String decide(
        @ToolParam(value = "options", description = "选项列表，用逗号或空格分隔。如\"火锅,烧烤,串串\"。不填则使用内置库按mode推荐。") String options,
        @ToolParam(value = "mode", description = "模式：choose(指定选项)、food(推荐吃的)、activity(推荐活动)", enums = {"choose", "food", "activity"}) String mode,
        @ToolParam(value = "count", description = "要选出的数量（默认1个，最多5个）") Integer count
    ) {
        try {
            String actualMode = (mode == null || mode.isBlank()) ? "choose" : mode.trim().toLowerCase();
            int actualCount = (count == null || count < 1) ? 1 : Math.min(count, 5);

            List<String> resultOptions;

            if (options != null && !options.isBlank()) {
                // 用户指定了选项
                resultOptions = parseOptions(options, actualCount);
            } else {
                // 从内置库选
                resultOptions = switch (actualMode) {
                    case "food" -> pickFromList(FOODS, actualCount);
                    case "activity" -> pickFromList(WEEKEND_ACTIVITIES, actualCount);
                    default -> List.of("你没有给选项让我选啊，用 options 参数给我几个选择吧");
                };
            }

            if (resultOptions.isEmpty()) {
                return "至少给我两个选项呀 😅";
            }

            // 构建结果
            StringBuilder sb = new StringBuilder();
            if (resultOptions.size() == 1) {
                sb.append("🎯 命运的选择是——");
                // 加点仪式感
                int delay = ThreadLocalRandom.current().nextInt(3, 6);
                sb.append("。".repeat(delay));
                sb.append(" **").append(resultOptions.get(0)).append("**！");
                if (FOODS.contains(resultOptions.get(0))) {
                    String[] comments = {"就这个了，安排！", "今天吃这个！", "走起走起！", "冲！"};
                    sb.append(" ").append(comments[ThreadLocalRandom.current().nextInt(comments.length)]);
                }
            } else {
                sb.append("🎯 命运抽选结果（前").append(resultOptions.size()).append("名）：\n");
                for (int i = 0; i < resultOptions.size(); i++) {
                    sb.append("\n").append(i + 1).append(". ").append(resultOptions.get(i));
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Decision maker failed", e);
            return "❌ 决定不了，我选择摆烂 😅";
        }
    }

    /**
     * 解析用户输入的选项，随机选 N 个
     */
    private List<String> parseOptions(String input, int count) {
        // 按逗号或空格分割
        String[] items = input.split("[,，、\\s]+");
        List<String> list = Arrays.stream(items)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (list.size() < 2) {
            return Collections.emptyList();
        }

        return pickFromList(list, Math.min(count, list.size()));
    }

    /**
     * 从列表中随机选出 N 个不重复的项
     */
    private List<String> pickFromList(List<String> source, int count) {
        List<String> copy = new ArrayList<>(source);
        Collections.shuffle(copy, ThreadLocalRandom.current());
        return copy.subList(0, Math.min(count, copy.size()));
    }

    @Tool(
        name = "team_split",
        description = "分组/分队伍。当用户说\"分组\"\"分队伍\"\"分队\"\"组队\"\"分成几组\"\"开黑分组\"时使用，适合游戏组队、团建分组等场景。\n" +
                      "用法：\n" +
                      "- members=\"小明,小红,小刚,小丽\" teams=2 → 将4人分成2组\n" +
                      "- members=\"A,B,C,D,E,F\" teams=3 → 将6人分成3组\n" +
                      "- max_per_team=4 → 每队最多4人（与teams二选一）"
    )
    public String splitTeams(
        @ToolParam(value = "members", description = "成员列表，用逗号或空格分隔，如\"小明,小红,小刚,小丽\"", required = true) String members,
        @ToolParam(value = "teams", description = "要分几组（与max_per_team二选一，默认2组）") Integer teams,
        @ToolParam(value = "max_per_team", description = "每组最多几人（与teams二选一，不填按teams分）") Integer maxPerTeam
    ) {
        if (members == null || members.isBlank()) {
            return "❌ 至少给我几个人名才能分组吧";
        }

        try {
            String[] items = members.split("[,，、\\s]+");
            List<String> people = Arrays.stream(items)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            if (people.size() < 2) {
                return "❌ 至少两个人才能分组哦";
            }

            if (people.size() < 2) {
                return "❌ 至少两个人才能分组哦";
            }

            int numTeams;
            if (maxPerTeam != null && maxPerTeam > 0) {
                numTeams = (int) Math.ceil((double) people.size() / maxPerTeam);
            } else {
                numTeams = (teams != null && teams > 0) ? teams : 2;
            }
            numTeams = Math.min(numTeams, people.size());

            // 打乱
            Collections.shuffle(people, ThreadLocalRandom.current());

            // 分组
            List<List<String>> groups = new ArrayList<>();
            for (int i = 0; i < numTeams; i++) {
                groups.add(new ArrayList<>());
            }
            for (int i = 0; i < people.size(); i++) {
                groups.get(i % numTeams).add(people.get(i));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("🎮 分组结果：\n");
            for (int i = 0; i < groups.size(); i++) {
                sb.append("\n").append(i + 1).append("队（").append(groups.get(i).size()).append("人）：");
                sb.append(String.join("、", groups.get(i)));
            }

            // 分组彩蛋
            if (numTeams <= 2) {
                sb.append("\n\n").append(ThreadLocalRandom.current().nextBoolean() ? "🤝 友谊第一，比赛第二！" : "⚔️ 开战！");
            } else {
                sb.append("\n\n🏆 各就各位，比赛开始！");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Team split failed", e);
            return "❌ 分组失败了";
        }
    }
}