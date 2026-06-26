package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 塔罗牌占卜工具。随机抽牌+简化解读，群聊娱乐用。
 */
@Slf4j
@Component
public class TarotTool {

    private static final List<Card> MAJOR_ARCANA = List.of(
        new Card("愚者", "新的开始、冒险、天真", "正位：新的旅程即将开始，保持开放的心态。\n逆位：鲁莽行事，需要谨慎考虑后果。"),
        new Card("魔术师", "创造力、技能、自信", "正位：你拥有实现目标所需的一切能力。\n逆位：能力被浪费，或被欺骗。"),
        new Card("女祭司", "直觉、神秘、内在智慧", "正位：相信你的直觉，静观其变。\n逆位：忽视内心声音，秘密被揭露。"),
        new Card("皇后", "丰饶、母性、自然", "正位：收获的季节，享受生活的美好。\n逆位：过度依赖他人，缺乏独立。"),
        new Card("皇帝", "权威、结构、控制", "正位：稳扎稳打，建立秩序和规则。\n逆位：专制或缺乏纪律。"),
        new Card("教皇", "传统、信仰、指导", "正位：遵循传统，寻求导师帮助。\n逆位：打破常规，走自己的路。"),
        new Card("恋人", "爱、和谐、选择", "正位：关系和谐，面临重要的选择。\n逆位：价值观冲突，关系失衡。"),
        new Card("战车", "意志力、胜利、决心", "正位：克服障碍，勇往直前。\n逆位：失控或方向错误。"),
        new Card("力量", "勇气、耐心、内在力量", "正位：以柔克刚，内在力量胜过蛮力。\n逆位：自我怀疑，缺乏信心。"),
        new Card("隐者", "内省、孤独、指引", "正位：需要独处思考，寻求内心答案。\n逆位：孤立自己，拒绝帮助。"),
        new Card("命运之轮", "命运、转折、循环", "正位：时来运转，抓住机遇。\n逆位：运气不佳，但终会过去。"),
        new Card("正义", "公平、真相、因果", "正位：公正的结果即将到来。\n逆位：不公平的对待，或逃避责任。"),
        new Card("倒吊人", "牺牲、换个角度、等待", "正位：换个角度看问题，暂时的停顿。\n逆位：无谓的牺牲，停滞不前。"),
        new Card("死神", "结束、转变、新生", "正位：旧的不去新的不来，迎接改变。\n逆位：抗拒改变，停滞不前。"),
        new Card("节制", "平衡、调和、耐心", "正位：保持中庸之道，一切都会好起来。\n逆位：极端或失衡。"),
        new Card("恶魔", "束缚、物质主义、欲望", "正位：被欲望或执念束缚。\n逆位：挣脱束缚，重获自由。"),
        new Card("高塔", "突变、崩塌、启示", "正位：突如其来的变化，旧结构的崩塌。\n逆位：避免了一场灾难，但问题仍在。"),
        new Card("星星", "希望、灵感、平静", "正位：希望就在前方，保持信念。\n逆位：失去信心，感到绝望。"),
        new Card("月亮", "幻觉、恐惧、潜意识", "正位：不要被表象迷惑，直面内心的恐惧。\n逆位：恐惧逐渐消散，真相浮出水面。"),
        new Card("太阳", "快乐、成功、活力", "正位：一切顺利，享受当下的美好。\n逆位：暂时的不顺，但阳光终会回来。"),
        new Card("审判", "觉醒、重生、召唤", "正位：是时候做出重要决定了。\n逆位：逃避内心的召唤，害怕改变。"),
        new Card("世界", "完成、圆满、成就", "正位：一个阶段的完美结束，达成目标。\n逆位：差一点就完成了，不要放弃。")
    );

    private static final List<String> AREAS = List.of(
        "感情", "事业", "学业", "财运", "健康", "人际关系", "整体运势"
    );

    record Card(String name, String keywords, String meaning) {}

    @Tool(
        name = "tarot",
        description = "塔罗牌占卜。当用户说\"塔罗牌\"\"帮我占卜\"\"抽塔罗\"\"塔罗\"\"算一下\"时使用。\n" +
                      "随机抽取3张塔罗牌（过去/现在/未来），附带简化解读。"
    )
    public String draw(
        @ToolParam(value = "question", description = "想问的问题或关心的领域（可选），如\"感情\"\"事业\"", required = false) String question
    ) {
        try {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            // 随机抽3张不重复的牌
            int i1 = random.nextInt(MAJOR_ARCANA.size());
            int i2, i3;
            do { i2 = random.nextInt(MAJOR_ARCANA.size()); } while (i2 == i1);
            do { i3 = random.nextInt(MAJOR_ARCANA.size()); } while (i3 == i1 || i3 == i2);

            Card past = MAJOR_ARCANA.get(i1);
            Card present = MAJOR_ARCANA.get(i2);
            Card future = MAJOR_ARCANA.get(i3);

            StringBuilder sb = new StringBuilder();
            sb.append("🃏 塔罗牌占卜\n");
            if (question != null && !question.isBlank()) {
                sb.append("问：").append(question.trim()).append("\n");
            }
            sb.append("━━━━━━━━━━━━━━\n\n");
            sb.append("📜 过去：").append(past.name()).append("\n");
            sb.append("   ").append(past.keywords()).append("\n\n");
            sb.append("🃏 现在：").append(present.name()).append("\n");
            sb.append("   ").append(present.keywords()).append("\n\n");
            sb.append("🔮 未来：").append(future.name()).append("\n");
            sb.append("   ").append(future.keywords()).append("\n");
            sb.append("━━━━━━━━━━━━━━\n\n");
            sb.append("💡 综合解读：\n");
            sb.append(future.meaning().split("\n")[0]);

            return sb.toString();
        } catch (Exception e) {
            log.error("Tarot draw failed", e);
            return "🃏 塔罗牌似乎今天不想说话...";
        }
    }
}
