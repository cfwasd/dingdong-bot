package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 日期倒计时工具。计算距离目标日期还有多少天，支持常见节日自动识别。
 */
@Slf4j
@Component
public class CountdownTool {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter CN_FMT = DateTimeFormatter.ofPattern("yyyy年M月d日");

    /** 常见节日/事件固定日期（月-日） */
    private static final Map<String, int[]> FIXED_DATES = Map.ofEntries(
            Map.entry("元旦", new int[]{1, 1}), Map.entry("劳动节", new int[]{5, 1}), Map.entry("五一", new int[]{5, 1}),
            Map.entry("国庆节", new int[]{10, 1}), Map.entry("十一", new int[]{10, 1}),
            Map.entry("儿童节", new int[]{6, 1}), Map.entry("六一", new int[]{6, 1}),
            Map.entry("建军节", new int[]{8, 1}), Map.entry("八一", new int[]{8, 1}),
            Map.entry("教师节", new int[]{9, 10}),
            Map.entry("圣诞节", new int[]{12, 25})
    );

    @Tool(
        name = "countdown",
        description = "日期倒计时/日期计算工具。计算距离某个日期还有多少天，或两个日期之间的间隔。\n" +
                      "当用户问\"距离XX还有多少天\"\"XX倒计时\"\"还有多久到XX\"时使用。\n" +
                      "支持：\n" +
                      "- 具体日期：date=\"2025-12-25\"\n" +
                      "- 节日名称：date=\"春节\" \"中秋\" \"高考\" \"元旦\" \"国庆\"\n" +
                      "- 相对天数：date=\"100天后\" \"30天后\"\n" +
                      "- 日期差：from=\"2025-01-01\", to=\"2025-12-31\""
    )
    public String countdown(
        @ToolParam(value = "date", description = "目标日期或关键词，如 \"2025-12-25\"、\"春节\"、\"高考\"、\"100天后\"") String date,
        @ToolParam(value = "from", description = "起始日期（可选），格式 yyyy-MM-dd，默认今天。用于计算两个日期之间的间隔") String from,
        @ToolParam(value = "to", description = "结束日期（可选），格式 yyyy-MM-dd。与 from 配合使用计算日期差") String to
    ) {
        try {
            LocalDate today = LocalDate.now();

            // 模式1：计算两个日期之间的间隔
            if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
                return calcDateDiff(parseDate(from, today), parseDate(to, today));
            }

            if (date == null || date.isBlank()) {
                return "❌ 请指定目标日期或关键词，如 \"春节\" \"2025-12-25\" \"100天后\"";
            }

            String trimmed = date.trim();

            // 模式2：相对天数 "X天后" / "X天前"
            if (trimmed.matches("\\d+天后?")) {
                int days = Integer.parseInt(trimmed.replaceAll("[^0-9]", ""));
                LocalDate target = today.plusDays(days);
                return "📅 从今天（" + today.format(CN_FMT) + "）起 " + days + " 天后是 " + target.format(CN_FMT) + "（" + getWeekDay(target) + "）";
            }
            if (trimmed.matches("\\d+天前?")) {
                int days = Integer.parseInt(trimmed.replaceAll("[^0-9]", ""));
                LocalDate target = today.minusDays(days);
                return "📅 从今天（" + today.format(CN_FMT) + "）往前 " + days + " 天是 " + target.format(CN_FMT) + "（" + getWeekDay(target) + "）";
            }

            // 模式3：节日/事件关键词
            LocalDate target = resolveEvent(trimmed, today);
            if (target != null) {
                return formatCountdown(trimmed, today, target);
            }

            // 模式4：具体日期
            try {
                LocalDate targetDate = LocalDate.parse(trimmed, DATE_FMT);
                return formatCountdown(trimmed, today, targetDate);
            } catch (Exception e) {
                return "❌ 无法识别日期：" + trimmed + "\n支持格式：yyyy-MM-dd、节日名称（春节/中秋/高考等）、X天后";
            }

        } catch (Exception e) {
            log.error("Countdown failed: date={}", date, e);
            return "❌ 日期计算失败：" + e.getMessage();
        }
    }

    private String formatCountdown(String name, LocalDate today, LocalDate target) {
        long days = ChronoUnit.DAYS.between(today, target);
        String targetStr = target.format(CN_FMT) + "（" + getWeekDay(target) + "）";

        if (days == 0) {
            return "🎉 " + name + " 就是今天！（" + targetStr + "）";
        } else if (days > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("⏳ 距离 ").append(name).append(" 还有 ");
            if (days >= 365) {
                sb.append(days / 365).append(" 年 ");
                sb.append((days % 365) / 30).append(" 个月 ");
                sb.append((days % 365) % 30).append(" 天");
            } else if (days >= 30) {
                sb.append(days / 30).append(" 个月 ");
                sb.append(days % 30).append(" 天");
            } else {
                sb.append(days).append(" 天");
            }
            sb.append("\n📅 目标日期：").append(targetStr);
            return sb.toString();
        } else {
            return "📅 " + name + " 已经过去 " + Math.abs(days) + " 天了（" + targetStr + "）";
        }
    }

    private String calcDateDiff(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days == 0) return "📅 两个日期是同一天：" + from.format(CN_FMT);
        if (days > 0) {
            return "📅 " + from.format(CN_FMT) + " 到 " + to.format(CN_FMT) + " 共 " + days + " 天";
        }
        return "📅 " + to.format(CN_FMT) + " 到 " + from.format(CN_FMT) + " 共 " + Math.abs(days) + " 天";
    }

    /**
     * 解析节日/事件关键词对应的日期
     */
    private LocalDate resolveEvent(String keyword, LocalDate today) {
        int year = today.getYear();

        // 固定日期节日
        for (var entry : FIXED_DATES.entrySet()) {
            if (keyword.contains(entry.getKey())) {
                LocalDate d = LocalDate.of(year, entry.getValue()[0], entry.getValue()[1]);
                if (d.isBefore(today)) d = LocalDate.of(year + 1, entry.getValue()[0], entry.getValue()[1]);
                return d;
            }
        }

        // 农历节日（近似计算，使用当年公历近似值）
        return resolveLunarFestival(keyword, today, year);
    }

    /**
     * 农历节日近似公历日期（2025-2030年范围）
     */
    private LocalDate resolveLunarFestival(String keyword, LocalDate today, int year) {
        // 春节、端午、中秋的近似公历日期表
        var springFestivals = Map.of(
                2025, LocalDate.of(2025, 1, 29), 2026, LocalDate.of(2026, 2, 17),
                2027, LocalDate.of(2027, 2, 6), 2028, LocalDate.of(2028, 1, 26),
                2029, LocalDate.of(2029, 2, 13), 2030, LocalDate.of(2030, 2, 3));
        var dragonBoatFestivals = Map.of(
                2025, LocalDate.of(2025, 5, 31), 2026, LocalDate.of(2026, 6, 19),
                2027, LocalDate.of(2027, 6, 9), 2028, LocalDate.of(2028, 5, 28),
                2029, LocalDate.of(2029, 6, 16), 2030, LocalDate.of(2030, 6, 5));
        var midAutumnFestivals = Map.of(
                2025, LocalDate.of(2025, 10, 6), 2026, LocalDate.of(2026, 9, 25),
                2027, LocalDate.of(2027, 9, 15), 2028, LocalDate.of(2028, 10, 3),
                2029, LocalDate.of(2029, 9, 22), 2030, LocalDate.of(2030, 9, 12));
        var qingmingFestivals = Map.of(
                2025, LocalDate.of(2025, 4, 4), 2026, LocalDate.of(2026, 4, 5),
                2027, LocalDate.of(2027, 4, 5), 2028, LocalDate.of(2028, 4, 4),
                2029, LocalDate.of(2029, 4, 4), 2030, LocalDate.of(2030, 4, 5));
        var gaokao = Map.of(
                2025, LocalDate.of(2025, 6, 7), 2026, LocalDate.of(2026, 6, 7),
                2027, LocalDate.of(2027, 6, 7), 2028, LocalDate.of(2028, 6, 7),
                2029, LocalDate.of(2029, 6, 7), 2030, LocalDate.of(2030, 6, 7));

        if (keyword.contains("春节") || keyword.contains("过年")) {
            return getNextFromMap(springFestivals, today, year);
        }
        if (keyword.contains("端午") || keyword.contains("粽子")) {
            return getNextFromMap(dragonBoatFestivals, today, year);
        }
        if (keyword.contains("中秋") || keyword.contains("月饼")) {
            return getNextFromMap(midAutumnFestivals, today, year);
        }
        if (keyword.contains("清明")) {
            return getNextFromMap(qingmingFestivals, today, year);
        }
        if (keyword.contains("高考")) {
            return getNextFromMap(gaokao, today, year);
        }
        return null;
    }

    private LocalDate getNextFromMap(Map<Integer, LocalDate> map, LocalDate today, int year) {
        LocalDate d = map.get(year);
        if (d != null && !d.isBefore(today)) return d;
        d = map.get(year + 1);
        return d;
    }

    private LocalDate parseDate(String dateStr, LocalDate defaultDate) {
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FMT);
        } catch (Exception e) {
            return defaultDate;
        }
    }

    private String getWeekDay(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }
}
