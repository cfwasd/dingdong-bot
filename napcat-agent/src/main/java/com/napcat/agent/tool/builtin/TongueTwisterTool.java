package com.napcat.agent.tool.builtin;

import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 绕口令工具。内置中文绕口令库，群聊互动娱乐。
 */
@Slf4j
@Component
public class TongueTwisterTool {

    private record Twister(String title, String content, String difficulty) {}

    private static final List<Twister> TWISTERS = List.of(
            new Twister("四是四",
                    "四是四，十是十，十四是十四，四十是四十。\n谁能说准四十四，就请谁来试一试。",
                    "⭐⭐⭐"),
            new Twister("吃葡萄",
                    "吃葡萄不吐葡萄皮，不吃葡萄倒吐葡萄皮。",
                    "⭐⭐"),
            new Twister("八百标兵",
                    "八百标兵奔北坡，炮兵并排北边跑。\n炮兵怕把标兵碰，标兵怕碰炮兵炮。",
                    "⭐⭐⭐⭐"),
            new Twister("扁担长板凳宽",
                    "扁担长，板凳宽，扁担没有板凳宽，板凳没有扁担长。\n扁担绑在板凳上，板凳不让扁担绑在板凳上。",
                    "⭐⭐⭐⭐"),
            new Twister("黑化肥",
                    "黑化肥发灰，灰化肥发黑。\n黑化肥发灰会挥发，灰化肥挥发会发黑。",
                    "⭐⭐⭐⭐⭐"),
            new Twister("牛郎恋刘娘",
                    "牛郎年年恋刘娘，刘娘年年念牛郎。\n牛郎恋刘娘，刘娘念牛郎。",
                    "⭐⭐⭐⭐"),
            new Twister("红鲤鱼",
                    "红鲤鱼家有头小绿驴叫李屡屡，\n绿鲤鱼家有头小红驴叫吕里里，\n红鲤鱼说他家的李屡屡比绿鲤鱼家的吕里里绿，\n绿鲤鱼说他家的吕里里比红鲤鱼家的李屡屡红。",
                    "⭐⭐⭐⭐⭐"),
            new Twister("鹅过河",
                    "坡上立着一只鹅，坡下就是一条河。\n宽宽的河，肥肥的鹅，鹅要过河，河要渡鹅。\n不知是鹅过河，还是河渡鹅。",
                    "⭐⭐"),
            new Twister("颠倒歌",
                    "咬牛奶喝面包，夹着火车上皮包。\n东西街南北走，出门看见人咬狗。\n捡起狗来打砖头，又怕砖头咬我手。",
                    "⭐⭐"),
            new Twister("司小四和史小世",
                    "司小四和史小世，四月十四日十四时四十上集市，\n司小四买了四十四斤四两西红柿，\n史小世买了十四斤四两细蚕丝。",
                    "⭐⭐⭐⭐⭐"),
            new Twister("白猫黑猫",
                    "白猫黑鼻子，黑猫白鼻子。\n黑猫的白鼻子，碰破了白猫的黑鼻子。\n白猫的黑鼻子破了，剥了秕谷壳儿补鼻子。\n黑猫的白鼻子不破，不剥秕谷壳儿补鼻子。",
                    "⭐⭐⭐"),
            new Twister("盆和瓶",
                    "桌上放个盆，盆里有个瓶。\n砰砰砰，乒乒乒。\n不知是瓶碰盆，还是盆碰瓶。",
                    "⭐⭐"),
            new Twister("荷花和蛤蟆",
                    "荷花一朵朵，蛤蟆一只只。\n荷花上面落蛤蟆，蛤蟆身上落荷花。\n不知是荷花落蛤蟆，还是蛤蟆落荷花。",
                    "⭐⭐⭐"),
            new Twister("板凳和扁担",
                    "板凳宽，扁担长。\n扁担想绑在板凳上，板凳不让扁担绑在板凳上。\n扁担偏要绑在板凳上，板凳偏偏不让扁担绑。",
                    "⭐⭐⭐⭐"),
            new Twister("小黄和小红",
                    "小黄坐在黄凳上，小红拿着红盆走过来。\n小黄看小红端红盆，小红看小黄坐黄凳。\n不知是小黄看小红端红盆，还是小红看小黄坐黄凳。",
                    "⭐⭐⭐"),
            new Twister("老龙和老农",
                    "老龙恼怒闹老农，老农恼怒闹老龙。\n农怒龙恼农更怒，龙恼农怒龙怕农。",
                    "⭐⭐⭐⭐"),
            new Twister("灰堆和乌龟",
                    "灰堆里埋乌龟，乌龟躲在灰堆里。\n不知是灰堆埋乌龟，还是乌龟钻灰堆。",
                    "⭐⭐"),
            new Twister("南南有个篮篮",
                    "南南有个篮篮，篮篮装着盘盘，\n盘盘放着碗碗，碗碗盛着饭饭。\n南南翻了篮篮，篮篮扣了盘盘，\n盘盘打了碗碗，碗碗撒了饭饭。",
                    "⭐⭐⭐")
    );

    @Tool(
        name = "tongue_twister",
        description = "绕口令。当用户说\"绕口令\"\"来个绕口令\"\"考考嘴皮子\"\"练练嘴\"时使用。\n" +
                      "可以指定难度：\n" +
                      "- difficulty=\"easy\" → 简单（⭐⭐）\n" +
                      "- difficulty=\"medium\" → 中等（⭐⭐⭐）\n" +
                      "- difficulty=\"hard\" → 困难（⭐⭐⭐⭐及以上）\n" +
                      "- difficulty=\"random\" → 随机（默认）"
    )
    public String twister(
        @ToolParam(value = "difficulty", description = "难度：easy(简单)、medium(中等)、hard(困难)、random(随机)",
                   enums = {"easy", "medium", "hard", "random"}) String difficulty
    ) {
        String diff = (difficulty == null || difficulty.isBlank()) ? "random" : difficulty.toLowerCase().trim();

        try {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            List<Twister> candidates = switch (diff) {
                case "easy" -> TWISTERS.stream().filter(t -> t.difficulty().length() <= 3).toList();
                case "medium" -> TWISTERS.stream().filter(t -> t.difficulty().length() == 4).toList();
                case "hard" -> TWISTERS.stream().filter(t -> t.difficulty().length() >= 5).toList();
                default -> TWISTERS;
            };

            if (candidates.isEmpty()) {
                candidates = TWISTERS;
            }

            Twister t = candidates.get(random.nextInt(candidates.size()));

            StringBuilder sb = new StringBuilder();
            sb.append("👅 绕口令挑战：").append(t.title()).append("\n");
            sb.append("难度：").append(t.difficulty()).append("\n\n");
            sb.append(t.content());
            sb.append("\n\n🏆 能连说三遍不打结算你赢！");

            return sb.toString();
        } catch (Exception e) {
            log.error("Tongue twister failed", e);
            return "❌ 绕口令出错了，等会再来...";
        }
    }
}
