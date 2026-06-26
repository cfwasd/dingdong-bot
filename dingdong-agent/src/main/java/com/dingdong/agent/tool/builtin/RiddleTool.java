package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 猜谜语工具。内置中文谜语库，先出题后给答案。群聊互动娱乐。
 */
@Slf4j
@Component
public class RiddleTool {

    private record Riddle(String question, String answer) {}

    private static final List<Riddle> RIDDLES = List.of(
            new Riddle("一物生来强又强，没脚没手会打仗，打输的往水里跳，打赢的岸上唱。", "螃蟹"),
            new Riddle("有头没有颈，身上冷冰冰，有翅不能飞，无脚也能行。", "鱼"),
            new Riddle("身穿绿衣裳，肚里红彤彤，生的儿子多，个个黑脸膛。", "西瓜"),
            new Riddle("千条线万条线，落到水里看不见。", "雨"),
            new Riddle("兄弟七八个，围着柱子坐，大家一分手，衣服就扯破。", "大蒜"),
            new Riddle("一个小姑娘，生在水中央，身穿粉红衫，坐在绿船上。", "荷花"),
            new Riddle("颜色白如雪，身子硬如铁，一日洗三遍，夜晚柜里歇。", "碗"),
            new Riddle("有面没有口，有脚没有手，虽有四只脚，自己不会走。", "桌子"),
            new Riddle("白嫩小宝宝，洗澡吹泡泡，洗洗身体小，再洗不见了。", "肥皂"),
            new Riddle("身穿大皮袄，野草吃个饱，过了严冬天，献出一身毛。", "绵羊"),
            new Riddle("八把尖刀，两把剪刀，身背皮箱，走路横跑。", "螃蟹"),
            new Riddle("一座桥，架上 sky，红橙黄绿真美丽，天上下雨它就出，雨停它就消失。", "彩虹"),
            new Riddle("说它是牛不是牛，背着房子到处走。", "蜗牛"),
            new Riddle("小时穿青袄，大时换红袍，脱下红袍看，一身白胖胖。", "花生"),
            new Riddle("弟兄几个真和气，天天并肩坐一起，少时喜欢绿衣服，老来都穿黄色衣。", "香蕉"),
            new Riddle("远看像只猫，近看是只鸟，晚上捉老鼠，白天睡大觉。", "猫头鹰"),
            new Riddle("一物三口，有腿无手，谁要没它，难见亲友。", "裤子"),
            new Riddle("又圆又扁肚里空，活动镜子在当中，大人小孩都爱它，每天离不开它。", "碗"),
            new Riddle("千姊妹万姊妹，不排坐，各人戴顶绿帽子。", "石榴"),
            new Riddle("一个老头，不跑不走，请他睡觉，他就摇头。", "不倒翁"),
            new Riddle("上不怕水，下不怕火，家家厨房，都有一个。", "锅"),
            new Riddle("小小诸葛亮，独坐军中帐，摆下八卦阵，专捉飞来将。", "蜘蛛"),
            new Riddle("头戴红帽子，身披五彩衣，从来不唱戏，喜欢吊嗓子。", "公鸡"),
            new Riddle("身披花棉袄，唱歌呱呱叫，田里捉害虫，丰收立功劳。", "青蛙"),
            new Riddle("性子像鸭不是鸭，浑身长满小疙瘩，晚上出门白天睡，捉虫本领顶呱呱。", "猫头鹰"),
            new Riddle("一匹马儿真正好，没有尾巴没有脚，不喝水来不吃草，骑上它就满街跑。", "自行车"),
            new Riddle("四四方方一块布，嘴巴鼻子它照顾，一天要用它几次，讲卫生靠它帮助。", "毛巾"),
            new Riddle("红口袋绿口袋，有人怕有人爱，爱吃的人不少，怕吃的人更多。", "辣椒"),
            new Riddle("圆筒白浆糊，早晚挤一股，兄弟三十二，都说有好处。", "牙膏"),
            new Riddle("口大无牙，肚大无脐，能吞不能吐，百样都欢喜。", "袋子"),
            new Riddle("像糖不甜，像盐不咸，冬天飞满天，夏天看不见。", "雪"),
            new Riddle("年纪并不大，胡子一大把，不论遇见谁，总爱喊妈妈。", "羊"),
            new Riddle("金箍桶，银箍桶，打开来，箍不拢。", "鸡蛋"),
            new Riddle("一位游泳家，说话呱呱呱，小时有尾没有脚，大时有脚没尾巴。", "青蛙"),
            new Riddle("皮黑肉儿白，肚里墨样黑，从不偷东西，硬说它是贼。", "乌贼"),
            new Riddle("名字叫做牛，不会拉犁头，说它力气小，背着房子走。", "蜗牛"),
            new Riddle("一物生得真奇怪，腰里长出胡子来，拔掉胡子剥开看，露出牙齿一排排。", "玉米"),
            new Riddle("不是葱不是蒜，一层一层裹紫缎，说葱长得矮，像蒜不分瓣。", "洋葱"),
            new Riddle("身子长，个不大，满身都是牙，人人见了怕，见它就打它。", "锯子"),
            new Riddle("什么东西天气越热，它爬得越高？", "温度计")
    );

    @Tool(
        name = "riddle",
        description = "猜谜语。当用户说\"猜谜语\"\"来个谜语\"\"出个谜语\"\"谜语\"时使用。\n" +
                      "用法：\n" +
                      "- show_answer=false → 只出谜语，不显示答案（让人猜）\n" +
                      "- show_answer=true → 出谜语并显示答案"
    )
    public String riddle(
        @ToolParam(value = "show_answer", description = "是否显示答案：true显示答案，false只出题（默认false）") String showAnswer
    ) {
        try {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Riddle r = RIDDLES.get(random.nextInt(RIDDLES.size()));

            boolean show = "true".equalsIgnoreCase(showAnswer);

            StringBuilder sb = new StringBuilder();
            sb.append("🧩 猜谜语：\n\n");
            sb.append(r.question());

            if (show) {
                sb.append("\n\n💡 答案：").append(r.answer());
            } else {
                sb.append("\n\n🤔 你猜到了吗？发答案给我看看~");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Riddle failed", e);
            return "❌ 谜语出错了，等会再来...";
        }
    }
}
