package com.napcat.agent.tool.builtin;

import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 石头剪刀布游戏工具。群聊互动娱乐。
 */
@Slf4j
@Component
public class RPSTool {

    private static final String[] CHOICES = {"石头", "剪刀", "布"};
    private static final String[] EMOJIS = {"✊", "✌️", "✋"};

    @Tool(
        name = "rock_paper_scissors",
        description = "石头剪刀布游戏。当用户说\"石头剪刀布\"\"来一局\"\"猜拳\"\"跟我猜拳\"时使用。\n" +
                      "用法：\n" +
                      "- choice=\"石头\" → 你出石头，跟AI猜拳\n" +
                      "- choice=\"剪刀\" → 你出剪刀\n" +
                      "- choice=\"布\" → 你出布\n" +
                      "- 不传 choice 则双方都随机出"
    )
    public String play(
        @ToolParam(value = "choice", description = "你出的手势：石头、剪刀、布（可选，不填则随机）",
                   enums = {"石头", "剪刀", "布"}) String choice
    ) {
        try {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            // AI 随机出
            int aiIdx = random.nextInt(3);

            // 玩家出招
            int playerIdx;
            String playerChoice;
            if (choice != null && !choice.isBlank()) {
                String trimmed = choice.trim();
                playerIdx = parseChoice(trimmed);
                if (playerIdx < 0) {
                    return "❌ 只能出「石头」「剪刀」或「布」哦，别出花招 😂";
                }
                playerChoice = trimmed;
            } else {
                playerIdx = random.nextInt(3);
                playerChoice = CHOICES[playerIdx];
            }

            String aiChoice = CHOICES[aiIdx];

            // 判断胜负：石头(0) > 剪刀(1) > 布(2) > 石头(0)
            // 0赢1, 1赢2, 2赢0 → playerIdx 赢 (playerIdx+1)%3 == aiIdx
            StringBuilder sb = new StringBuilder();
            sb.append("🎮 石头剪刀布！\n\n");
            sb.append("你：").append(EMOJIS[playerIdx]).append(" ").append(playerChoice).append("\n");
            sb.append("我：").append(EMOJIS[aiIdx]).append(" ").append(aiChoice).append("\n\n");

            if (playerIdx == aiIdx) {
                sb.append("🤝 平局！想到一块去了");
                // 随机加一句调侃
                String[] drawTaunts = {
                        "，心有灵犀啊",
                        "，再来一局？",
                        "，默契十足",
                        "，英雄所见略同"
                };
                sb.append(drawTaunts[random.nextInt(drawTaunts.length)]);
            } else if ((playerIdx + 1) % 3 == aiIdx) {
                // 玩家赢了
                sb.append("😤 你赢了...");
                String[] winTaunts = {
                        "这次算你厉害",
                        "别得意，下局我一定赢",
                        "行行行，你牛",
                        "运气好而已哼"
                };
                sb.append(winTaunts[random.nextInt(winTaunts.length)]);
            } else {
                // AI 赢了
                sb.append("😎 我赢了！");
                String[] loseTaunts = {
                        "承让承让~",
                        "哈哈，不服再来！",
                        "姜还是老的辣",
                        "下次加油哦"
                };
                sb.append(loseTaunts[random.nextInt(loseTaunts.length)]);
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("RPS game failed", e);
            return "❌ 游戏出错了，再来一次吧";
        }
    }

    /**
     * 解析玩家出招
     * @return 0=石头, 1=剪刀, 2=布, -1=无效
     */
    private int parseChoice(String choice) {
        for (int i = 0; i < CHOICES.length; i++) {
            if (CHOICES[i].equals(choice)) return i;
        }
        // 支持英文输入
        String lower = choice.toLowerCase();
        if (lower.contains("rock") || lower.contains("r")) return 0;
        if (lower.contains("scissor") || lower.contains("s")) return 1;
        if (lower.contains("paper") || lower.contains("p")) return 2;
        return -1;
    }
}
