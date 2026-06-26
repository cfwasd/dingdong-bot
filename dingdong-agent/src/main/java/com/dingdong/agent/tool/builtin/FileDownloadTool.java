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
import java.nio.file.*;
import java.time.Duration;

/**
 * 文件下载工具。从 URL 下载文件到本地指定目录。
 */
@Slf4j
@Component
public class FileDownloadTool {

    private static final String DEFAULT_DIR = "downloads";
    private static final long MAX_SIZE = 100 * 1024 * 1024; // 100MB

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool(
        name = "file_download",
        description = "从指定 URL 下载文件到本地。\n" +
                      "当用户要求下载文件、保存图片、获取网络资源时使用。\n" +
                      "示例：\n" +
                      "- url=\"https://example.com/image.png\" → 下载到 downloads/image.png\n" +
                      "- url=\"https://example.com/doc.pdf\", filename=\"文档.pdf\" → 下载到 downloads/文档.pdf"
    )
    public String download(
        @ToolParam(value = "url", description = "要下载的文件 URL 地址", required = true) String url,
        @ToolParam(value = "filename", description = "保存的文件名（可选），不填则从 URL 自动提取") String filename
    ) {
        if (url == null || url.isBlank()) {
            return "❌ 请提供下载链接";
        }

        try {
            // 校验 URL
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return "❌ 仅支持 HTTP/HTTPS 链接";
            }

            // 确定文件名
            if (filename == null || filename.isBlank()) {
                filename = extractFilename(url, uri);
            }
            if (filename == null || filename.isBlank()) {
                filename = "download_" + System.currentTimeMillis();
            }

            // 安全校验：防止路径穿越
            filename = filename.replaceAll("[/\\\\:*?\"<>|]", "_");
            if (filename.contains("..")) {
                filename = filename.replace("..", "_");
            }

            // 创建下载目录
            Path downloadDir = Paths.get(DEFAULT_DIR);
            Files.createDirectories(downloadDir);

            Path targetPath = downloadDir.resolve(filename);
            // 防止路径穿越
            if (!targetPath.normalize().startsWith(downloadDir.normalize())) {
                return "❌ 文件名不合法";
            }

            // 下载文件
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(120))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                return "❌ 下载失败，HTTP 状态码：" + response.statusCode();
            }

            // 检查 Content-Length
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > MAX_SIZE) {
                return "❌ 文件过大（" + formatSize(contentLength) + "），超过 100MB 限制";
            }

            // 写入文件
            try (InputStream is = response.body()) {
                long bytesWritten = Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                if (bytesWritten > MAX_SIZE) {
                    Files.deleteIfExists(targetPath);
                    return "❌ 文件过大，下载已取消";
                }
                return "✅ 文件下载成功！\n" +
                       "📁 保存路径：" + targetPath.toString() + "\n" +
                       "📦 文件大小：" + formatSize(bytesWritten);
            }

        } catch (Exception e) {
            log.error("File download failed: {}", url, e);
            return "❌ 下载失败：" + e.getMessage();
        }
    }

    private String extractFilename(String url, URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            return null;
        }
        String name = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            name = path.substring(lastSlash + 1);
        }
        // 去掉查询参数残留
        int queryIdx = name.indexOf('?');
        if (queryIdx > 0) {
            name = name.substring(0, queryIdx);
        }
        return name;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
