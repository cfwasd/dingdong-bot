package com.napcat.core.tts;

import com.napcat.core.scheduler.DbManager;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用户语音偏好存储。每个用户独立配置语音模式（default/voice/text）。
 * 持久化到 SQLite user_preferences 表。
 */
@Slf4j
public class VoicePreferenceStore {

    private final DbManager dbManager;

    /** 语音模式枚举 */
    public enum VoiceMode {
        /** 50% 概率语音，50% 概率文字 */
        DEFAULT,
        /** 每次都发语音 */
        VOICE,
        /** 只发文字 */
        TEXT;

        public static VoiceMode fromString(String s) {
            if (s == null) return DEFAULT;
            return switch (s.toLowerCase().trim()) {
                case "voice", "on" -> VOICE;
                case "text", "off" -> TEXT;
                default -> DEFAULT;
            };
        }

        public String toDisplayString() {
            return switch (this) {
                case DEFAULT -> "默认（50%概率语音）";
                case VOICE -> "语音模式（每次语音）";
                case TEXT -> "文字模式（不发语音）";
            };
        }
    }

    public VoicePreferenceStore(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    /** 建表 DDL */
    public static String ddl() {
        return "CREATE TABLE IF NOT EXISTS user_preferences (" +
                "user_id INTEGER NOT NULL," +
                "pref_key TEXT NOT NULL," +
                "pref_value TEXT NOT NULL," +
                "updated_at TEXT DEFAULT (datetime('now'))," +
                "PRIMARY KEY (user_id, pref_key)" +
                ")";
    }

    /**
     * 获取用户的语音模式。
     */
    public VoiceMode getVoiceMode(long userId) {
        String sql = "SELECT pref_value FROM user_preferences WHERE user_id = ? AND pref_key = 'voice_mode'";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return VoiceMode.fromString(rs.getString("pref_value"));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get voice mode for user {}", userId, e);
        }
        return VoiceMode.DEFAULT;
    }

    /**
     * 设置用户的语音模式。
     */
    public boolean setVoiceMode(long userId, VoiceMode mode) {
        String sql = "INSERT INTO user_preferences (user_id, pref_key, pref_value, updated_at) " +
                "VALUES (?, ?, ?, datetime('now')) " +
                "ON CONFLICT(user_id, pref_key) DO UPDATE SET pref_value = ?, updated_at = datetime('now')";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, "voice_mode");
            String value = mode.name().toLowerCase();
            ps.setString(3, value);
            ps.setString(4, value);
            ps.executeUpdate();
            log.info("Voice mode set to {} for user {}", mode, userId);
            return true;
        } catch (SQLException e) {
            log.error("Failed to set voice mode for user {}", userId, e);
            return false;
        }
    }
}
