package com.napcat.agent.tool.builtin;

import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 真心话大冒险工具。群聊互动娱乐。
 */
@Slf4j
@Component
public class TruthOrDareTool {

    private static final List<String> TRUTHS = List.of(
            "你最近一次撒谎是什么时候？说了什么？",
            "你手机里最不想让人看到的聊天记录是什么？",
            "你有没有偷偷喜欢过在座的某个人？",
            "你做过最丢脸的事是什么？",
            "你最后悔的一件事是什么？",
            "你偷偷做过什么对不起朋友的事？",
            "你最大的秘密是什么？",
            "你最讨厌在座某个人的什么行为？",
            "你暗恋过几个人？分别是什么类型的？",
            "你撒过最大的谎是什么？",
            "你最害怕的事情是什么？",
            "你偷偷哭过最多次是因为什么？",
            "如果可以变成异性一天，你最想做什么？",
            "你做过最离谱的白日梦是什么？",
            "你朋友圈/QQ空间里最不想让人看到的一条是什么？",
            "你最想对在座某个人说什么但一直没说出口？",
            "你最近一次熬夜到几点？在干什么？",
            "你有没有假装看懂一个其实完全不懂的东西？",
            "你最想删掉的手机APP是哪个？为什么？",
            "你偷偷关注过某个人的社交媒体多久了？",
            "你觉得自己最大的缺点是什么？",
            "你最近一次社死现场是什么？",
            "你有没有假装收到消息没看到来逃避社交？",
            "你最想对小时候的自己说什么？",
            "你手机搜索记录里最不想让人知道的是什么？"
    );

    private static final List<String> DARES = List.of(
            "给最近聊天的人发一句\"我想你了\"",
            "用方言唱一首歌的副歌部分",
            "发一张你最近的自拍到群里",
            "模仿一个表情包发到群里",
            "给通讯录第10个人发\"在吗\"",
            "用撒娇的语气说\"人家好喜欢你哦\"",
            "发一段10秒的语音，内容是大声喊\"我是猪\"",
            "把你微信/QQ签名改成\"我是笨蛋\"保持10分钟",
            "在群里发一条消息：\"有没有人想请我吃饭\"",
            "用左手写自己的名字发出来",
            "表演一个才艺（唱歌/跳舞/讲笑话都行）",
            "给通讯录随机一个人发\"我们做朋友吧\"",
            "发一张你手机里最丑的自拍",
            "用机器人/AI的语气说一段话，持续30秒",
            "在群里@一个人说\"你今天的发型真好看\"",
            "发一段你模仿明星说话的语音",
            "把手机相册第5张照片发出来",
            "用唱歌的方式说出你今天的感受",
            "在群里发一条消息：\"其实我是外星人\"",
            "给最近通话的人发一条\"我爱你\"",
            "倒着说出自己的名字",
            "模仿群主/管理员说一句话",
            "发一段你跳广场舞的视频/动图描述",
            "用三种不同的语气说\"我喜欢你\"（温柔/愤怒/害羞）",
            "闭着眼打一段话发到群里（不能看屏幕）"
    );

    @Tool(
        name = "truth_or_dare",
        description = "真心话大冒险。当用户说\"真心话大冒险\"\"真心话\"\"大冒险\"\"来一局真心话\"时使用。\n" +
                      "用法：\n" +
                      "- mode=\"random\" → 随机出真心话或大冒险（默认）\n" +
                      "- mode=\"truth\" → 只出真心话\n" +
                      "- mode=\"dare\" → 只出大冒险"
    )
    public String play(
        @ToolParam(value = "mode", description = "模式：random(随机)、truth(真心话)、dare(大冒险)",
                   enums = {"random", "truth", "dare"}) String mode
    ) {
        String actualMode = (mode == null || mode.isBlank()) ? "random" : mode.toLowerCase().trim();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        try {
            String question;
            String emoji;

            switch (actualMode) {
                case "truth" -> {
                    question = TRUTHS.get(random.nextInt(TRUTHS.size()));
                    emoji = "💬";
                    return emoji + " 【真心话】\n\n" + question;
                }
                case "dare" -> {
                    question = DARES.get(random.nextInt(DARES.size()));
                    emoji = "🔥";
                    return emoji + " 【大冒险】\n\n" + question;
                }
                default -> {
                    // random: 先随机选真心话还是大冒险
                    if (random.nextBoolean()) {
                        question = TRUTHS.get(random.nextInt(TRUTHS.size()));
                        return "🎲 命运选择了... 【真心话】！\n\n" + question;
                    } else {
                        question = DARES.get(random.nextInt(DARES.size()));
                        return "🎲 命运选择了... 【大冒险】！\n\n" + question;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Truth or dare failed", e);
            return "❌ 游戏出错，再来一次吧";
        }
    }
}
