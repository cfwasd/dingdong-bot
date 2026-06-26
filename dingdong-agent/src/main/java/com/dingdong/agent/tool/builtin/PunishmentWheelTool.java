package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 惩罚大转盘工具。随机抽取惩罚项目，群聊娱乐用。
 */
@Slf4j
@Component
public class PunishmentWheelTool {

    private static final List<String> PUNISHMENTS = List.of(
        "🎤 唱一首歌！在群里发一段语音唱歌（跑调也算）",
        "🤳 发自拍！立刻发一张自拍到群里",
        "😂 讲一个冷笑话！不好笑就再来一个",
        "🦆 用鸭子音说一句话发语音",
        "📱 给你最近聊天的第3个人发\"我想你了\"",
        "✍️ 把群名片改成\"我是憨憨\"保持10分钟",
        "🗣️ 在群里发语音大喊三声\"我是猪\"",
        "💃 用文字描述你跳广场舞的画面（不少于30字）",
        "🙈 说出你最近一次社死经历",
        "🎭 模仿群主/管理员说一句话",
        "📞 给通讯录随机一个人打电话说\"没啥事就是想你了\"",
        "🤪 用三种不同的语气说\"你好讨厌哦\"（撒娇/愤怒/深情）",
        "🖼️ 把你手机相册倒数第3张照片发出来",
        "🔄 倒着念出自己的名字发语音",
        "😈 在群里@一个人夸他/她（必须真诚，不准阴阳怪气）",
        "🤖 用机器人的语气说话，坚持30秒（群里打字）",
        "🎵 把群公告改成一段歌词保持5分钟",
        "🗿 一分钟内不准说话（系统自动计时）",
        "🍜 描述你吃过最难吃的东西（不少于20字）",
        "💔 说出你最尴尬的一次表白/被表白经历",
        "🎪 用emoji讲一个故事（至少5个emoji）",
        "🦸 给自己取一个超级英雄名字并解释能力",
        "🎯 闭眼在键盘上随机打5个字发出来",
        "📢 在群里发：\"有没有人想请我喝奶茶\"",
        "🍕 如果必须选一种食物吃一辈子，你选什么？为什么？"
    );

    @Tool(
        name = "punishment_wheel",
        description = "惩罚大转盘。当用户说\"大转盘\"\"惩罚转盘\"\"转盘\"\"惩罚\"\"整蛊\"\"来点惩罚\"\"抽一个惩罚\"时使用。\n" +
                      "随机抽取一个搞笑惩罚项目，群聊整蛊娱乐用。"
    )
    public String spin(
        @ToolParam(value = "count", description = "抽取数量（默认1，最多5）", required = false) String countStr
    ) {
        int count = 1;
        if (countStr != null && !countStr.isBlank()) {
            try {
                count = Integer.parseInt(countStr.trim());
                if (count < 1) count = 1;
                if (count > 5) count = 5;
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            boolean[] used = new boolean[PUNISHMENTS.size()];
            StringBuilder sb = new StringBuilder();
            sb.append("🎡 惩罚大转盘结果：\n\n");

            for (int i = 0; i < count; i++) {
                int idx;
                do {
                    idx = random.nextInt(PUNISHMENTS.size());
                } while (used[idx]);
                used[idx] = true;

                sb.append(count > 1 ? (i + 1) + ". " : "").append(PUNISHMENTS.get(idx)).append("\n");
                if (i < count - 1) sb.append("\n");
            }

            if (count > 1) {
                sb.append("\n💡 选一个执行就好！");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Punishment wheel failed", e);
            return "🎡 转盘卡住了...再来一次！";
        }
    }
}
