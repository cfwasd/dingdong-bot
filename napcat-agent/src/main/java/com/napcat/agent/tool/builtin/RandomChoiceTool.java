package com.napcat.agent.tool.builtin;

import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机决策工具。支持抽签、掷骰子、随机数、从选项中随机挑选。
 * 群聊互动常用。
 */
@Slf4j
@Component
public class RandomChoiceTool {

    @Tool(
        name = "random_choice",
        description = "从多个选项中随机选择一个，或者掷骰子、生成随机数。" +
                      "当用户说\"帮我选一个\"\"掷骰子\"\"抽签\"\"随机挑一个\"时使用。\n" +
                      "用法示例：\n" +
                      "- options=\"火锅,烧烤,麻辣烫\" → 从三个中随机选一个\n" +
                      "- mode=\"dice\" → 掷一个六面骰子\n" +
                      "- mode=\"coin\" → 抛硬币\n" +
                      "- mode=\"number\", min=1, max=100 → 生成1到100的随机数"
    )
    public String randomChoice(
        @ToolParam(value = "mode", description = "模式：choice(从选项中随机，默认)、dice(掷骰子)、coin(抛硬币)、number(随机数)",
                   enums = {"choice", "dice", "coin", "number"}) String mode,
        @ToolParam(value = "options", description = "选项列表，用逗号分隔。如\"火锅,烧烤,麻辣烫\"。仅 mode=choice 时有效") String options,
        @ToolParam(value = "min", description = "随机数最小值（含），默认1。仅 mode=number 时有效") Integer min,
        @ToolParam(value = "max", description = "随机数最大值（含），默认100。仅 mode=number 时有效") Integer max
    ) {
        String actualMode = (mode == null || mode.isBlank()) ? "choice" : mode.toLowerCase().trim();

        try {
            return switch (actualMode) {
                case "dice" -> rollDice();
                case "coin" -> flipCoin();
                case "number" -> randomNumber(
                        min != null ? min : 1,
                        max != null ? max : 100
                );
                default -> pickFromOptions(options);
            };
        } catch (Exception e) {
            log.error("Random choice failed, mode={}", actualMode, e);
            return "出错了：" + e.getMessage();
        }
    }

    private String pickFromOptions(String options) {
        if (options == null || options.isBlank()) {
            return "❌ 请提供选项，用逗号分隔。比如：火锅,烧烤,麻辣烫";
        }

        // 支持中英文逗号、顿号分隔
        String[] parts = options.split("[,，、;；]+");
        List<String> items = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }

        if (items.isEmpty()) {
            return "❌ 选项为空，请提供至少一个选项。";
        }
        if (items.size() == 1) {
            return "就一个选项，那还选啥...就是 **" + items.get(0) + "** 呗 😂";
        }

        int idx = ThreadLocalRandom.current().nextInt(items.size());
        return "🎯 从 " + items.size() + " 个选项中随机选中：**" + items.get(idx) + "**";
    }

    private String rollDice() {
        int result = ThreadLocalRandom.current().nextInt(1, 7);
        String[] diceFaces = {"", "⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
        return "🎲 掷出了 " + diceFaces[result] + " **" + result + "** 点";
    }

    private String flipCoin() {
        boolean heads = ThreadLocalRandom.current().nextBoolean();
        return "🪙 " + (heads ? "正面！" : "反面！");
    }

    private String randomNumber(int min, int max) {
        if (min > max) {
            int tmp = min; min = max; max = tmp;
        }
        if (min == max) {
            return "🔢 就一个数...那不就是 **" + min + "** 嘛";
        }
        int result = ThreadLocalRandom.current().nextInt(min, max + 1);
        return "🔢 " + min + "~" + max + " 之间随机 → **" + result + "**";
    }
}
