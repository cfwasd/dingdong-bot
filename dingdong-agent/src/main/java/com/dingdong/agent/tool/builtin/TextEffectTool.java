package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文字特效工具。支持文字翻转、间隔插入、加粗、删除线、马赛克等效果。
 */
@Slf4j
@Component
public class TextEffectTool {

    /** 英文字母翻转映射 */
    private static final Map<Character, Character> FLIP_MAP = Map.ofEntries(
            Map.entry('a', '\u0250'), Map.entry('b', 'q'), Map.entry('c', '\u0254'),
            Map.entry('d', 'p'), Map.entry('e', '\u01DD'), Map.entry('f', '\u025F'),
            Map.entry('g', '\u0253'), Map.entry('h', '\u0265'), Map.entry('i', '\u0131'),
            Map.entry('j', '\u027E'), Map.entry('k', '\u029E'), Map.entry('l', 'l'),
            Map.entry('m', '\u026F'), Map.entry('n', 'u'), Map.entry('o', 'o'),
            Map.entry('p', 'd'), Map.entry('q', 'b'), Map.entry('r', '\u0279'),
            Map.entry('s', 's'), Map.entry('t', '\u0287'), Map.entry('u', 'n'),
            Map.entry('v', '\u028C'), Map.entry('w', '\u028D'), Map.entry('x', 'x'),
            Map.entry('y', '\u028E'), Map.entry('z', 'z'),
            Map.entry('A', '\u2200'), Map.entry('B', 'B'), Map.entry('C', '\u0186'),
            Map.entry('D', 'D'), Map.entry('E', '\u018E'), Map.entry('F', '\u2132'),
            Map.entry('G', '\u2141'), Map.entry('H', 'H'), Map.entry('I', 'I'),
            Map.entry('J', '\u017F'), Map.entry('K', 'K'), Map.entry('L', '\u02E5'),
            Map.entry('M', 'W'), Map.entry('N', 'N'), Map.entry('O', 'O'),
            Map.entry('P', '\u0500'), Map.entry('Q', 'Q'), Map.entry('R', 'R'),
            Map.entry('S', 'S'), Map.entry('T', '\u22A5'), Map.entry('U', '\u2229'),
            Map.entry('V', '\u039B'), Map.entry('W', 'M'), Map.entry('X', 'X'),
            Map.entry('Y', '\u2144'), Map.entry('Z', 'Z')
    );

    @Tool(
        name = "text_effect",
        description = "文字特效工具。对文字进行各种变换效果。\n" +
                      "当用户说\"翻转文字\"\"加特效\"\"文字变粗\"等时使用。\n" +
                      "效果类型：\n" +
                      "- effect=\"flip\" → 上下翻转文字（仅英文有效）\n" +
                      "- effect=\"space\" → 每 个 字 之 间 加 空 格\n" +
                      "- effect=\"strike\" → 删̶除̶线̶效果\n" +
                      "- effect=\"bold\" → 𝐓𝐄𝐗𝐓加粗效果（英文/数字）\n" +
                      "- effect=\"fancy\" → ⓔⓝⓒⓞⓓⓔⓓ圆圈效果\n" +
                      "- effect=\"repeat\" → 文字重复多次\n" +
                      "- effect=\"reverse\" → 文字反转（支持中文）"
    )
    public String effect(
        @ToolParam(value = "effect", description = "特效类型：flip(翻转)、space(加空格)、strike(删除线)、bold(加粗)、fancy(圆圈)、repeat(重复)、reverse(反转)",
                   enums = {"flip", "space", "strike", "bold", "fancy", "repeat", "reverse"}, required = true) String effect,
        @ToolParam(value = "text", description = "要处理的文字", required = true) String text,
        @ToolParam(value = "count", description = "重复次数，仅 effect=repeat 时有效，默认3") Integer count
    ) {
        if (text == null || text.isBlank()) {
            return "❌ 请输入要处理的文字";
        }
        if (effect == null || effect.isBlank()) {
            return "❌ 请指定特效类型";
        }

        try {
            return switch (effect.toLowerCase().trim()) {
                case "flip" -> flipText(text);
                case "space" -> spaceText(text);
                case "strike" -> strikeText(text);
                case "bold" -> boldText(text);
                case "fancy" -> fancyText(text);
                case "repeat" -> repeatText(text, count != null ? count : 3);
                case "reverse" -> reverseText(text);
                default -> "❌ 不支持的特效：" + effect;
            };
        } catch (Exception e) {
            log.error("Text effect failed: effect={}", effect, e);
            return "❌ 特效处理失败：" + e.getMessage();
        }
    }

    private String flipText(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            sb.append(FLIP_MAP.getOrDefault(c, c));
        }
        return "🔄 翻转效果：\n" + sb;
    }

    private String spaceText(String text) {
        return "✨ 空格效果：\n" + String.join(" ", text.split(""));
    }

    private String strikeText(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c).append('\u0336'); // Unicode combining long stroke overlay
        }
        return "✖️ 删除线效果：\n" + sb;
    }

    private String boldText(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                sb.append((char) ('\uD835' + ((c - 'A') / 26)));
                sb.append((char) ('\uDc00' + (c - 'A') % 26));
            } else if (c >= 'a' && c <= 'z') {
                sb.append((char) ('\uD835' + ((c - 'a') / 26)));
                sb.append((char) ('\uDc1a' + (c - 'a') % 26));
            } else if (c >= '0' && c <= '9') {
                sb.append((char) ('\uD835' + ((c - '0') / 26)));
                sb.append((char) ('\uDcEC' + (c - '0') % 26));
            } else {
                sb.append(c);
            }
        }
        return "🅱️ 加粗效果：\n" + sb;
    }

    private String fancyText(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                sb.append((char) ('\u24D0' + (c - 'a')));
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) ('\u24B6' + (c - 'A')));
            } else if (c >= '0' && c <= '9') {
                sb.append((char) ('\u245F' + (c - '0')));
            } else {
                sb.append(c);
            }
        }
        return "⓪ 圆圈效果：\n" + sb;
    }

    private String repeatText(String text, int count) {
        if (count <= 0) count = 1;
        if (count > 20) count = 20;
        return "🔁 重复 " + count + " 次：\n" + (text + " ").repeat(count).trim();
    }

    private String reverseText(String text) {
        return "🔀 反转效果：\n" + new StringBuilder(text).reverse().toString();
    }
}
