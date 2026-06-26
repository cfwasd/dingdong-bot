package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 掷骰子工具。支持多种骰子类型（d6/d20/d100等），跑团/桌游常用。
 */
@Slf4j
@Component
public class DiceTool {

    @Tool(
        name = "roll_dice",
        description = "掷骰子。当用户说\"掷骰子\"\"扔骰子\"\"roll\"\"投骰子\"\"扔个d20\"\"掷3个骰子\"时使用。\n" +
                      "支持多种骰子：d4、d6、d8、d10、d12、d20、d100。\n" +
                      "示例：\n" +
                      "- dice=\"d6\" → 掷1个6面骰\n" +
                      "- dice=\"d20\" → 掷1个20面骰\n" +
                      "- dice=\"3d6\" → 掷3个6面骰，返回每个结果和总和\n" +
                      "- dice=\"2d20+5\" → 掷2个20面骰，每个结果+5"
    )
    public String roll(
        @ToolParam(value = "dice", description = "骰子表达式，格式：[数量]d[面数][+/-修正值]，如 d6、3d6、2d20+5", required = true) String dice
    ) {
        if (dice == null || dice.isBlank()) {
            dice = "d6";
        }

        try {
            String expr = dice.trim().toLowerCase().replaceAll("\\s+", "");

            // 解析表达式：[count]d[sides][+/-modifier]
            int count = 1;
            int modifier = 0;
            int sides;

            String mainPart = expr;
            // 提取修正值
            int plusIdx = mainPart.lastIndexOf('+');
            int minusIdx = mainPart.lastIndexOf('-');
            int modIdx = Math.max(plusIdx, minusIdx);
            if (modIdx > 0 && modIdx > mainPart.indexOf('d')) {
                String modStr = mainPart.substring(modIdx);
                modifier = Integer.parseInt(modStr);
                mainPart = mainPart.substring(0, modIdx);
            }

            // 提取数量和面数
            int dIdx = mainPart.indexOf('d');
            if (dIdx < 0) {
                return "❌ 骰子格式错误，正确格式如：d6、3d6、2d20+5";
            }

            String countStr = mainPart.substring(0, dIdx);
            String sidesStr = mainPart.substring(dIdx + 1);

            if (!countStr.isEmpty()) {
                count = Integer.parseInt(countStr);
            }
            if (count < 1 || count > 20) {
                return "❌ 骰子数量只能在 1~20 之间";
            }

            sides = Integer.parseInt(sidesStr);
            if (sides < 2 || sides > 1000) {
                return "❌ 骰子面数只能在 2~1000 之间";
            }

            // 掷骰子
            ThreadLocalRandom random = ThreadLocalRandom.current();
            List<Integer> results = new ArrayList<>();
            int total = 0;
            for (int i = 0; i < count; i++) {
                int r = random.nextInt(1, sides + 1);
                results.add(r);
                total += r;
            }
            total += modifier;

            // 构建输出
            StringBuilder sb = new StringBuilder();
            sb.append("🎲 掷骰子结果：\n\n");
            sb.append("骰子：").append(count).append("d").append(sides);
            if (modifier != 0) {
                sb.append(modifier > 0 ? "+" : "").append(modifier);
            }
            sb.append("\n");

            if (count > 1) {
                sb.append("每个：").append(results).append("\n");
            }
            if (modifier != 0 && count > 1) {
                int rawTotal = results.stream().mapToInt(Integer::intValue).sum();
                sb.append("原始总和：").append(rawTotal).append("\n");
                sb.append("修正值：").append(modifier > 0 ? "+" : "").append(modifier).append("\n");
            }
            sb.append("最终结果：").append(total);

            // 特殊骰子彩蛋
            if (sides == 20 && count == 1) {
                if (total - modifier == 20) {
                    sb.append("\n\n🌟 大成功！暴击！");
                } else if (total - modifier == 1) {
                    sb.append("\n\n💀 大失败！惨案！");
                }
            }

            return sb.toString();
        } catch (NumberFormatException e) {
            return "❌ 骰子格式错误，正确格式如：d6、3d6、2d20+5";
        } catch (Exception e) {
            log.error("Dice roll failed: {}", dice, e);
            return "❌ 骰子扔飞了，再来一次吧...";
        }
    }
}
