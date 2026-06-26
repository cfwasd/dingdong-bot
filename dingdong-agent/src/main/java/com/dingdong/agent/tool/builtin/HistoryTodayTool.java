package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 历史上的今天工具。内置事件库，群聊科普/闲聊场景。
 */
@Slf4j
@Component
public class HistoryTodayTool {

    private record HistoryEvent(int month, int day, int year, String event) {}

    /** 每月每日的事件（简化版，每个日期选代表性事件） */
    private static final List<HistoryEvent> EVENTS = List.of(
            new HistoryEvent(1, 1, 1912, "中华民国成立"),
            new HistoryEvent(1, 1, 1979, "中美正式建交"),
            new HistoryEvent(1, 6, 1938, "新四军军部成立"),
            new HistoryEvent(1, 8, 1976, "周恩来逝世"),
            new HistoryEvent(1, 15, 1935, "遵义会议召开"),
            new HistoryEvent(1, 27, 1964, "中法建交"),
            new HistoryEvent(2, 4, 1999, "\"中国北极黄河站\"建成"),
            new HistoryEvent(2, 10, 2003, "中国首次载人航天计划公布"),
            new HistoryEvent(2, 19, 1997, "邓小平逝世"),
            new HistoryEvent(2, 23, 1980, "中共十一届五中全会召开"),
            new HistoryEvent(3, 5, 1963, "毛泽东题词\"向雷锋同志学习\""),
            new HistoryEvent(3, 8, 1909, "国际妇女节设立"),
            new HistoryEvent(3, 12, 1925, "孙中山逝世"),
            new HistoryEvent(3, 20, 2003, "伊拉克战争爆发"),
            new HistoryEvent(4, 1, 2001, "中美南海撞机事件"),
            new HistoryEvent(4, 5, 1975, "蒋介石逝世"),
            new HistoryEvent(4, 12, 1961, "加加林首次进入太空"),
            new HistoryEvent(4, 15, 1990, "中国首次成功发射外星"),
            new HistoryEvent(5, 1, 2008, "杭州湾跨海大桥通车"),
            new HistoryEvent(5, 4, 1919, "五四运动爆发"),
            new HistoryEvent(5, 12, 2008, "汶川大地震"),
            new HistoryEvent(5, 20, 1980, "中国首次向南太平洋发射运载火箭"),
            new HistoryEvent(6, 1, 1951, "国际儿童节"),
            new HistoryEvent(6, 4, 1989, "中苏关系正常化"),
            new HistoryEvent(6, 9, 1885, "《中法新约》签订"),
            new HistoryEvent(6, 10, 1952, "毛泽东题词\"发展体育运动，增强人民体质\""),
            new HistoryEvent(6, 15, 2001, "上海合作组织成立"),
            new HistoryEvent(6, 17, 1967, "中国第一颗氢弹爆炸成功"),
            new HistoryEvent(6, 23, 1992, "香港中环中心大厦动工"),
            new HistoryEvent(7, 1, 1997, "香港回归祖国"),
            new HistoryEvent(7, 1, 1921, "中国共产党成立"),
            new HistoryEvent(7, 7, 1937, "七七事变（卢沟桥事变）"),
            new HistoryEvent(7, 20, 1969, "阿姆斯特朗登月"),
            new HistoryEvent(8, 1, 1927, "南昌起义"),
            new HistoryEvent(8, 8, 2008, "北京奥运会开幕"),
            new HistoryEvent(8, 15, 1945, "日本宣布无条件投降"),
            new HistoryEvent(9, 3, 1945, "抗日战争胜利纪念日"),
            new HistoryEvent(9, 9, 1976, "毛泽东逝世"),
            new HistoryEvent(9, 18, 1931, "九一八事变"),
            new HistoryEvent(9, 25, 2008, "神舟七号载人航天飞行"),
            new HistoryEvent(10, 1, 1949, "中华人民共和国成立"),
            new HistoryEvent(10, 10, 1911, "辛亥革命武昌起义"),
            new HistoryEvent(10, 16, 1964, "中国第一颗原子弹爆炸成功"),
            new HistoryEvent(10, 24, 2007, "嫦娥一号发射"),
            new HistoryEvent(11, 5, 2008, "奥巴马当选美国总统"),
            new HistoryEvent(11, 11, 1918, "第一次世界大战结束"),
            new HistoryEvent(11, 12, 1939, "白求恩逝世"),
            new HistoryEvent(11, 20, 1999, "神舟一号发射"),
            new HistoryEvent(11, 29, 2012, "习近平总书记提出\"中国梦\""),
            new HistoryEvent(12, 1, 1988, "世界艾滋病日设立"),
            new HistoryEvent(12, 12, 1936, "西安事变"),
            new HistoryEvent(12, 20, 1999, "澳门回归祖国"),
            new HistoryEvent(12, 26, 1893, "毛泽东诞辰"),
            new HistoryEvent(12, 29, 1968, "南京长江大桥全面建成通车"),
            new HistoryEvent(1, 1, 1863, "美国《解放黑人奴隶宣言》生效"),
            new HistoryEvent(2, 1, 1986, "中国发出第一封电子邮件"),
            new HistoryEvent(3, 14, 1883, "马克思逝世"),
            new HistoryEvent(4, 14, 1912, "泰坦尼克号撞上冰山"),
            new HistoryEvent(5, 5, 1921, "孙中山就任非常大总统"),
            new HistoryEvent(6, 25, 1950, "朝鲜战争爆发"),
            new HistoryEvent(7, 4, 1776, "美国独立日"),
            new HistoryEvent(8, 6, 1945, "美国在广岛投下原子弹"),
            new HistoryEvent(9, 2, 1945, "日本签署投降书"),
            new HistoryEvent(10, 5, 2015, "屠呦呦获诺贝尔生理学或医学奖"),
            new HistoryEvent(11, 24, 1859, "达尔文《物种起源》出版"),
            new HistoryEvent(12, 17, 1903, "莱特兄弟首次试飞成功"),
            new HistoryEvent(3, 23, 1983, "中国第一台巨型计算机诞生"),
            new HistoryEvent(5, 25, 1960, "中国登山队首次登顶珠峰"),
            new HistoryEvent(8, 20, 1940, "百团大战"),
            new HistoryEvent(9, 9, 1948, "金圆券改革"),
            new HistoryEvent(10, 1, 2010, "嫦娥二号发射"),
            new HistoryEvent(11, 8, 2012, "中共十八大召开"),
            new HistoryEvent(12, 1, 1991, "苏联解体公投"),
            new HistoryEvent(1, 23, 2020, "武汉因新冠疫情封城"),
            new HistoryEvent(2, 7, 2020, "李文亮医生去世"),
            new HistoryEvent(3, 30, 1981, "里根遇刺"),
            new HistoryEvent(4, 20, 2013, "四川芦山地震"),
            new HistoryEvent(5, 22, 1960, "智利9.5级大地震（有记录以来最大）"),
            new HistoryEvent(6, 11, 2010, "南非世界杯开幕"),
            new HistoryEvent(7, 5, 2010, "中国GDP超日本成为世界第二"),
            new HistoryEvent(8, 8, 2010, "甘肃舟曲特大泥石流"),
            new HistoryEvent(9, 15, 2008, "雷曼兄弟破产，全球金融危机"),
            new HistoryEvent(10, 22, 1995, "联合国成立50周年"),
            new HistoryEvent(11, 5, 2009, "中国空军成立60周年"),
            new HistoryEvent(12, 26, 2004, "印度洋海啸")
    );

    @Tool(
        name = "history_today",
        description = "历史上的今天。当用户说\"历史上的今天\"\"今天历史上的大事\"\"今天发生了什么\"\"今日历史\"时使用。\n" +
                      "也可以查指定日期：\n" +
                      "- date=\"5月12日\" → 查5月12日的历史事件\n" +
                      "- date=\"2008年5月12日\" → 查2008年5月12日（精确匹配）\n" +
                      "- 不传参 → 查询今天的历史事件"
    )
    public String today(
        @ToolParam(value = "date", description = "日期，格式如\"5月12日\"或\"2008年5月12日\"。不填则查询今天。") String date
    ) {
        try {
            int month, day;
            Integer targetYear = null;

            if (date != null && !date.isBlank()) {
                String clean = date.trim();
                // 解析 "2008年5月12日" 或 "5月12日"
                var yearMatcher = java.util.regex.Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日").matcher(clean);
                var simpleMatcher = java.util.regex.Pattern.compile("(\\d{1,2})月(\\d{1,2})日").matcher(clean);

                if (yearMatcher.find()) {
                    targetYear = Integer.parseInt(yearMatcher.group(1));
                    month = Integer.parseInt(yearMatcher.group(2));
                    day = Integer.parseInt(yearMatcher.group(3));
                } else if (simpleMatcher.find()) {
                    month = Integer.parseInt(simpleMatcher.group(1));
                    day = Integer.parseInt(simpleMatcher.group(2));
                } else {
                    return "❌ 日期格式不对，试试\"5月12日\"或\"2008年5月12日\"";
                }
            } else {
                // 默认今天
                LocalDate now = LocalDate.now();
                month = now.getMonthValue();
                day = now.getDayOfMonth();
            }

            // 过滤事件
            final Integer filteredYear = targetYear;
            List<HistoryEvent> matched = EVENTS.stream()
                    .filter(e -> e.month() == month && e.day() == day)
                    .filter(e -> filteredYear == null || e.year() == filteredYear)
                    .sorted((a, b) -> Integer.compare(b.year(), a.year()))
                    .toList();

            if (matched.isEmpty()) {
                return "📅 " + month + "月" + day + "日\n\n今天好像没什么特别的大事... 平平淡淡才是真嘛 😄";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📅 ").append(month).append("月").append(day).append("日 历史上的今天\n\n");
            for (HistoryEvent e : matched) {
                sb.append("· ").append(e.year()).append("年 — ").append(e.event()).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("History today failed", e);
            return "❌ 查历史翻车了，等会再试试吧";
        }
    }
}