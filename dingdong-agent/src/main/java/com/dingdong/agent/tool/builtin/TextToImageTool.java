package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * 文生图工具。调用 OpenAI 兼容的图片生成 API。
 * 返回特殊标记 [IMAGE:url=xxx] 供 Bot 层检测并发送图片消息。
 * 商汤等 API 直接返回图片 URL，无需下载到本地，NapCat 支持直接通过 URL 发送图片。
 */
@Slf4j
@Component
public class TextToImageTool {

    private static final String IMAGE_DIR = "generated_images";

    /** 商汤 API 支持的合法尺寸集合 */
    private static final java.util.Set<String> VALID_SIZES = java.util.Set.of(
            "1664x2496", "2496x1664", "1760x2368", "2368x1760",
            "1824x2272", "2272x1824", "2048x2048",
            "2752x1536", "1536x2752", "3072x1376", "1344x3136"
    );

    /** 获取图片目录的绝对路径 */
    private Path getImageDir() {
        return Paths.get(IMAGE_DIR).toAbsolutePath();
    }

    /** 文生图配置，由 NapCatAutoConfiguration 注入 */
    private String baseUrl;
    private String apiKey;
    private String model;
    private String size;
    private String quality;
    private long timeout;
    private boolean enabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 由自动配置调用，注入文生图 API 配置。
     */
    public void configure(String baseUrl, String apiKey, String model, String size, String quality, long timeout, boolean enabled) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.size = size;
        this.quality = quality;
        this.timeout = timeout;
        this.enabled = enabled;
        log.info("TextToImageTool configured: enabled={}, baseUrl={}, model={}", enabled, baseUrl, model);
    }

    public boolean isEnabled() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    @Tool(
        name = "text_to_image",
        description = "根据文字描述生成图片。当用户说\"画一张\"\"生成图片\"\"帮我画\"\"文生图\"或回复内容太长需要以图片形式展示时使用。\n" +
                      "示例：\n" +
                      "- prompt=\"一只可爱的猫咪在月球上\" → 生成一张猫咪在月球的图片\n" +
                      "- prompt=\"赛博朋克风格的城市夜景\" → 生成赛博朋克城市图片\n" +
                      "- prompt=\"把以下内容做成图片：你好世界\" → 生成包含文字的图片"
    )
    public String generateImage(
        @ToolParam(value = "prompt", description = "图片描述/生成提示词，详细描述要生成的图片内容", required = true) String prompt,
        @ToolParam(value = "size", description = "图片尺寸（可选），不填使用默认尺寸。" +
                      "合法值只能从以下选择：2048x2048(1:1)、1664x2496(2:3竖图)、2496x1664(3:2横图)、" +
                      "1760x2368(3:4竖图)、2368x1760(4:3横图)、1824x2272(4:5)、2272x1824(5:4)、" +
                      "2752x1536(16:9宽屏)、1536x2752(9:16竖屏)、3072x1376(21:9)、1344x3136(9:21)") String imageSize
    ) {
        if (!enabled) {
            return "❌ 文生图功能未启用，请在配置中设置 dingdong.agent.text-to-image.enabled=true";
        }
        if (prompt == null || prompt.isBlank()) {
            return "❌ 请提供图片描述";
        }

        try {
            // 构建请求 JSON，自动纠正非法尺寸
            String effectiveSize = (imageSize != null && !imageSize.isBlank()) ? imageSize : this.size;
            effectiveSize = validateSize(effectiveSize);
            String requestBody = buildRequestBody(prompt, effectiveSize);

            // baseUrl 只到 /v1，拼接 /images/generations
            String endpoint = baseUrl.endsWith("/") ? baseUrl + "images/generations" : baseUrl + "/images/generations";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.info("TextToImage request: prompt={}, size={}", prompt, effectiveSize);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("TextToImage API error: status={}, body={}", response.statusCode(), response.body());
                return "❌ 图片生成失败（HTTP " + response.statusCode() + "）";
            }

            // 解析响应，提取图片 URL 或 base64
            String imageUrl = extractImageUrl(response.body());
            if (imageUrl == null || imageUrl.isBlank()) {
                return "❌ 图片生成成功但无法解析结果\n响应：" + truncate(response.body(), 200);
            }

            // 直接返回 URL 标记，Bot 层通过 NapCat 发送图片（无需下载）
            if (imageUrl.startsWith("data:image")) {
                // base64 图片仍需保存为本地文件
                String filename = saveBase64Image(imageUrl);
                if (filename == null) return "❌ 图片保存失败";
                return "[IMAGE:path=" + getImageDir().resolve(filename).toString() + "]";
            }
            // URL 直接返回，NapCat 支持通过 URL 发送图片
            return "[IMAGE:url=" + imageUrl + "]";

        } catch (Exception e) {
            log.error("TextToImage failed: prompt={}", prompt, e);
            return "❌ 图片生成出错：" + e.getMessage();
        }
    }

    private String buildRequestBody(String prompt, String size) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"prompt\":\"").append(escapeJson(prompt)).append("\"");
        sb.append(",\"model\":\"").append(escapeJson(model)).append("\"");
        sb.append(",\"n\":1");
        sb.append(",\"size\":\"").append(escapeJson(size)).append("\"");
        if (quality != null && !quality.isBlank()) {
            sb.append(",\"quality\":\"").append(escapeJson(quality)).append("\"");
        }
        sb.append(",\"response_format\":\"url\"");
        sb.append("}");
        return sb.toString();
    }

    /**
     * 从 API 响应中提取图片 URL。
     * 兼容 OpenAI 格式：{"data":[{"url":"..."}]} 或 {"data":[{"b64_json":"..."}]}
     */
    private String extractImageUrl(String responseBody) {
        if (responseBody == null) return null;

        // 尝试提取 "url":"..."
        int urlIdx = responseBody.indexOf("\"url\"");
        if (urlIdx >= 0) {
            int colonIdx = responseBody.indexOf(':', urlIdx);
            if (colonIdx >= 0) {
                int startQuote = responseBody.indexOf('"', colonIdx + 1);
                if (startQuote >= 0) {
                    int endQuote = findEndQuote(responseBody, startQuote + 1);
                    if (endQuote > startQuote) {
                        return responseBody.substring(startQuote + 1, endQuote);
                    }
                }
            }
        }

        // 尝试提取 b64_json（base64 图片）
        int b64Idx = responseBody.indexOf("\"b64_json\"");
        if (b64Idx >= 0) {
            int colonIdx = responseBody.indexOf(':', b64Idx);
            if (colonIdx >= 0) {
                int startQuote = responseBody.indexOf('"', colonIdx + 1);
                if (startQuote >= 0) {
                    int endQuote = findEndQuote(responseBody, startQuote + 1);
                    if (endQuote > startQuote) {
                        String b64 = responseBody.substring(startQuote + 1, endQuote);
                        // 保存 base64 为临时文件并返回 data URI
                        return "data:image/png;base64," + b64;
                    }
                }
            }
        }

        return null;
    }

    private int findEndQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '"' && s.charAt(i - 1) != '\\') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 保存 base64 图片到本地（仅 base64 格式需要，URL 格式不需要）
     */
    private String saveBase64Image(String dataUri) {
        try {
            Path dir = getImageDir();
            Files.createDirectories(dir);
            String filename = "img_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            Path targetPath = dir.resolve(filename);
            String b64Data = dataUri.substring(dataUri.indexOf(',') + 1);
            byte[] imageBytes = java.util.Base64.getDecoder().decode(b64Data);
            Files.write(targetPath, imageBytes);
            log.info("Base64 image saved: {}", targetPath);
            return filename;
        } catch (Exception e) {
            log.error("Failed to save base64 image", e);
            return null;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /**
     * 校验尺寸是否合法，不合法时根据宽高比自动匹配最接近的合法尺寸。
     */
    private String validateSize(String size) {
        if (size == null || size.isBlank()) return this.size;
        if (VALID_SIZES.contains(size)) return size;

        // 尝试解析宽高比，匹配最接近的合法尺寸
        log.warn("Invalid image size '{}', auto-mapping to nearest valid size", size);
        try {
            String[] parts = size.toLowerCase().split("x");
            if (parts.length == 2) {
                double w = Double.parseDouble(parts[0].trim());
                double h = Double.parseDouble(parts[1].trim());
                double targetRatio = w / h;

                String bestSize = this.size;
                double bestDiff = Double.MAX_VALUE;
                for (String valid : VALID_SIZES) {
                    String[] vp = valid.split("x");
                    double vw = Double.parseDouble(vp[0]);
                    double vh = Double.parseDouble(vp[1]);
                    double diff = Math.abs((vw / vh) - targetRatio);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestSize = valid;
                    }
                }
                log.info("Mapped size '{}' -> '{}' (ratio diff={})", size, bestSize, bestDiff);
                return bestSize;
            }
        } catch (Exception ignored) {}

        // 无法解析时回退到默认尺寸
        return this.size;
    }
}
