package com.napcat.agent.tool.builtin;

import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 翻译工具。使用免费 API 进行多语言翻译。
 */
@Slf4j
@Component
public class TranslateTool {

    private static final String API_URL = "https://api.mymemory.translated.net/get";
    private static final Map<String, String> LANG_MAP = Map.ofEntries(
            Map.entry("中文", "zh-CN"), Map.entry("中文简体", "zh-CN"), Map.entry("中文繁体", "zh-TW"),
            Map.entry("英语", "en-GB"), Map.entry("英文", "en-GB"), Map.entry("日语", "ja"), Map.entry("日文", "ja"),
            Map.entry("韩语", "ko"), Map.entry("韩文", "ko"), Map.entry("法语", "fr"), Map.entry("法文", "fr"),
            Map.entry("德语", "de"), Map.entry("德文", "de"), Map.entry("西班牙语", "es"), Map.entry("俄语", "ru"),
            Map.entry("葡萄牙语", "pt"), Map.entry("意大利语", "it"), Map.entry("阿拉伯语", "ar"),
            Map.entry("泰语", "th"), Map.entry("越南语", "vi"), Map.entry("印尼语", "id"),
            Map.entry("自动", "auto")
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool(
        name = "translate",
        description = "翻译文本。支持中/英/日/韩/法/德/西/俄等多种语言互译。\n" +
                      "当用户说\"翻译\"\"translate\"\"用英语怎么说\"\"翻译成中文\"时使用。\n" +
                      "示例：\n" +
                      "- text=\"你好世界\", target=\"英语\" → Hello World\n" +
                      "- text=\"Hello\", target=\"日语\" → こんにちは\n" +
                      "- text=\"你好\", target=\"韩语\" → 안녕하세요\n" +
                      "如果不指定 source，自动检测源语言。"
    )
    public String translate(
        @ToolParam(value = "text", description = "要翻译的文本", required = true) String text,
        @ToolParam(value = "target", description = "目标语言，如：英语、日语、韩语、法语、中文、德语、西班牙语等", required = true) String target,
        @ToolParam(value = "source", description = "源语言（可选），不填则自动检测。如：中文、英语、日语等") String source
    ) {
        if (text == null || text.isBlank()) {
            return "❌ 请输入要翻译的文本";
        }
        if (target == null || target.isBlank()) {
            return "❌ 请指定目标语言";
        }

        try {
            String targetLang = resolveLang(target.trim());
            String sourceLang = (source != null && !source.isBlank()) ? resolveLang(source.trim()) : "auto";

            if (targetLang == null) {
                return "❌ 不支持的目标语言：" + target + "\n支持的语言：中文、英语、日语、韩语、法语、德语、西班牙语、俄语、葡萄牙语、意大利语、阿拉伯语、泰语、越南语、印尼语";
            }

            // 如果 source 是 auto，先简单检测
            if ("auto".equals(sourceLang)) {
                sourceLang = detectSimple(text);
                // 如果源和目标相同，提示用户
                if (sourceLang.equals(targetLang)) {
                    return "⚠️ 检测到的源语言和目标语言相同，无需翻译。\n原文：" + text;
                }
            }

            // 调用 MyMemory 翻译 API
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String langPair = sourceLang + "|" + targetLang;
            String url = API_URL + "?q=" + encodedText + "&langpair=" + URLEncoder.encode(langPair, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "NapCatBot/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "❌ 翻译服务返回错误（HTTP " + response.statusCode() + "），请稍后重试";
            }

            // 简单解析 JSON 结果
            String body = response.body();
            String translated = extractJsonValue(body, "translatedText");
            if (translated == null || translated.isBlank()) {
                return "❌ 翻译结果为空，请检查输入内容";
            }

            // HTML 实体解码
            translated = translated.replace("&quot;", "\"").replace("&amp;", "&")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'");

            String sourceName = getLangName(sourceLang);
            String targetName = getLangName(targetLang);

            return "🌐 翻译结果（" + sourceName + " → " + targetName + "）：\n" + translated;

        } catch (Exception e) {
            log.error("Translation failed: text={}, target={}", text, target, e);
            return "❌ 翻译失败：" + e.getMessage();
        }
    }

    private String resolveLang(String input) {
        if (input == null || input.isBlank()) return null;
        // 直接匹配中文名称
        String resolved = LANG_MAP.get(input);
        if (resolved != null) return resolved;
        // 尝试小写匹配
        String lower = input.toLowerCase();
        for (var entry : LANG_MAP.entrySet()) {
            if (entry.getKey().toLowerCase().contains(lower) || lower.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        // 如果已经是语言代码格式（如 en, ja, zh）
        if (input.matches("[a-z]{2}(-[A-Z]{2})?")) return input;
        return null;
    }

    /**
     * 简单语言检测（基于字符范围）
     */
    private String detectSimple(String text) {
        int cjkCount = 0, jpCount = 0, krCount = 0, latinCount = 0, cyrillicCount = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) cjkCount++;
            else if (c >= 0x3040 && c <= 0x309F) jpCount++; // 平假名
            else if (c >= 0x30A0 && c <= 0x30FF) jpCount++; // 片假名
            else if (c >= 0xAC00 && c <= 0xD7AF) krCount++; // 韩文
            else if (c >= 0x1100 && c <= 0x11FF) krCount++;  // 韩文字母
            else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) latinCount++;
            else if (c >= 0x0400 && c <= 0x04FF) cyrillicCount++;
        }

        int total = text.length();
        if (jpCount > 0 && (jpCount + cjkCount) > total * 0.3) return "ja";
        if (krCount > total * 0.2) return "ko";
        if (cjkCount > total * 0.3) return "zh-CN";
        if (cyrillicCount > total * 0.3) return "ru";
        if (latinCount > total * 0.3) return "en-GB";
        return "en-GB"; // 默认当英文
    }

    private String getLangName(String code) {
        if (code == null) return "未知";
        return switch (code) {
            case "zh-CN" -> "中文";
            case "zh-TW" -> "中文繁体";
            case "en-GB" -> "英语";
            case "ja" -> "日语";
            case "ko" -> "韩语";
            case "fr" -> "法语";
            case "de" -> "德语";
            case "es" -> "西班牙语";
            case "ru" -> "俄语";
            case "pt" -> "葡萄牙语";
            case "it" -> "意大利语";
            case "ar" -> "阿拉伯语";
            case "th" -> "泰语";
            case "vi" -> "越南语";
            case "id" -> "印尼语";
            default -> code;
        };
    }

    /**
     * 简单 JSON 值提取（避免引入额外依赖）
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        // 处理转义引号
        while (endQuote > 0 && json.charAt(endQuote - 1) == '\\') {
            endQuote = json.indexOf('"', endQuote + 1);
        }
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }
}
