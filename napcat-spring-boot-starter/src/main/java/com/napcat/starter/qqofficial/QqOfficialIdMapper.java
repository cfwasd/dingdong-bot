package com.napcat.starter.qqofficial;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 将 QQ 官方 openid 字符串稳定映射为 NapCatAgent 可用的 long 会话键。
 */
public class QqOfficialIdMapper {

    public long toUserId(String openid) {
        return stablePositiveLong("qqofficial:user:" + safe(openid));
    }

    public long toGroupId(String groupOpenid) {
        if (groupOpenid == null || groupOpenid.isBlank()) {
            return 0L;
        }
        return stablePositiveLong("qqofficial:group:" + safe(groupOpenid));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static long stablePositiveLong(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (bytes[i] & 0xffL);
            }
            long positive = result & Long.MAX_VALUE;
            return positive == 0L ? 1L : positive;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
