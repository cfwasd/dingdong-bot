package com.napcat.agent.tool.builtin;

import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * 本地文件操作工具。支持列出目录、读取文件内容、查看文件信息、创建目录。
 * 限制在 downloads 目录内操作，防止越权访问。
 */
@Slf4j
@Component
public class LocalFileTool {

    private static final String BASE_DIR = "downloads";
    private static final int MAX_READ_SIZE = 8000; // 最大读取字符数
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool(
        name = "local_file",
        description = "本地文件操作工具。支持以下功能：\n" +
                      "- action=\"list\" → 列出 downloads 目录下的文件\n" +
                      "- action=\"read\", path=\"文件名\" → 读取文件内容（文本文件）\n" +
                      "- action=\"info\", path=\"文件名\" → 查看文件详细信息（大小、修改时间等）\n" +
                      "- action=\"mkdir\", path=\"目录名\" → 在 downloads 下创建子目录\n" +
                      "- action=\"delete\", path=\"文件名\" → 删除指定文件\n" +
                      "所有操作限制在 downloads 目录内。"
    )
    public String operate(
        @ToolParam(value = "action", description = "操作类型：list(列目录)、read(读文件)、info(文件信息)、mkdir(创建目录)、delete(删除文件)",
                   enums = {"list", "read", "info", "mkdir", "delete"}, required = true) String action,
        @ToolParam(value = "path", description = "文件路径（相对于 downloads 目录），如 \"test.txt\" 或 \"subdir/file.txt\"") String path
    ) {
        if (action == null || action.isBlank()) {
            return "❌ 请指定操作类型（list/read/info/mkdir/delete）";
        }

        try {
            Path baseDir = Paths.get(BASE_DIR).toAbsolutePath().normalize();
            Files.createDirectories(baseDir);

            return switch (action.toLowerCase().trim()) {
                case "list" -> listFiles(baseDir, path);
                case "read" -> readFile(baseDir, path);
                case "info" -> fileInfo(baseDir, path);
                case "mkdir" -> makeDir(baseDir, path);
                case "delete" -> deleteFile(baseDir, path);
                default -> "❌ 未知操作：" + action + "，支持：list、read、info、mkdir、delete";
            };
        } catch (Exception e) {
            log.error("Local file operation failed: action={}, path={}", action, path, e);
            return "❌ 操作失败：" + e.getMessage();
        }
    }

    private String listFiles(Path baseDir, String subPath) throws IOException {
        Path targetDir = resolveSafePath(baseDir, subPath);
        if (targetDir == null) {
            return "❌ 路径不合法，不能访问 downloads 目录外的文件";
        }
        if (!Files.isDirectory(targetDir)) {
            return "❌ 目录不存在：" + (subPath == null ? "downloads" : subPath);
        }

        try (Stream<Path> stream = Files.list(targetDir)) {
            var entries = stream.sorted().toList();
            if (entries.isEmpty()) {
                return "📂 目录为空";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📂 ").append(subPath == null || subPath.isBlank() ? "downloads" : subPath).append(" 目录：\n");
            int count = 0;
            for (Path entry : entries) {
                if (count >= 50) {
                    sb.append("... 还有 ").append(entries.size() - 50).append(" 个文件\n");
                    break;
                }
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    sb.append("📁 ").append(name).append("/\n");
                } else {
                    long size = Files.size(entry);
                    sb.append("📄 ").append(name).append(" (").append(formatSize(size)).append(")\n");
                }
                count++;
            }
            sb.append("\n共 ").append(entries.size()).append(" 个文件/目录");
            return sb.toString();
        }
    }

    private String readFile(Path baseDir, String path) throws IOException {
        if (path == null || path.isBlank()) {
            return "❌ 请指定要读取的文件路径";
        }

        Path target = resolveSafePath(baseDir, path);
        if (target == null) {
            return "❌ 路径不合法";
        }
        if (!Files.exists(target)) {
            return "❌ 文件不存在：" + path;
        }
        if (Files.isDirectory(target)) {
            return "❌ 这是一个目录，不是文件。请用 list 操作查看目录内容";
        }

        long size = Files.size(target);
        if (size > 1024 * 1024) {
            return "❌ 文件过大（" + formatSize(size) + "），不支持直接读取";
        }

        String content = Files.readString(target);
        if (content.length() > MAX_READ_SIZE) {
            content = content.substring(0, MAX_READ_SIZE) + "\n...（内容过长，已截断）";
        }

        return "📄 " + path + " 的内容：\n\n" + content;
    }

    private String fileInfo(Path baseDir, String path) throws IOException {
        if (path == null || path.isBlank()) {
            return "❌ 请指定文件路径";
        }

        Path target = resolveSafePath(baseDir, path);
        if (target == null) {
            return "❌ 路径不合法";
        }
        if (!Files.exists(target)) {
            return "❌ 文件不存在：" + path;
        }

        BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
        StringBuilder sb = new StringBuilder();
        sb.append("📋 文件信息：").append(path).append("\n");
        sb.append("类型：").append(attrs.isDirectory() ? "📁 目录" : "📄 文件").append("\n");

        if (attrs.isRegularFile()) {
            sb.append("大小：").append(formatSize(attrs.size())).append("\n");
            String fileName = target.getFileName().toString();
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx > 0) {
                sb.append("扩展名：").append(fileName.substring(dotIdx)).append("\n");
            }
        }

        sb.append("创建时间：").append(formatTime(attrs.creationTime().toMillis())).append("\n");
        sb.append("修改时间：").append(formatTime(attrs.lastModifiedTime().toMillis())).append("\n");
        sb.append("最后访问：").append(formatTime(attrs.lastAccessTime().toMillis())).append("\n");

        if (attrs.isRegularFile()) {
            sb.append("可读：").append(Files.isReadable(target) ? "✅" : "❌").append("  ");
            sb.append("可写：").append(Files.isWritable(target) ? "✅" : "❌").append("\n");
        }

        return sb.toString();
    }

    private String makeDir(Path baseDir, String path) throws IOException {
        if (path == null || path.isBlank()) {
            return "❌ 请指定目录名";
        }

        Path target = resolveSafePath(baseDir, path);
        if (target == null) {
            return "❌ 路径不合法";
        }
        if (Files.exists(target)) {
            return "⚠️ 目录已存在：" + path;
        }

        Files.createDirectories(target);
        return "✅ 目录创建成功：" + path;
    }

    private String deleteFile(Path baseDir, String path) throws IOException {
        if (path == null || path.isBlank()) {
            return "❌ 请指定要删除的文件路径";
        }

        Path target = resolveSafePath(baseDir, path);
        if (target == null) {
            return "❌ 路径不合法";
        }
        if (!Files.exists(target)) {
            return "❌ 文件不存在：" + path;
        }
        if (Files.isDirectory(target)) {
            return "❌ 不支持删除目录，请指定具体文件";
        }

        long size = Files.size(target);
        Files.delete(target);
        return "✅ 文件已删除：" + path + "（原大小：" + formatSize(size) + "）";
    }

    /**
     * 安全路径解析：确保目标路径在 baseDir 内，防止路径穿越
     */
    private Path resolveSafePath(Path baseDir, String subPath) {
        if (subPath == null || subPath.isBlank()) {
            return baseDir;
        }
        Path target = baseDir.resolve(subPath).normalize();
        if (!target.startsWith(baseDir)) {
            return null; // 路径穿越
        }
        return target;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(FMT);
    }
}
