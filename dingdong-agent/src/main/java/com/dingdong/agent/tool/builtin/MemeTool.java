package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 表情包/梗图推荐工具。群聊中提供有趣的表情包描述、梗、语录。
 * 注意：返回的是文字描述（表情包标题/梗内容/段子），不是真实图片。
 */
@Slf4j
@Component
public class MemeTool {

    /** 当前网络热梗（定期更新） */
    private static final List<String> HOT_MEMES = List.of(
            "完了，芭比Q了",
            "家人们谁懂啊",
            "泰酷辣！",
            "你个老六",
            "尊嘟假嘟",
            "遥遥领先",
            "主打一个...",
            "绝绝子",
            "我真的会谢",
            "栓Q",
            "666",
            "破防了",
            "上头了",
            "优雅，太优雅了",
            "格局打开",
            "你是懂...的",
            "CPU都要烧了",
            "已老实，求放过",
            "这波在大气层",
            "不能说是毫无关系，只能说是一模一样",
            "在吗？在吗？在吗？重要的事说三遍",
            "笑不活了家人们",
            "emmm...怎么不算呢",
            "你礼貌吗？",
            "这就是传说中的...吗",
            "属于是...了",
            "嗨害嗨！",
            "显眼包",
            "偷感很重",
            "浓人一个",
            "癫公颠婆",
            "硬控我X秒",
            "水灵灵地...",
            "包赢的吗？",
            "那咋了？"
    );

    /** 经典语录/梗 */
    private static final List<String> CLASSIC_QUOTES = List.of(
            "做人嘛，最重要的是开心。",
            "我全都要！",
            "小孩子才做选择，成年人我全都要。",
            "不要问我为什么，问就是任性。",
            "钞能力是比较厉害的能力。",
            "只要我不努力，老板就永远过不上他想要的生活。",
            "上班摸鱼，下班钓鱼。",
            "别卷了，再卷成寿司了。",
            "我也想低调，可是实力不允许啊。",
            "别问我有没有对象，我现在只想搞钱。",
            "在上班和上进之间，我选择了上香。",
            "本来想给你看看新学的技能，但想想还是算了，怕你自卑。",
            "人生就像打电话，不是你先挂就是我先挂。",
            "爱情不是你想买，想买就能卖。",
            "难瘦，想哭。",
            "打工是发不了财的，想发财就要...做梦。",
            "我这人没啥优点，就是有点不要脸。",
            "三分天注定，七分靠打拼，剩下九十分靠脸。",
            "天气这么热，我想跟冰淇淋私奔。",
            "穷不可怕，可怕的是穷的人是我。"
    );

    /** 怼人/调侃语录（不会太毒，适合群友互怼） */
    private static final List<String> ROASTS = List.of(
            "你怕不是个天才（反向意思）",
            "行行行，你说得都对（敷衍.jpg）",
            "你是对的，所以呢？",
            "听君一席话，如听一席话。",
            "我竟无言以对...被你整不会了",
            "但凡你有点逻辑也不至于一点逻辑没有",
            "这很难评，祝你好运吧",
            "你开心就好",
            "啊对对对",
            "这破天的富贵终于轮到你了（嘲讽限定版）",
            "你搁这儿搁这儿呢",
            "废话文学算是被你玩明白了",
            "别说了，我脑子要长脑子了",
            "要不你去当个段子手？我觉得很有前途"
    );

    @Tool(
        name = "get_meme",
        description = "表情包/热梗/段子推荐。当用户说\"来个梗\"\"来点表情包\"\"讲个段子\"\"有没有什么梗\"\"热梗\"\"最新梗\"\"我想发个表情包\"\"考考你的梗库\"时使用。\n" +
                      "支持：\n" +
                      "- type=\"hot\" → 网络热梗/流行语\n" +
                      "- type=\"quote\" → 经典语录\n" +
                      "- type=\"roast\" → 怼人/调侃语录\n" +
                      "- type=\"random\" → 随机（默认）"
    )
    public String getMeme(
        @ToolParam(value = "type", description = "类型：hot(热梗)、quote(经典语录)、roast(怼人语录)、random(随机)",
                   enums = {"hot", "quote", "roast", "random"}) String type
    ) {
        String actualType = (type == null || type.isBlank()) ? "random" : type.trim().toLowerCase();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        try {
            String result;
            String prefix;

            switch (actualType) {
                case "hot" -> {
                    result = HOT_MEMES.get(random.nextInt(HOT_MEMES.size()));
                    prefix = "🔥 热梗时间：";
                }
                case "quote" -> {
                    result = CLASSIC_QUOTES.get(random.nextInt(CLASSIC_QUOTES.size()));
                    prefix = "📜 经典语录：";
                }
                case "roast" -> {
                    result = ROASTS.get(random.nextInt(ROASTS.size()));
                    prefix = "🎯 今日怼人：";
                }
                default -> {
                    // random: 随机选一个类别
                    int r = random.nextInt(3);
                    return switch (r) {
                        case 0 -> "🔥 热梗：" + HOT_MEMES.get(random.nextInt(HOT_MEMES.size()));
                        case 1 -> "📜 " + CLASSIC_QUOTES.get(random.nextInt(CLASSIC_QUOTES.size()));
                        default -> "🎯 " + ROASTS.get(random.nextInt(ROASTS.size()));
                    };
                }
            }

            return prefix + result;
        } catch (Exception e) {
            log.error("Meme tool failed", e);
            return "哈哈，今天没梗了，容我憋一会儿 😅";
        }
    }
}