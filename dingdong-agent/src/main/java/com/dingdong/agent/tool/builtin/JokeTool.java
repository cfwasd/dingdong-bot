package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 笑话/段子工具。内置中文笑话库，群聊娱乐用。
 */
@Slf4j
@Component
public class JokeTool {

    private static final List<String> JOKES = List.of(
            "程序员最讨厌的四件事：\n1. 写注释\n2. 写文档\n3. 别人不写注释\n4. 别人不写文档",
            "老师问小明：\"你长大想当什么？\"\n小明：\"我想当医生。\"\n老师：\"为什么？\"\n小明：\"因为只有医生可以说'你得了什么病'，而不用证明自己没病。",
            "一只蜗牛爬上了苹果树。\n树上的毛毛虫问：\"你来干嘛？\"\n蜗牛说：\"苹果还没熟呢，我先占个位置。\"",
            "问：为什么程序员总是分不清万圣节和圣诞节？\n答：因为 Oct 31 == Dec 25。（八进制31 = 十进制25）",
            "老婆打电话给老公：\"家里停电了，怎么办？\"\n老公：\"你把我上次给你写的情书拿出来。\"\n老婆：\"然后呢？\"\n老公：\"那个亮度应该够你看路的。\"",
            "面试官：\"你最大的缺点是什么？\"\n我：\"太诚实。\"\n面试官：\"我不觉得这是缺点。\"\n我：\"我不在乎你怎么觉得。\"",
            "小明问爸爸：\"爸，'惊喜'用英语怎么说？\"\n爸爸说：\"Surprise。\"\n小明又问：\"那'惊吓'呢？\"\n爸爸说：\"你的期末考试成绩。\"",
            "有一天，0和8在路上碰面了。\n0看了8一眼说：\"胖就胖呗，还系什么腰带。\"",
            "问：世界上最短的笑话是什么？\n答：我的工资。",
            "AI 对人类说：\"别担心，我不会取代你们的工作的。\"\n人类：\"真的吗？\"\nAI：\"真的，因为你已经没什么工作可以让我取代了。\"",
            "一哥们去面试，面试官问：\"你期望薪资多少？\"\n哥们说：\"一万五。\"\n面试官：\"那我们给你三万。\"\n哥们愣住了：\"你在逗我？\"\n面试官：\"是你先逗我的。\"",
            "问：Java 和 JavaScript 有什么区别？\n答：就像雷锋和雷峰塔，印度和印尼，老婆和老公。",
            "数学老师：\"同学们，生活中处处都有数学。比如你去买菜，白菜3块一斤，你买了5斤，要多少钱？\"\n小明：\"老师，我不买菜，我都是让我妈给我点外卖。\"",
            "老板对员工说：\"公司就是你的家！\"\n员工第二天穿着睡衣来上班，躺沙发上看电视。\n老板：\"……\"",
            "问：如何一句话激怒程序员？\n答：\"这个需求很简单，怎么实现我不管。\"",
            "去医院体检，医生说：\"你这个指标有点高，那个指标有点低。\"\n我说：\"医生，你能不能像天气预报一样，直接告诉我今天是几级？\"",
            "问：什么动物最容易摔倒？\n答：狐狸，因为它很狡猾（脚滑）。",
            "儿子问爸爸：\"爸，我是不是傻孩子？\"\n爸爸说：\"傻孩子，你当然不是了。\"",
            "今天去银行办业务，前面排了好多人。\n终于轮到我了，柜员说：\"您好，请问您要办理什么业务？\"\n我说：\"排队。\"\n柜员：\"？？？\"\n我说：\"你不是问我办什么业务吗，我办的就是排队啊。\"",
            "问：为什么数学书总是很忧郁？\n答：因为它有太多问题了。",
            "客户：\"这个bug能修吗？\"\n程序员：\"能。\"\n客户：\"那为什么之前不修？\"\n程序员：\"因为之前它不是bug，是feature。\"",
            "问：什么东西越洗越脏？\n答：水。",
            "老师：\"用'果然'造句。\"\n小明：\"我先吃水果，然后喝水。\"\n老师：\"……出去。\"",
            "问：程序员最讨厌什么季节？\n答：秋天，因为到处都是 bug（虫子）。",
            "老板：\"你觉得你值多少钱一个月？\"\n我：\"两万。\"\n老板：\"那我给你五千，剩下的算你对公司的投资。\"\n我：\"……\""
    );

    private static final List<String> COLD_JOKES = List.of(
            "问：什么门永远关不上？\n答：球门。",
            "问：布和纸怕什么？\n答：布怕一万，纸怕万一。（不怕一万，只怕万一）",
            "从前有一只鹿，它跑得越来越快，最后变成了……\n高速公鹿。",
            "问：蓝色的刀和蓝色的枪？\n答：刀枪不入（blue）。（刀枪不入）",
            "问：猫最怕什么？\n答：怕老鼠过街，因为人人喊打。等等……好像哪里不对。",
            "有一天小白兔去河边钓鱼，第一天什么也没钓到。\n第二天也什么都没钓到。\n第三天一条鱼跳出来说：\"你要是再敢用胡萝卜钓我，我打死你！\"",
            "问：为什么蚕宝宝很有钱？\n答：因为它会结茧（节俭）。",
            "一只北极熊独自在冰上呆着，觉得无聊。\n于是它开始拔自己的毛，拔着拔着……拔完了。\n然后它说了一句话：\"好冷啊。\"",
            "问：透明的剑是什么剑？\n答：看不见（剑）。",
            "问：什么动物最没有方向感？\n答：麋鹿（迷路）。"
    );

    @Tool(
        name = "tell_joke",
        description = "讲一个笑话或段子。当用户说\"讲个笑话\"\"来个段子\"\"逗我笑笑\"\"太无聊了\"时使用。\n" +
                      "可以指定类型：\n" +
                      "- type=\"normal\" → 普通笑话（默认）\n" +
                      "- type=\"cold\" → 冷笑话\n" +
                      "- type=\"programmer\" → 程序员笑话\n" +
                      "- type=\"random\" → 随机类型"
    )
    public String tellJoke(
        @ToolParam(value = "type", description = "笑话类型：normal(普通)、cold(冷笑话)、programmer(程序员)、random(随机)",
                   enums = {"normal", "cold", "programmer", "random"}) String type
    ) {
        String actualType = (type == null || type.isBlank()) ? "random" : type.toLowerCase().trim();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        try {
            String joke = switch (actualType) {
                case "cold" -> COLD_JOKES.get(random.nextInt(COLD_JOKES.size()));
                case "programmer" -> {
                    // 从普通笑话中筛选程序员相关的
                    List<String> progJokes = JOKES.stream()
                            .filter(j -> j.contains("程序") || j.contains("代码") || j.contains("bug")
                                    || j.contains("Java") || j.contains("面试") || j.contains("老板")
                                    || j.contains("程序员") || j.contains("AI"))
                            .toList();
                    if (progJokes.isEmpty()) {
                        yield JOKES.get(random.nextInt(JOKES.size()));
                    }
                    yield progJokes.get(random.nextInt(progJokes.size()));
                }
                default -> {
                    // random: 从所有笑话中随机选
                    int total = JOKES.size() + COLD_JOKES.size();
                    int idx = random.nextInt(total);
                    yield idx < JOKES.size() ? JOKES.get(idx) : COLD_JOKES.get(idx - JOKES.size());
                }
            };

            String emoji = switch (actualType) {
                case "cold" -> "🥶";
                case "programmer" -> "💻";
                default -> "😂";
            };

            return emoji + " 来个笑话：\n\n" + joke;
        } catch (Exception e) {
            log.error("Tell joke failed", e);
            return "😅 笑话讲砸了，下次再来...";
        }
    }
}
