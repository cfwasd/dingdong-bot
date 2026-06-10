package com.napcat.agent.tool.builtin;

import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 猜数字游戏工具。支持多用户同时游戏，记录各自进度。
 */
@Slf4j
@Component
public class NumberGuessTool {

    /** 进行中的游戏，key = userId */
    private final Map<String, GameState> games = new ConcurrentHashMap<>();

    @Tool(
        name = "number_guess",
        description = "猜数字游戏。AI 想一个 1-100 的数字，你来猜，会告诉你大了还是小了。\n" +
                      "当用户说\"猜数字\"\"来玩猜数字\"时使用。\n" +
                      "用法：\n" +
                      "- action=\"start\" → 开始新游戏\n" +
                      "- action=\"guess\", number=50 → 猜一个数字\n" +
                      "- action=\"giveup\" → 放弃，公布答案"
    )
    public String play(
        @ToolParam(value = "action", description = "操作：start(开始游戏)、guess(猜数字)、giveup(放弃)",
                   enums = {"start", "guess", "giveup"}, required = true) String action,
        @ToolParam(value = "number", description = "猜的数字（1-100），仅 action=guess 时有效") Integer number,
        @ToolParam(value = "user_id", description = "玩家标识（可选），用于区分不同玩家") String userId
    ) {
        String key = (userId != null && !userId.isBlank()) ? userId : "default";
        String actualAction = (action == null || action.isBlank()) ? "start" : action.toLowerCase().trim();

        try {
            return switch (actualAction) {
                case "start" -> startGame(key);
                case "guess" -> makeGuess(key, number);
                case "giveup" -> giveUp(key);
                default -> "❌ 未知操作，支持：start、guess、giveup";
            };
        } catch (Exception e) {
            log.error("Number guess failed", e);
            return "❌ 游戏出错：" + e.getMessage();
        }
    }

    private String startGame(String key) {
        int answer = ThreadLocalRandom.current().nextInt(1, 101);
        games.put(key, new GameState(answer));
        return "🎯 猜数字游戏开始！\n" +
               "我已经想好了一个 1~100 之间的数字\n" +
               "发 \"猜 XX\" 来猜吧，比如 \"猜 50\"\n" +
               "我会告诉你大了还是小了 😎";
    }

    private String makeGuess(String key, Integer number) {
        if (number == null) {
            return "❌ 请输入你要猜的数字（1-100）";
        }
        if (number < 1 || number > 100) {
            return "❌ 数字范围是 1-100";
        }

        GameState game = games.get(key);
        if (game == null) {
            return "⚠️ 还没有开始游戏哦，先说 \"猜数字\" 开始吧";
        }
        if (game.isFinished()) {
            games.remove(key);
            return "🎮 这局已经结束了，说 \"猜数字\" 开始新一局吧";
        }

        game.incrementAttempts();
        int answer = game.getAnswer();
        int attempts = game.getAttempts();

        if (number == answer) {
            game.setFinished(true);
            String rating;
            if (attempts <= 3) rating = "🏆 太厉害了，天才！";
            else if (attempts <= 5) rating = "👏 不错不错，很聪明！";
            else if (attempts <= 7) rating = "😊 还可以，继续加油！";
            else rating = "😅 终于猜到了，辛苦了...";

            return "🎉 恭喜你猜对了！答案就是 " + answer + "\n" +
                   "一共猜了 " + attempts + " 次\n" + rating;
        }

        String hint = number > answer ? "📉 大了！往小了猜" : "📈 小了！往大了猜";
        // 缩小范围提示
        if (number > answer) game.updateMin(game.getMin());
        else game.updateMax(game.getMax());

        return hint + "\n已经猜了 " + attempts + " 次，加油！";
    }

    private String giveUp(String key) {
        GameState game = games.remove(key);
        if (game == null) {
            return "⚠️ 没有进行中的游戏";
        }
        return "😏 这就放弃啦？答案是 " + game.getAnswer() + "\n下次加油！";
    }

    private static class GameState {
        private final int answer;
        private int attempts;
        private boolean finished;
        private int min = 1;
        private int max = 100;

        GameState(int answer) { this.answer = answer; }
        int getAnswer() { return answer; }
        int getAttempts() { return attempts; }
        boolean isFinished() { return finished; }
        void setFinished(boolean f) { this.finished = f; }
        void incrementAttempts() { attempts++; }
        int getMin() { return min; }
        int getMax() { return max; }
        void updateMin(int v) { this.min = Math.max(this.min, v); }
        void updateMax(int v) { this.max = Math.min(this.max, v); }
    }
}
