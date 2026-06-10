package com.napcat.agent.tool.builtin;

import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 节假日/纪念日计算工具。群聊中查距离放假还有多久、查询节日信息。
 */
@Slf4j
@Component
public class HolidayTool {

    /** 固定日期节假日 */
    private static final List<Holiday> FIXED_HOLIDAYS = List.of(
            new Holiday("元旦", 1, 1, true),
            new Holiday("情人节", 2, 14, false),
            new Holiday("妇女节", 3, 8, false),
            new Holiday("植树节", 3, 12, false),
            new Holiday("愚人节", 4, 1, false),
            new Holiday("劳动节", 5, 1, true),
            new Holiday("青年节", 5, 4, false),
            new Holiday("儿童节", 6, 1, false),
            new Holiday("建党节", 7, 1, false),
            new Holiday("建军节", 8, 1, false),
            new Holiday("教师节", 9, 10, false),
            new Holiday("国庆节", 10, 1, true),
            new Holiday("程序员节", 10, 24, false),
            new Holiday("万圣节", 10, 31, false),
            new Holiday("光棍节", 11, 11, false),
            new Holiday("平安夜", 12, 24, false),
            new Holiday("圣诞节", 12, 25, false),
            new Holiday("小寒", 1, 5, false),
            new Holiday("大寒", 1, 20, false),
            new Holiday("立春", 2, 3, false),
            new Holiday("雨水", 2, 19, false),
            new Holiday("惊蛰", 3, 5, false),
            new Holiday("春分", 3, 20, false),
            new Holiday("清明", 4, 5, true),
            new Holiday("谷雨", 4, 20, false),
            new Holiday("立夏", 5, 5, false),
            new Holiday("小满", 5, 21, false),
            new Holiday("夏至", 6, 21, false),
            new Holiday("小暑", 7, 7, false),
            new Holiday("大暑", 7, 23, false),
            new Holiday("立秋", 8, 7, false),
            new Holiday("处暑", 8, 23, false),
            new Holiday("白露", 9, 8, false),
            new Holiday("秋分", 9, 23, false),
            new Holiday("寒露", 10, 8, false),
            new Holiday("霜降", 10, 23, false),
            new Holiday("立冬", 11, 7, false),
            new Holiday("小雪", 11, 22, false),
            new Holiday("大雪", 12, 7, false),
            new Holiday("冬至", 12, 21, false)
    );

    private record Holiday(String name, int month, int day, boolean isOff) {}

    @Tool(
        name = "holiday_countdown",
        description = "节假日/纪念日倒计时。当用户说\"还有几天放假\"\"距离春节还有多久\"\"什么时候过节\"\"下个节日\"\"查节日\"\"现在是什么节气\"时使用。\n" +
                      "也可查询指定节日：query=\"国庆节\"。"
    )
    public String countdown(
        @ToolParam(value = "query", description = "查询的节日关键词，如\"国庆节\"\"春节\"\"元旦\"。不填则查询下一个最近的节日。") String query
    ) {
        try {
            LocalDate today = LocalDate.now();
            int currentYear = today.getYear();
            int currentMonth = today.getMonthValue();
            int currentDay = today.getDayOfMonth();

            if (query != null && !query.isBlank()) {
                // 查询指定节日
                String clean = query.trim().toLowerCase();
                for (Holiday h : FIXED_HOLIDAYS) {
                    if (h.name().toLowerCase().contains(clean)) {
                        return formatHolidayInfo(h, currentYear, today);
                    }
                }
                return "没找到\"" + query + "\"相关的节日，试试：元旦、劳动节、国庆节、圣诞节";
            }

            // 找下一个最近的节日
            Holiday nextHoliday = null;
            long minDays = Long.MAX_VALUE;

            for (Holiday h : FIXED_HOLIDAYS) {
                LocalDate hDate = LocalDate.of(currentYear, h.month(), h.day());

                // 如果今年的已经过了，看明年
                if (hDate.isBefore(today) || hDate.isEqual(today)) {
                    hDate = LocalDate.of(currentYear + 1, h.month(), h.day());
                }

                long days = ChronoUnit.DAYS.between(today, hDate);
                if (days < minDays) {
                    minDays = days;
                    nextHoliday = h;
                }
            }

            if (nextHoliday == null) {
                return "找不到下一个节日 🤔";
            }

            LocalDate nextDate = LocalDate.of(
                    today.getYear() + (nextHoliday.month() < today.getMonthValue() ||
                            (nextHoliday.month() == today.getMonthValue() && nextHoliday.day() <= today.getDayOfMonth()) ? 1 : 0),
                    nextHoliday.month(), nextHoliday.day());

            StringBuilder sb = new StringBuilder();
            sb.append("📅 下个节日：").append(nextHoliday.name()).append("\n");
            sb.append("日期：").append(nextDate.getYear()).append("年").append(nextHoliday.month()).append("月").append(nextHoliday.day()).append("日\n");
            sb.append("距今还有：**").append(minDays).append("天**\n");
            if (nextHoliday.isOff()) {
                sb.append("🎉 法定节假日，可以放假！");
            } else {
                sb.append("💼 不是法定假日，但可以自己给自己放");

            }
            return sb.toString();

        } catch (Exception e) {
            log.error("Holiday tool failed", e);
            return "❌ 查节日信息翻车了...";
        }
    }

    private String formatHolidayInfo(Holiday h, int currentYear, LocalDate today) {
        LocalDate hDate = LocalDate.of(currentYear, h.month(), h.day());
        boolean passed = hDate.isBefore(today);

        StringBuilder sb = new StringBuilder();
        sb.append("📅 ").append(h.name()).append("\n");
        sb.append("日期：每年").append(h.month()).append("月").append(h.day()).append("日\n");

        if (passed) {
            long daysAgo = ChronoUnit.DAYS.between(hDate, today);
            sb.append("今年已经过了").append(daysAgo).append("天了哦\n");

            // 明年的
            LocalDate next = LocalDate.of(currentYear + 1, h.month(), h.day());
            long daysUntil = ChronoUnit.DAYS.between(today, next);
            sb.append("距离明年还有：**").append(daysUntil).append("天**");
        } else if (hDate.isEqual(today)) {
            sb.append("🌟 今天就是").append(h.name()).append("！");
        } else {
            long daysUntil = ChronoUnit.DAYS.between(today, hDate);
            sb.append("距今还有：**").append(daysUntil).append("天**");
        }

        sb.append("\n");
        if (h.isOff()) {
            sb.append("🎉 法定假日，可以休息！");
        } else {
            sb.append("💼 非法定假日");
        }

        return sb.toString();
    }
}