package com.napcat.agent.tool.builtin;

import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 魔力8号球工具。经典占卜玩具，随机回答各种问题。群聊娱乐用。
 */
@Slf4j
@Component
public class EightBallTool {

    private static final List<String> ANSWERS = List.of(
            // 肯定回答
            "毫无疑问，是的！",
            "必须的！",
            "是的，100%确定。",
            "你可以指望这个。",
            "看起来很不错。",
            "是的， definitely！",
            "我掐指一算，是的。",
            "前景一片大好。",
            "这个可以有。",
            "信号显示：可以！",
            // 中性回答
            "嗯...再问一次试试？",
            "我现在不方便回答。",
            "现在预测还太早。",
            "这个嘛...不好说。",
            "你懂的，天机不可泄露。",
            "让我想想...",
            "这个问题太深奥了，我选择沉默。",
            "答案在风中飘荡~",
            " concentrar... concentrar... 信号不好。",
            // 否定回答
            "别做梦了。",
            "我的回答是：不。",
            "醒醒吧，不可能的。",
            "我觉得悬。",
            "前景不太妙。",
            "我劝你还是别想了。",
            "非常不确定。",
            "这个...算了吧。",
            "我建议你还是换个问题吧。",
            "想都别想。"
    );

    @Tool(
        name = "magic_8ball",
        description = "魔力8号球占卜。当用户问\"能不能\"\"会不会\"\"该不该\"等是/否问题，" +
                      "或者说\"帮我占卜\"\"问问8号球\"\"8号球\"时使用。\n" +
                      "用法：传入问题，会随机给出肯定/中性/否定的回答。"
    )
    public String ask(
        @ToolParam(value = "question", description = "要占卜的问题，例如：\"今天会下雨吗\"\"我能抽到SSR吗\"", required = true) String question
    ) {
        if (question == null || question.isBlank()) {
            return "🎱 你得先问个问题呀！";
        }

        try {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            String answer = ANSWERS.get(random.nextInt(ANSWERS.size()));

            // 根据回答类型选 emoji
            String emoji;
            int idx = ANSWERS.indexOf(answer);
            if (idx < 10) {
                emoji = "✅";  // 肯定
            } else if (idx < 20) {
                emoji = "🤔";  // 中性
            } else {
                emoji = "❌";  // 否定
            }

            return "🎱 魔力8号球回答你的问题：\n\n" +
                   "问：" + question.trim() + "\n" +
                   "答：" + emoji + " " + answer;
        } catch (Exception e) {
            log.error("Magic 8ball failed", e);
            return "🎱 8号球信号中断了，等会再问吧...";
        }
    }
}
