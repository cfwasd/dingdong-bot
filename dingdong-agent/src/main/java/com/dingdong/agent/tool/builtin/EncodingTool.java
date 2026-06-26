package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 编码/解码工具。支持 Base64、URL 编码、Unicode 编码等。
 */
@Slf4j
@Component
public class EncodingTool {

    @Tool(
        name = "encode_decode",
        description = "文本编码/解码工具。支持 Base64、URL 编码、Unicode 编码的转换。\n" +
                      "当用户说\"base64编码\"\"解码\"\"url编码\"\"unicode转码\"时使用。\n" +
                      "示例：\n" +
                      "- action=\"base64_encode\", text=\"Hello\" → SGVsbG8=\n" +
                      "- action=\"base64_decode\", text=\"SGVsbG8=\" → Hello\n" +
                      "- action=\"url_encode\", text=\"你好世界\" → %E4%BD%A0%E5%A5%BD...\n" +
                      "- action=\"url_decode\", text=\"%E4%BD%A0%E5%A5%BD\" → 你好\n" +
                      "- action=\"unicode_encode\", text=\"你好\" → \\u4f60\\u597d\n" +
                      "- action=\"unicode_decode\", text=\"\\u4f60\\u597d\" → 你好"
    )
    public String encodeDecode(
        @ToolParam(value = "action", description = "操作类型：base64_encode、base64_decode、url_encode、url_decode、unicode_encode、unicode_decode",
                   enums = {"base64_encode", "base64_decode", "url_encode", "url_decode", "unicode_encode", "unicode_decode"},
                   required = true) String action,
        @ToolParam(value = "text", description = "要处理的文本", required = true) String text
    ) {
        if (action == null || action.isBlank()) {
            return "❌ 请指定操作类型";
        }
        if (text == null || text.isBlank()) {
            return "❌ 请输入要处理的文本";
        }

        try {
            return switch (action.toLowerCase().trim()) {
                case "base64_encode" -> base64Encode(text);
                case "base64_decode" -> base64Decode(text);
                case "url_encode" -> urlEncode(text);
                case "url_decode" -> urlDecode(text);
                case "unicode_encode" -> unicodeEncode(text);
                case "unicode_decode" -> unicodeDecode(text);
                default -> "❌ 不支持的操作：" + action;
            };
        } catch (Exception e) {
            log.error("Encode/decode failed: action={}", action, e);
            return "❌ 处理失败：" + e.getMessage();
        }
    }

    private String base64Encode(String text) {
        String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        return "🔐 Base64 编码结果：\n" + encoded;
    }

    private String base64Decode(String text) {
        try {
            byte[] decoded = Base64.getDecoder().decode(text.trim());
            return "🔓 Base64 解码结果：\n" + new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 尝试 URL-safe Base64
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(text.trim());
                return "🔓 Base64(URL-safe) 解码结果：\n" + new String(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e2) {
                return "❌ 不是有效的 Base64 编码，请检查输入";
            }
        }
    }

    private String urlEncode(String text) {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        return "🌐 URL 编码结果：\n" + encoded;
    }

    private String urlDecode(String text) {
        try {
            String decoded = URLDecoder.decode(text.trim(), StandardCharsets.UTF_8);
            return "🌐 URL 解码结果：\n" + decoded;
        } catch (Exception e) {
            return "❌ 不是有效的 URL 编码，请检查输入";
        }
    }

    private String unicodeEncode(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c > 127) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return "✨ Unicode 编码结果：\n" + sb;
    }

    private String unicodeDecode(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 5 < text.length() && text.charAt(i) == '\\' && text.charAt(i + 1) == 'u') {
                String hex = text.substring(i + 2, i + 6);
                try {
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            sb.append(text.charAt(i));
            i++;
        }
        return "✨ Unicode 解码结果：\n" + sb;
    }
}
