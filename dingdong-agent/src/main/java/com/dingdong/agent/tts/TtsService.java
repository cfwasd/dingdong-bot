package com.dingdong.agent.tts;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TTS 语音合成服务。兼容 OpenAI /v1/audio/speech 接口格式。
 * <p>
 * 调用外部 TTS API 将文本转为语音文件（MP3），返回本地文件路径。
 * 支持根据人格声线配置（voice profile）使用不同的声线和语速。
 */
@Slf4j
public class TtsService {

    /** TTS 配置 */
    @Data
    public static class TtsConfig {
        private boolean enabled = false;
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private String format = "mp3";
        private double speed = 1.0;
        private String defaultVoice = "alloy";
        private long timeout = 30000;
        private int maxTextLength = 500;
        private Map<String, VoiceProfile> voiceProfiles = new LinkedHashMap<>();
    }

    /** 单个声线配置 */
    @Data
    public static class VoiceProfile {
        private String voice = "alloy";
        private double speed = 1.0;
        /** 音调（VoiceCraft 特有），如 "+0Hz"、"-50Hz"、"+100Hz" */
        private String pitch = "0Hz";
        /** 语音风格（VoiceCraft 特有），如 general/cheerful/sad/angry/fearful/disgruntled/serious/affectionate/terrified/shouting/whispering */
        private String style = "general";
    }

    private final TtsConfig config;
    private final HttpClient httpClient;
    private final Path audioDir;

    public TtsService(TtsConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.audioDir = Path.of("dingdong_data", "audio");
        try {
            Files.createDirectories(audioDir);
        } catch (Exception e) {
            log.warn("Failed to create audio dir: {}", audioDir, e);
        }
    }

    /**
     * 判断 TTS 功能是否可用。
     */
    public boolean isEnabled() {
        return config.isEnabled() && config.getBaseUrl() != null && !config.getBaseUrl().isBlank();
    }

    /**
     * 将文本合成为语音文件。
     *
     * @param text            要合成的文本
     * @param voiceProfileName 声线配置名称（对应 tts.voice-profiles 中的 key），可为 null 使用默认
     * @return 生成的音频文件绝对路径，失败返回 null
     */
    public String synthesize(String text, String voiceProfileName) {
        if (!isEnabled()) return null;
        if (text == null || text.isBlank()) return null;

        // 超长文本截断
        String inputText = text;
        if (inputText.length() > config.getMaxTextLength()) {
            inputText = inputText.substring(0, config.getMaxTextLength());
            log.debug("TTS text truncated from {} to {} chars", text.length(), inputText.length());
        }

        // 解析声线参数
        String voice = config.getDefaultVoice();
        double speed = config.getSpeed();
        String pitch = "0Hz";
        String style = "general";
        if (voiceProfileName != null && !voiceProfileName.isBlank()) {
            VoiceProfile profile = config.getVoiceProfiles().get(voiceProfileName);
            if (profile != null) {
                voice = profile.getVoice();
                speed = profile.getSpeed();
                pitch = profile.getPitch() != null ? profile.getPitch() : "0Hz";
                style = profile.getStyle() != null ? profile.getStyle() : "general";
                log.debug("Using voice profile '{}': voice={}, speed={}, pitch={}, style={}", voiceProfileName, voice, speed, pitch, style);
            } else {
                log.debug("Voice profile '{}' not found, using defaults", voiceProfileName);
            }
        }

        try {
            // 构建请求体（OpenAI /v1/audio/speech 兼容格式 + VoiceCraft 扩展参数）
            String requestBody = buildRequestBody(inputText, voice, speed, pitch, style);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl()))
                    .timeout(Duration.ofMillis(config.getTimeout()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.debug("TTS request: voice={}, speed={}, text={} chars", voice, speed, inputText.length());
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body());
                log.error("TTS API error: status={}, body={}", response.statusCode(), errorBody);
                return null;
            }

            // 保存音频文件
            String filename = "tts_" + UUID.randomUUID().toString().substring(0, 8) + "." + config.getFormat();
            Path targetPath = audioDir.resolve(filename);
            Files.write(targetPath, response.body());

            log.info("TTS audio saved: {} ({} bytes, voice={}, speed={})", filename, response.body().length, voice, speed);
            return targetPath.toAbsolutePath().toString();

        } catch (Exception e) {
            log.error("TTS synthesis failed", e);
            return null;
        }
    }

    /**
     * 清理过期的音频文件（超过 1 小时的）。
     * 可由定时任务或手动调用。
     */
    public void cleanupOldFiles() {
        try {
            long cutoff = System.currentTimeMillis() - 3600_000; // 1 hour
            try (var stream = Files.list(audioDir)) {
                stream.filter(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis() < cutoff;
                    } catch (Exception e) {
                        return false;
                    }
                }).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                        log.debug("Cleaned up old audio file: {}", p.getFileName());
                    } catch (Exception e) {
                        log.warn("Failed to delete audio file: {}", p, e);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup audio files", e);
        }
    }

    private String buildRequestBody(String text, String voice, double speed, String pitch, String style) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(escapeJson(config.getModel())).append("\"");
        sb.append(",\"input\":\"").append(escapeJson(text)).append("\"");
        sb.append(",\"voice\":\"").append(escapeJson(voice)).append("\"");
        sb.append(",\"response_format\":\"").append(escapeJson(config.getFormat())).append("\"");
        sb.append(",\"speed\":").append(speed);
        // VoiceCraft 扩展参数
        if (pitch != null && !"0Hz".equals(pitch)) {
            sb.append(",\"pitch\":\"").append(escapeJson(pitch)).append("\"");
        }
        if (style != null && !"general".equals(style)) {
            sb.append(",\"style\":\"").append(escapeJson(style)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
