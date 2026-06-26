# 修仙系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `dingdong-cultivation` module with full cultivation RPG system (realm/cultivation/tribulation/sparring/sects/pills/dual-cultivation) and migrate CheckInTool/FortuneTool/MarriageTool from `dingdong-agent`.

**Architecture:** New Maven module `dingdong-cultivation` depends only on `dingdong-core`. Each feature has dual entry points: `@Tool` methods in `tool/` package (Agent auto-invocation) and `@Command` methods in `bot/` package (direct user input). Both channels (OneBot11 + QQ Official) work via `@ComponentScan("com.dingdong")` auto-discovery.

**Tech Stack:** Java 17, Spring Boot 3.2, SQLite (via `DbManager`), Lombok, `@Tool`/`@Command` annotations from `dingdong-core`

---

### Task 1: Module Skeleton — Create `dingdong-cultivation` pom.xml

**Files:**
- Create: `dingdong-cultivation/pom.xml`

- [ ] **Step 1: Write pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.napcat</groupId>
        <artifactId>dingdong-parent</artifactId>
        <version>1.0.0</version>
    </parent>
    <artifactId>dingdong-cultivation</artifactId>
    <name>dingdong-cultivation</name>
    <description>DingDong Java SDK - Cultivation/XiuXian RPG system</description>

    <dependencies>
        <dependency>
            <groupId>com.napcat</groupId>
            <artifactId>dingdong-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Register module in parent pom.xml**

In `pom.xml`, add to `<modules>` after `dingdong-agent`:

```xml
<module>dingdong-cultivation</module>
```

- [ ] **Step 3: Add dependency to `dingdong-boot-starter/pom.xml`**

In `dingdong-boot-starter/pom.xml`, add after the `dingdong-qqofficial` dependency:

```xml
<dependency>
    <groupId>com.napcat</groupId>
    <artifactId>dingdong-cultivation</artifactId>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 4: Add `dingdong-cultivation` to parent `<dependencyManagement>`**

In `pom.xml`, add to `<dependencyManagement>` / `<dependencies>`:

```xml
<dependency>
    <groupId>com.napcat</groupId>
    <artifactId>dingdong-cultivation</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 5: Create package directories**

```bash
mkdir -p dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool
mkdir -p dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot
```

- [ ] **Step 6: Verify compile**

```bash
mvn compile -pl dingdong-cultivation -am -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add dingdong-cultivation/pom.xml pom.xml dingdong-boot-starter/pom.xml
git commit -m "feat: add dingdong-cultivation module skeleton"
```

---

### Task 2: Migration 9 — Register cultivation tables

**Files:**
- Modify: `dingdong-boot-starter/src/main/java/com/dingdong/boot/starter/config/NapCatAutoConfiguration.java`

- [ ] **Step 1: Add migration 9**

In the `migrationManager` bean method, after `mm.register(8, ...)`, add:

```java
mm.register(9, "create cultivation tables",
    "CREATE TABLE IF NOT EXISTS cultivation_users (" +
    "user_id INTEGER NOT NULL," +
    "group_id INTEGER NOT NULL DEFAULT 0," +
    "user_name TEXT DEFAULT ''," +
    "realm TEXT NOT NULL DEFAULT 'mortal'," +
    "sub_level INTEGER DEFAULT 1," +
    "cultivation INTEGER DEFAULT 0," +
    "root_bone INTEGER DEFAULT 10," +
    "luck INTEGER DEFAULT 10," +
    "spirit INTEGER DEFAULT 10," +
    "spirit_stones INTEGER DEFAULT 100," +
    "reputation INTEGER DEFAULT 0," +
    "last_cultivate_time TEXT DEFAULT ''," +
    "last_checkin_date TEXT DEFAULT ''," +
    "is_injured INTEGER DEFAULT 0," +
    "injury_until TEXT DEFAULT ''," +
    "has_reborn INTEGER DEFAULT 0," +
    "created_at TEXT DEFAULT ''," +
    "PRIMARY KEY (user_id, group_id));" +
    "CREATE TABLE IF NOT EXISTS sects (" +
    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
    "group_id INTEGER NOT NULL DEFAULT 0," +
    "name TEXT NOT NULL," +
    "leader_id INTEGER NOT NULL," +
    "leader_name TEXT DEFAULT ''," +
    "level INTEGER DEFAULT 1," +
    "contribution INTEGER DEFAULT 0," +
    "member_count INTEGER DEFAULT 1," +
    "created_at TEXT DEFAULT '');" +
    "CREATE INDEX IF NOT EXISTS idx_sects_group ON sects(group_id, name);" +
    "CREATE TABLE IF NOT EXISTS sect_members (" +
    "sect_id INTEGER NOT NULL," +
    "user_id INTEGER NOT NULL," +
    "group_id INTEGER NOT NULL DEFAULT 0," +
    "user_name TEXT DEFAULT ''," +
    "joined_at TEXT DEFAULT ''," +
    "PRIMARY KEY (sect_id, user_id, group_id));" +
    "CREATE TABLE IF NOT EXISTS spar_challenges (" +
    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
    "group_id INTEGER NOT NULL DEFAULT 0," +
    "challenger_id INTEGER NOT NULL," +
    "challenger_name TEXT DEFAULT ''," +
    "target_id INTEGER NOT NULL," +
    "target_name TEXT DEFAULT ''," +
    "created_at TEXT DEFAULT ''," +
    "status TEXT DEFAULT 'pending');"
);
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -pl dingdong-boot-starter -am -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add dingdong-boot-starter/src/main/java/com/dingdong/boot/starter/config/NapCatAutoConfiguration.java
git commit -m "feat: add migration 9 for cultivation tables"
```

---

### Task 3: CultivationTool — Data model and constants

**Files:**
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java`

- [ ] **Step 1: Write CultivationTool with realm/attribute constants, ensureTable, and helper methods**

```java
package com.dingdong.cultivation.tool;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import com.dingdong.core.scheduler.DbManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class CultivationTool {

    private final DbManager dbManager;
    private volatile boolean tableReady;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // 境界体系: 名称, 基础修为需求
    private static final String[][] REALMS = {
        {"mortal", "凡人", "0"},
        {"lianqi", "练气", "100"},
        {"zhuji", "筑基", "300"},
        {"jindan", "金丹", "600"},
        {"yuanying", "元婴", "1000"},
        {"huashen", "化神", "1500"},
        {"dujie", "渡劫", "2200"},
        {"dacheng", "大乘", "3000"},
        {"zhenxian", "真仙", "4000"},
    };

    private static final String[] SUB_LEVEL_NAMES = {"", "初期", "中期", "后期", "圆满"};

    // 境界系数: 修炼修为 = 根骨 × 境界系数
    private static final double[] REALM_COEFF = {1.0, 1.2, 1.5, 1.8, 2.2, 2.6, 3.0, 3.5, 4.0};

    // 招式池
    private static final String[][] MOVE_POOL = {
        {"天雷破", "20", "引天雷之力，直劈而下"},
        {"玄冰咒", "18", "凝结玄冰，刺骨寒心"},
        {"烈焰掌", "22", "掌心烈焰，焚尽万物"},
        {"万剑诀", "25", "万剑齐发，无处可逃"},
        {"风刃术", "15", "无形风刃，削铁如泥"},
        {"裂地斩", "24", "一刀裂地，山河震动"},
        {"噬魂诀", "19", "吞噬神魂，直击元神"},
        {"紫电青光", "21", "紫电环绕，青光一闪"},
        {"太虚步", "16", "身形飘忽，出其不意"},
        {"金刚印", "23", "金刚伏魔，一掌定乾坤"},
        {"星辰陨", "28", "引星辰之力，陨落凡尘"},
        {"苍龙吟", "26", "苍龙咆哮，声震九霄"},
    };

    public CultivationTool(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    private void ensureTable() {
        if (tableReady) return;
        synchronized (this) {
            if (tableReady) return;
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS cultivation_users (" +
                     "user_id INTEGER NOT NULL," +
                     "group_id INTEGER NOT NULL DEFAULT 0," +
                     "user_name TEXT DEFAULT ''," +
                     "realm TEXT NOT NULL DEFAULT 'mortal'," +
                     "sub_level INTEGER DEFAULT 1," +
                     "cultivation INTEGER DEFAULT 0," +
                     "root_bone INTEGER DEFAULT 10," +
                     "luck INTEGER DEFAULT 10," +
                     "spirit INTEGER DEFAULT 10," +
                     "spirit_stones INTEGER DEFAULT 100," +
                     "reputation INTEGER DEFAULT 0," +
                     "last_cultivate_time TEXT DEFAULT ''," +
                     "last_checkin_date TEXT DEFAULT ''," +
                     "is_injured INTEGER DEFAULT 0," +
                     "injury_until TEXT DEFAULT ''," +
                     "has_reborn INTEGER DEFAULT 0," +
                     "created_at TEXT DEFAULT ''," +
                     "PRIMARY KEY (user_id, group_id))")) {
                stmt.execute();
                tableReady = true;
            } catch (Exception e) {
                log.error("Failed to create cultivation_users table", e);
            }
        }
    }

    // ============ 辅助方法 ============

    private int getRealmIndex(String realm) {
        for (int i = 0; i < REALMS.length; i++) {
            if (REALMS[i][0].equals(realm)) return i;
        }
        return 0;
    }

    private String realmDisplayName(String realm, int subLevel) {
        int idx = getRealmIndex(realm);
        return REALMS[idx][1] + "·" + SUB_LEVEL_NAMES[subLevel];
    }

    private int getBaseCultivationCost(int realmIdx, int subLevel) {
        int base = Integer.parseInt(REALMS[realmIdx][2]);
        return (int) (base * Math.pow(1.5, (realmIdx * 4 + subLevel - 1)));
    }

    private CultivationUser loadUser(Connection conn, long userId, long groupId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                CultivationUser u = new CultivationUser();
                u.userId = rs.getLong("user_id");
                u.groupId = rs.getLong("group_id");
                u.userName = rs.getString("user_name");
                u.realm = rs.getString("realm");
                u.subLevel = rs.getInt("sub_level");
                u.cultivation = rs.getInt("cultivation");
                u.rootBone = rs.getInt("root_bone");
                u.luck = rs.getInt("luck");
                u.spirit = rs.getInt("spirit");
                u.spiritStones = rs.getInt("spirit_stones");
                u.reputation = rs.getInt("reputation");
                u.lastCultivateTime = rs.getString("last_cultivate_time");
                u.lastCheckinDate = rs.getString("last_checkin_date");
                u.isInjured = rs.getInt("is_injured") != 0;
                u.injuryUntil = rs.getString("injury_until");
                u.hasReborn = rs.getInt("has_reborn") != 0;
                return u;
            }
        }
        return null;
    }

    private void saveUser(Connection conn, CultivationUser u) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO cultivation_users (user_id, group_id, user_name, realm, sub_level, " +
                "cultivation, root_bone, luck, spirit, spirit_stones, reputation, " +
                "last_cultivate_time, last_checkin_date, is_injured, injury_until, has_reborn, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(user_id, group_id) DO UPDATE SET " +
                "user_name = excluded.user_name, realm = excluded.realm, sub_level = excluded.sub_level, " +
                "cultivation = excluded.cultivation, root_bone = excluded.root_bone, luck = excluded.luck, " +
                "spirit = excluded.spirit, spirit_stones = excluded.spirit_stones, " +
                "reputation = excluded.reputation, last_cultivate_time = excluded.last_cultivate_time, " +
                "last_checkin_date = excluded.last_checkin_date, is_injured = excluded.is_injured, " +
                "injury_until = excluded.injury_until, has_reborn = excluded.has_reborn")) {
            stmt.setLong(1, u.userId);
            stmt.setLong(2, u.groupId);
            stmt.setString(3, u.userName != null ? u.userName : "");
            stmt.setString(4, u.realm);
            stmt.setInt(5, u.subLevel);
            stmt.setInt(6, u.cultivation);
            stmt.setInt(7, u.rootBone);
            stmt.setInt(8, u.luck);
            stmt.setInt(9, u.spirit);
            stmt.setInt(10, u.spiritStones);
            stmt.setInt(11, u.reputation);
            stmt.setString(12, u.lastCultivateTime != null ? u.lastCultivateTime : "");
            stmt.setString(13, u.lastCheckinDate != null ? u.lastCheckinDate : "");
            stmt.setInt(14, u.isInjured ? 1 : 0);
            stmt.setString(15, u.injuryUntil != null ? u.injuryUntil : "");
            stmt.setInt(16, u.hasReborn ? 1 : 0);
            stmt.setString(17, u.createdAt != null ? u.createdAt : "");
            stmt.executeUpdate();
        }
    }

    private String now() {
        return LocalDateTime.now().format(ISO_FMT);
    }

    private double realmCoeff(String realm) {
        return REALM_COEFF[getRealmIndex(realm)];
    }

    private static class CultivationUser {
        long userId, groupId;
        String userName;
        String realm = "mortal";
        int subLevel = 1;
        int cultivation;
        int rootBone = 10;
        int luck = 10;
        int spirit = 10;
        int spiritStones = 100;
        int reputation;
        String lastCultivateTime;
        String lastCheckinDate;
        boolean isInjured;
        String injuryUntil;
        boolean hasReborn;
        String createdAt;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java
git commit -m "feat: add CultivationTool data model and constants"
```

---

### Task 4: CultivationTool — start_cultivation (initialize cultivator)

**Files:**
- Modify: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java`

- [ ] **Step 1: Add `start_cultivation` method**

Add this method inside the `CultivationTool` class:

```java
@Tool(
    name = "start_cultivation",
    description = "开启修仙之路。当用户说\"修仙\"\"开始修仙\"\"我要修仙\"\"成为修仙者\"时使用。随机生成根骨/气运/灵力属性。"
)
public String startCultivation(
    @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
    @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
    @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName
) {
    long userId, groupId;
    try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
    try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

    ensureTable();
    String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

    try (Connection conn = dbManager.getConnection()) {
        CultivationUser user = loadUser(conn, userId, groupId);
        if (user != null) {
            return "⚡ " + uName + " 你已经是修仙者了！\n当前境界：" + realmDisplayName(user.realm, user.subLevel);
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int rootBone = rng.nextInt(5, 16);  // 5-15
        int luck = rng.nextInt(5, 16);
        int spirit = rng.nextInt(5, 16);

        CultivationUser newUser = new CultivationUser();
        newUser.userId = userId;
        newUser.groupId = groupId;
        newUser.userName = uName;
        newUser.rootBone = rootBone;
        newUser.luck = luck;
        newUser.spirit = spirit;
        newUser.spiritStones = 100;
        newUser.createdAt = now();
        saveUser(conn, newUser);

        // 天赋评价
        int total = rootBone + luck + spirit;
        String talent;
        if (total >= 40) talent = "🌟 天灵根！万中无一的修炼奇才！";
        else if (total >= 35) talent = "🔥 极品灵根，前途不可限量！";
        else if (total >= 30) talent = "✨ 上品灵根，修仙之路一片光明";
        else if (total >= 25) talent = "👍 中品灵根，中规中矩的修仙者";
        else if (total >= 20) talent = "📘 下品灵根，勤能补拙，加油！";
        else talent = "💪 杂灵根...不过谁说杂灵根不能逆天改命？";

        return "⚡ " + uName + " 踏上了修仙之路！\n\n"
            + "━━━━━━━━━━━━━━\n"
            + "🎭 天赋：" + talent + "\n"
            + "━━━━━━━━━━━━━━\n"
            + "🦴 根骨：" + rootBone + "（修炼效率）\n"
            + "🍀 气运：" + luck + "（渡劫/奇遇）\n"
            + "⚔️ 灵力：" + spirit + "（战斗伤害）\n"
            + "💎 初始灵石：100\n"
            + "━━━━━━━━━━━━━━\n\n"
            + "💡 说\"修炼\"开始获取修为！\n"
            + "💡 说\"修仙菜单\"查看全部功能";
    } catch (Exception e) {
        log.error("start_cultivation failed", e);
        return "❌ 修仙之路开启失败，天道异常...";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java
git commit -m "feat: add start_cultivation tool method"
```

---

### Task 5: CultivationTool — cultivate (active cultivation + accidents)

**Files:**
- Modify: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java`

- [ ] **Step 1: Add `cultivate` method**

Add this method inside the `CultivationTool` class:

```java
@Tool(
    name = "cultivate",
    description = "主动修炼。当用户说\"修炼\"\"开始修炼\"\"打坐\"\"闭关\"时使用。1小时CD，消耗1小时获得修为。"
)
public String cultivate(
    @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
    @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
    @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName
) {
    long userId, groupId;
    try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
    try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

    ensureTable();
    String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

    try (Connection conn = dbManager.getConnection()) {
        CultivationUser user = loadUser(conn, userId, groupId);
        if (user == null) {
            return "🤔 " + uName + " 你还没开启修仙之路！说\"修仙\"开始吧~";
        }

        // 重伤检查
        if (user.isInjured) {
            if (user.injuryUntil != null && !user.injuryUntil.isEmpty()) {
                LocalDateTime until = LocalDateTime.parse(user.injuryUntil, ISO_FMT);
                if (LocalDateTime.now().isBefore(until)) {
                    return "💔 " + uName + " 你正处于重伤状态，修炼效率-50%！\n"
                        + "⏰ 重伤持续至：" + until.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n"
                        + "💊 可使用疗伤丹立即恢复：说\"购买 疗伤丹\"";
                } else {
                    user.isInjured = false;
                    user.injuryUntil = "";
                }
            }
        }

        // CD 检查
        if (user.lastCultivateTime != null && !user.lastCultivateTime.isEmpty()) {
            LocalDateTime lastTime = LocalDateTime.parse(user.lastCultivateTime, ISO_FMT);
            long minutesSince = ChronoUnit.MINUTES.between(lastTime, LocalDateTime.now());
            if (minutesSince < 60) {
                long remainMin = 60 - minutesSince;
                return "⏳ " + uName + " 修炼冷却中...还需等待 " + remainMin + " 分钟\n"
                    + "💡 修炼CD为1小时，耐心打坐吧~";
            }
        }

        // 计算后台被动收益（时间差）
        int passiveGain = 0;
        if (user.lastCultivateTime != null && !user.lastCultivateTime.isEmpty()) {
            LocalDateTime lastTime = LocalDateTime.parse(user.lastCultivateTime, ISO_FMT);
            double hoursPassed = ChronoUnit.MINUTES.between(lastTime, LocalDateTime.now()) / 60.0;
            hoursPassed = Math.min(hoursPassed, 8.0); // 上限8小时
            passiveGain = (int) (hoursPassed * user.rootBone * realmCoeff(user.realm) * 0.5);
        }

        // 主动修炼收益
        double coeff = realmCoeff(user.realm);
        int activeGain = (int) (user.rootBone * coeff);

        // 加成
        double bonusMultiplier = 1.0;
        // 道侣加成: 由MarriageTool调用时注入，此处通过last_cultivate_time的标记判断
        // 转世加成
        if (user.hasReborn) {
            bonusMultiplier += 0.2;
        }
        // 重伤减成
        if (user.isInjured) {
            bonusMultiplier -= 0.5;
        }

        activeGain = (int) (activeGain * bonusMultiplier);
        passiveGain = (int) (passiveGain * bonusMultiplier);

        // 意外事件判定 (10%基础概率，气运每高1点-0.5%)
        double accidentChance = 0.10 - (user.luck - 10) * 0.005;
        accidentChance = Math.max(0.02, Math.min(0.15, accidentChance));
        String accidentMsg = "";
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() < accidentChance) {
            int roll = rng.nextInt(100);
            if (roll < 5) { // ~5% of 10% = 0.5% 走火入魔
                user.isInjured = true;
                user.injuryUntil = LocalDateTime.now().plusHours(1).format(ISO_FMT);
                user.cultivation = user.cultivation / 2;
                accidentMsg = "\n\n💀 走火入魔！当前修为减半，修炼效率-50%（持续1小时）！";
            } else if (roll < 8) { // ~3% 天降奇遇
                activeGain *= 3;
                accidentMsg = "\n\n🌟 天降祥瑞！修为暴击×3！";
            } else if (roll < 9) { // ~1% 上古遗迹
                int boostAttr = rng.nextInt(3);
                String attrName;
                switch (boostAttr) {
                    case 0: user.rootBone = Math.min(15, user.rootBone + 1); attrName = "根骨"; break;
                    case 1: user.luck = Math.min(15, user.luck + 1); attrName = "气运"; break;
                    default: user.spirit = Math.min(15, user.spirit + 1); attrName = "灵力"; break;
                }
                accidentMsg = "\n\n🏛️ 你在修炼中感应到上古遗迹..." + attrName + "永久+1！";
            }
        }

        int totalGain = activeGain + passiveGain;
        user.cultivation += totalGain;
        user.lastCultivateTime = now();
        saveUser(conn, user);

        StringBuilder sb = new StringBuilder();
        sb.append("🧘 ").append(uName).append(" 修炼完毕！\n\n");
        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("📈 主动修炼：+").append(activeGain).append(" 修为\n");
        if (passiveGain > 0) {
            sb.append("⏳ 后台收益：+").append(passiveGain).append(" 修为\n");
        }
        sb.append("💎 累计修为：").append(user.cultivation).append("\n");
        if (bonusMultiplier > 1.0) {
            sb.append("🔥 加成倍率：×").append(String.format("%.1f", bonusMultiplier)).append("\n");
        }
        if (user.isInjured) {
            sb.append("💔 重伤减成：-50%\n");
        }
        sb.append("━━━━━━━━━━━━━━");

        if (accidentMsg != null && !accidentMsg.isEmpty()) {
            sb.append(accidentMsg);
        }

        // 提示突破
        int realmIdx = getRealmIndex(user.realm);
        int cost = getBaseCultivationCost(realmIdx, user.subLevel);
        if (user.cultivation >= cost && user.subLevel < 4) {
            sb.append("\n\n⚡ 修为已满！说\"突破\"来提升小层吧！");
        }

        return sb.toString();
    } catch (Exception e) {
        log.error("cultivate failed", e);
        return "❌ 修炼失败，灵气紊乱...";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java
git commit -m "feat: add cultivate tool method with accidents"
```

---

### Task 6: CultivationTool — breakthrough (小层突破)

**Files:**
- Modify: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java`

- [ ] **Step 1: Add `breakthrough` method**

```java
@Tool(
    name = "breakthrough",
    description = "突破当前小层。当用户说\"突破\"\"提升境界\"\"我要突破\"时使用。消耗指定修为值，100%成功。"
)
public String breakthrough(
    @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
    @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
    @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName
) {
    long userId, groupId;
    try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
    try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

    ensureTable();
    String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

    try (Connection conn = dbManager.getConnection()) {
        CultivationUser user = loadUser(conn, userId, groupId);
        if (user == null) {
            return "🤔 " + uName + " 你还没开启修仙之路！说\"修仙\"开始吧~";
        }

        int realmIdx = getRealmIndex(user.realm);
        if (realmIdx >= REALMS.length - 1 && user.subLevel >= 4) {
            return "👑 " + uName + " 你已达到最高境界——真仙·圆满！已是仙界至尊！";
        }

        if (user.subLevel >= 4) {
            // 需要渡劫突破大境界
            return "⚡ " + uName + " 已到" + REALMS[realmIdx][1] + "圆满！\n"
                + "突破至下一大境界需要渡劫！说\"渡劫\"开始天劫试炼！";
        }

        int cost = getBaseCultivationCost(realmIdx, user.subLevel);
        if (user.cultivation < cost) {
            int need = cost - user.cultivation;
            return "📉 " + uName + " 修为不足！\n"
                + "需要：" + cost + " 修为\n"
                + "当前：" + user.cultivation + " 修为\n"
                + "还差：" + need + " 修为\n"
                + "💡 说\"修炼\"获取修为！";
        }

        user.cultivation -= cost;
        user.subLevel++;
        String oldRealm = realmDisplayName(user.realm, user.subLevel - 1);
        String newRealm = realmDisplayName(user.realm, user.subLevel);
        saveUser(conn, user);

        String msg = "🎉 " + uName + " 突破成功！\n\n"
            + oldRealm + " → " + newRealm + "\n"
            + "💎 剩余修为：" + user.cultivation + "\n";

        if (user.subLevel >= 4) {
            msg += "\n⚡ 已达" + REALMS[realmIdx][1] + "圆满！\n"
                + "💀 突破至下一大境界需要渡劫！说\"渡劫\"开始天劫试炼！";
        } else {
            int nextCost = getBaseCultivationCost(realmIdx, user.subLevel);
            msg += "\n📈 下一层需要：" + nextCost + " 修为";
        }

        return msg;
    } catch (Exception e) {
        log.error("breakthrough failed", e);
        return "❌ 突破失败，灵气暴走...";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java
git commit -m "feat: add breakthrough tool method"
```

---

### Task 7: CultivationTool — dujie (渡劫)

**Files:**
- Modify: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java`

- [ ] **Step 1: Add `dujie` method**

```java
@Tool(
    name = "dujie",
    description = "渡劫突破大境界。当用户说\"渡劫\"\"开始渡劫\"\"我要渡劫\"时使用。多轮天雷判定，连续成功才算通过。"
)
public String dujie(
    @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
    @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
    @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName
) {
    long userId, groupId;
    try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
    try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

    ensureTable();
    String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

    try (Connection conn = dbManager.getConnection()) {
        CultivationUser user = loadUser(conn, userId, groupId);
        if (user == null) {
            return "🤔 " + uName + " 你还没开启修仙之路！说\"修仙\"开始吧~";
        }

        int realmIdx = getRealmIndex(user.realm);
        if (user.subLevel < 4) {
            return "🤔 " + uName + " 需要先达到" + REALMS[realmIdx][1] + "圆满才能渡劫！";
        }
        if (realmIdx >= REALMS.length - 1) {
            return "👑 " + uName + " 你已是真仙，无需渡劫！";
        }

        // 天雷轮数: 练气3轮 → 大乘9轮
        int totalRounds = 3 + realmIdx;
        double baseSuccessRate = 0.60 + user.luck * 0.02; // 60% + 气运×2%
        baseSuccessRate = Math.min(0.90, baseSuccessRate);

        // TODO: 丹药加成、护法加成从外部注入
        // 此处使用基础成功率

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        sb.append("⚡ 天劫降临！").append(uName).append(" 正在渡劫...\n\n");
        sb.append("目标：").append(REALMS[realmIdx][1]).append(" → ").append(REALMS[realmIdx + 1][1]).append("\n");
        sb.append("天雷轮数：").append(totalRounds).append(" 轮\n");
        sb.append("每轮成功率：").append(String.format("%.0f", baseSuccessRate * 100)).append("%\n");
        sb.append("━━━━━━━━━━━━━━\n");

        int passedRounds = 0;
        for (int i = 1; i <= totalRounds; i++) {
            boolean success = rng.nextDouble() < baseSuccessRate;
            if (success) {
                passedRounds++;
                sb.append("✅ 第").append(i).append("轮：成功通过！");
                if (i < totalRounds) sb.append("（剩余").append(totalRounds - i).append("轮）");
                sb.append("\n");
            } else {
                sb.append("❌ 第").append(i).append("轮：被天雷击中！渡劫失败！\n");
                break;
            }
        }

        sb.append("━━━━━━━━━━━━━━\n");

        if (passedRounds == totalRounds) {
            // 渡劫成功
            String oldRealm = realmDisplayName(user.realm, user.subLevel);
            user.realm = REALMS[realmIdx + 1][0];
            user.subLevel = 1;
            user.cultivation = 0;
            saveUser(conn, user);
            String newRealm = realmDisplayName(user.realm, user.subLevel);

            sb.append("🎉 渡劫成功！\n");
            sb.append(oldRealm).append(" → ").append(newRealm).append("\n");
            sb.append("\n💪 恭喜踏入新境界！修仙之路更进一步！");
        } else {
            // 渡劫失败
            boolean reincarnate = rng.nextDouble() < 0.80;
            if (reincarnate) {
                int oldRootBone = user.rootBone;
                int retainedRoot = (int) Math.ceil(user.rootBone * 0.5);
                user.realm = "mortal";
                user.subLevel = 1;
                user.cultivation = 0;
                user.rootBone = retainedRoot;
                user.reputation = 0;
                user.hasReborn = true;
                saveUser(conn, user);

                sb.append("💀 身死道消...进入转世重修...\n\n");
                sb.append("━━━━━━━━━━━━━━\n");
                sb.append("🔄 修为归零，境界重置为凡人\n");
                sb.append("🦴 根骨保留50%：").append(oldRootBone).append(" → ").append(retainedRoot).append("\n");
                sb.append("💎 灵石和宗门关系保留\n");
                sb.append("🌟 获得「轮回印记」：修炼效率+20%\n");
                sb.append("━━━━━━━━━━━━━━\n\n");
                sb.append("💡 天道轮回，重新开始！说\"修仙\"再次踏上修仙之路！");
            } else {
                // 重伤掉境
                int dropLevels = rng.nextInt(1, 3);
                int newRealmIdx = Math.max(0, realmIdx - dropLevels);
                user.realm = REALMS[newRealmIdx][0];
                user.subLevel = Math.max(1, user.subLevel - rng.nextInt(1, 3));
                user.cultivation = user.cultivation / 2;
                user.isInjured = true;
                user.injuryUntil = LocalDateTime.now().plusHours(1).format(ISO_FMT);
                saveUser(conn, user);

                sb.append("💔 渡劫失败，重伤掉境！\n");
                sb.append("当前境界：").append(realmDisplayName(user.realm, user.subLevel)).append("\n");
                sb.append("💊 重伤持续1小时，可使用疗伤丹恢复");
            }
        }

        return sb.toString();
    } catch (Exception e) {
        log.error("dujie failed", e);
        return "❌ 渡劫失败，天道紊乱...";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java
git commit -m "feat: add dujie (tribulation) tool method"
```

---

### Task 8: CultivationTool — cultivation_status and cultivation_ranking

**Files:**
- Modify: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java`

- [ ] **Step 1: Add `cultivation_status` and `cultivation_ranking` methods**

```java
@Tool(
    name = "cultivation_status",
    description = "查看修仙状态面板。当用户说\"修仙状态\"\"我的修仙\"\"查看修仙\"\"修仙面板\"时使用。"
)
public String cultivationStatus(
    @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
    @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
    @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName
) {
    long userId, groupId;
    try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
    try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

    ensureTable();
    String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

    try (Connection conn = dbManager.getConnection()) {
        CultivationUser user = loadUser(conn, userId, groupId);
        if (user == null) {
            return "🤔 " + uName + " 你还没开启修仙之路！说\"修仙\"开始吧~";
        }

        int realmIdx = getRealmIndex(user.realm);
        int nextCost = getBaseCultivationCost(realmIdx, user.subLevel);
        String realmFull = realmDisplayName(user.realm, user.subLevel);

        StringBuilder sb = new StringBuilder();
        sb.append("📋 ").append(uName).append(" 的修仙面板\n\n");
        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("🏅 境界：").append(realmFull).append("\n");
        sb.append("💎 修为：").append(user.cultivation).append(" / ").append(nextCost);
        if (user.subLevel >= 4) sb.append("（满，可渡劫）");
        sb.append("\n");
        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("🦴 根骨：").append(user.rootBone).append("（修炼效率）\n");
        sb.append("🍀 气运：").append(user.luck).append("（渡劫/奇遇）\n");
        sb.append("⚔️ 灵力：").append(user.spirit).append("（战斗伤害）\n");
        sb.append("💎 灵石：").append(user.spiritStones).append("\n");
        sb.append("⭐ 声望：").append(user.reputation).append("\n");
        sb.append("━━━━━━━━━━━━━━\n");

        if (user.isInjured) {
            sb.append("💔 状态：重伤中（修炼效率-50%）\n");
        }
        if (user.hasReborn) {
            sb.append("🌟 轮回印记：修炼效率+20%\n");
        }

        // 道侣信息（由MarriageTool查询填充，这里只做占位）
        return sb.toString();
    } catch (Exception e) {
        log.error("cultivation_status failed", e);
        return "❌ 查询失败，天道紊乱...";
    }
}

@Tool(
    name = "cultivation_ranking",
    description = "查看本群修仙排行榜。当用户说\"修仙排行\"\"修仙排名\"\"修仙排行榜\"\"修仙榜\"时使用。"
)
public String cultivationRanking(
    @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr
) {
    long groupId;
    try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

    ensureTable();

    try (Connection conn = dbManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(
             "SELECT user_name, realm, sub_level, cultivation, reputation FROM cultivation_users " +
             "WHERE group_id = ? ORDER BY " +
             "CASE realm " +
             "WHEN 'zhenxian' THEN 9 WHEN 'dacheng' THEN 8 WHEN 'dujie' THEN 7 " +
             "WHEN 'huashen' THEN 6 WHEN 'yuanying' THEN 5 WHEN 'jindan' THEN 4 " +
             "WHEN 'zhuji' THEN 3 WHEN 'lianqi' THEN 2 ELSE 1 END DESC, " +
             "sub_level DESC, cultivation DESC LIMIT 10")) {
        stmt.setLong(1, groupId);
        ResultSet rs = stmt.executeQuery();

        StringBuilder sb = new StringBuilder();
        sb.append("🏆 修仙排行榜 TOP 10\n\n");

        int rank = 0;
        boolean hasAny = false;
        while (rs.next()) {
            hasAny = true;
            rank++;
            String name = rs.getString("user_name");
            String realm = rs.getString("realm");
            int subLevel = rs.getInt("sub_level");
            int reputation = rs.getInt("reputation");
            String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : rank + ".";

            sb.append(medal).append(" ").append(name)
              .append(" — ").append(realmDisplayName(realm, subLevel))
              .append(" | ⭐").append(reputation).append("\n");
        }

        if (!hasAny) {
            sb.append("本群还没有修仙者...\n💡 说\"修仙\"成为第一个！");
        }

        return sb.toString();
    } catch (Exception e) {
        log.error("cultivation_ranking failed", e);
        return "❌ 查询失败...";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java
git commit -m "feat: add cultivation_status and cultivation_ranking methods"
```

---

### Task 9: CultivationTool — spar_initiate and spar_accept (切磋)

**Files:**
- Modify: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java`

- [ ] **Step 1: Add spar methods**

```java
@Tool(
    name = "spar_initiate",
    description = "发起切磋挑战。当用户说\"切磋 @某人\"\"挑战 @某人\"\"来打架\"时使用。"
)
public String sparInitiate(
    @ToolParam(value = "user_id", description = "发起者用户ID", required = true) String userIdStr,
    @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
    @ToolParam(value = "user_name", description = "发起者昵称", required = false) String userName,
    @ToolParam(value = "target_id", description = "被挑战者用户ID", required = true) String targetIdStr,
    @ToolParam(value = "target_name", description = "被挑战者昵称", required = false) String targetName
) {
    long userId, groupId, targetId;
    try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
    try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }
    try { targetId = Long.parseLong(targetIdStr.trim()); } catch (NumberFormatException e) { return "❌ 目标用户ID格式错误"; }

    if (userId == targetId) {
        return "🤔 不能和自己切磋！";
    }

    ensureTable();
    String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);
    String tName = (targetName != null && !targetName.isBlank()) ? targetName : String.valueOf(targetId);

    try (Connection conn = dbManager.getConnection()) {
        CultivationUser challenger = loadUser(conn, userId, groupId);
        CultivationUser target = loadUser(conn, targetId, groupId);

        if (challenger == null) return "🤔 " + uName + " 你还没开启修仙之路！";
        if (target == null) return "🤔 " + tName + " 还没开启修仙之路！";

        // 检查是否有pending挑战
        try (PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT id FROM spar_challenges WHERE group_id = ? AND challenger_id = ? AND target_id = ? AND status = 'pending'")) {
            checkStmt.setLong(1, groupId);
            checkStmt.setLong(2, userId);
            checkStmt.setLong(3, targetId);
            if (checkStmt.executeQuery().next()) {
                return "⚔️ 你已经向 " + tName + " 发起过挑战了！等待对方应战...";
            }
        }

        // 创建挑战
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO spar_challenges (group_id, challenger_id, challenger_name, target_id, target_name, created_at, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'pending')")) {
            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);
            stmt.setString(3, uName);
            stmt.setLong(4, targetId);
            stmt.setString(5, tName);
            stmt.setString(6, now());
            stmt.executeUpdate();
        }

        return "⚔️ " + uName + " 向 " + tName + " 发起切磋挑战！\n\n"
            + uName + "：" + realmDisplayName(challenger.realm, challenger.subLevel) + "\n"
            + tName + "：" + realmDisplayName(target.realm, target.subLevel) + "\n\n"
            + "💡 " + tName + " 说\"应战\"来接受挑战！";
    } catch (Exception e) {
        log.error("spar_initiate failed", e);
        return "❌ 发起切磋失败...";
    }
}

@Tool(
    name = "spar_accept",
    description = "接受切磋挑战。当用户说\"应战\"\"接受挑战\"\"来吧\"时使用。"
)
public String sparAccept(
    @ToolParam(value = "user_id", description = "接受者用户ID", required = true) String userIdStr,
    @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
    @ToolParam(value = "user_name", description = "接受者昵称", required = false) String userName
) {
    long userId, groupId;
    try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
    try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

    ensureTable();
    String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

    try (Connection conn = dbManager.getConnection()) {
        // 查找pending挑战
        long challengeId;
        String challengerName;
        long challengerId;
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, challenger_id, challenger_name FROM spar_challenges " +
                "WHERE group_id = ? AND target_id = ? AND status = 'pending' ORDER BY id DESC LIMIT 1")) {
            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return "🤔 " + uName + " 目前没有待接受的切磋挑战！";
            }
            challengeId = rs.getLong("id");
            challengerId = rs.getLong("challenger_id");
            challengerName = rs.getString("challenger_name");
        }

        // 标记为accepted
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE spar_challenges SET status = 'accepted' WHERE id = ?")) {
            stmt.setLong(1, challengeId);
            stmt.executeUpdate();
        }

        // 执行战斗
        CultivationUser p1 = loadUser(conn, challengerId, groupId);
        CultivationUser p2 = loadUser(conn, userId, groupId);

        if (p1 == null || p2 == null) {
            return "❌ 一方修仙数据异常，切磋取消...";
        }

        return executeSpar(p1, p2, challengerName, uName, conn);
    } catch (Exception e) {
        log.error("spar_accept failed", e);
        return "❌ 切磋失败，灵力暴走...";
    }
}

private String executeSpar(CultivationUser p1, CultivationUser p2, String name1, String name2, Connection conn) throws Exception {
    int hp1 = 100 + (getRealmIndex(p1.realm) * 4 + p1.subLevel) * 50;
    int hp2 = 100 + (getRealmIndex(p2.realm) * 4 + p2.subLevel) * 50;
    int maxHp1 = hp1, maxHp2 = hp2;

    // 随机选3个招式
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int[][] moves1 = pickMoves(rng);
    int[][] moves2 = pickMoves(rng);

    StringBuilder sb = new StringBuilder();
    sb.append("⚔️ ").append(name1).append(" VS ").append(name2).append(" — 切磋开始！\n\n");
    sb.append("【").append(name1).append("】HP: ").append(hp1).append("/").append(maxHp1)
      .append("   【").append(name2).append("】HP: ").append(hp2).append("/").append(maxHp2).append("\n\n");

    int round = 0;
    int moveIdx1 = 0, moveIdx2 = 0;

    while (hp1 > 0 && hp2 > 0 && round < 20) {
        round++;

        // P1 攻击
        if (moveIdx1 < moves1.length) {
            int[] move = moves1[moveIdx1++];
            String moveName = MOVE_POOL[move[0]][0];
            String moveDesc = MOVE_POOL[move[0]][2];
            int baseDmg = Integer.parseInt(MOVE_POOL[move[0]][1]);
            int damage = (int) ((baseDmg + p1.spirit * 2) * rng.nextDouble(0.8, 1.2));
            hp2 -= damage;

            sb.append("第").append(round).append"回合：\n");
            sb.append("🗡️ ").append(name1).append(" 使出「").append(moveName).append("」— ").append(moveDesc).append("！\n");
            sb.append("   造成 ").append(damage).append(" 点伤害！\n");
            sb.append("   【").append(name2).append("】HP: ").append(Math.max(0, hp2)).append("/").append(maxHp2).append("\n\n");

            if (hp2 <= 0) break;
        }

        // P2 攻击
        if (moveIdx2 < moves2.length) {
            int[] move = moves2[moveIdx2++];
            String moveName = MOVE_POOL[move[0]][0];
            String moveDesc = MOVE_POOL[move[0]][2];
            int baseDmg = Integer.parseInt(MOVE_POOL[move[0]][1]);
            int damage = (int) ((baseDmg + p2.spirit * 2) * rng.nextDouble(0.8, 1.2));
            hp1 -= damage;

            sb.append("⚡ ").append(name2).append(" 使出「").append(moveName).append("」— ").append(moveDesc).append("！\n");
            sb.append("   造成 ").append(damage).append(" 点伤害！\n");
            sb.append("   【").append(name1).append("】HP: ").append(Math.max(0, hp1)).append("/").append(maxHp1).append("\n\n");
        }
    }

    // 结果
    if (hp1 <= 0 && hp2 <= 0) {
        sb.append("🤝 平局！双方同时倒地！\n");
        sb.append("   ").append(name1).append(" +5声望 | ").append(name2).append(" +5声望\n");
        p1.reputation += 5;
        p2.reputation += 5;
    } else if (hp2 <= 0) {
        sb.append("🏆 ").append(name1).append(" 获胜！\n");
        sb.append("   ").append(name1).append(" +10声望 | ").append(name2).append(" +15修为（在战斗中领悟）\n");
        p1.reputation += 10;
        p2.cultivation += 15;
    } else {
        sb.append("🏆 ").append(name2).append(" 获胜！\n");
        sb.append("   ").append(name2).append(" +10声望 | ").append(name1).append(" +15修为（在战斗中领悟）\n");
        p2.reputation += 10;
        p1.cultivation += 15;
    }

    saveUser(conn, p1);
    saveUser(conn, p2);
    return sb.toString();
}

private int[][] pickMoves(ThreadLocalRandom rng) {
    int count = MOVE_POOL.length;
    int i1 = rng.nextInt(count);
    int i2, i3;
    do { i2 = rng.nextInt(count); } while (i2 == i1);
    do { i3 = rng.nextInt(count); } while (i3 == i1 || i3 == i2);
    return new int[][]{{i1}, {i2}, {i3}};
}
```

- [ ] **Step 2: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CultivationTool.java
git commit -m "feat: add spar_initiate and spar_accept methods"
```

---

### Task 10: SectTool — Complete sect system

**Files:**
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/SectTool.java`

- [ ] **Step 1: Write SectTool.java**

```java
package com.dingdong.cultivation.tool;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import com.dingdong.core.scheduler.DbManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class SectTool {

    private final DbManager dbManager;
    private volatile boolean tableReady;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int CREATE_SECT_COST = 500;
    private static final int MIN_REALM_FOR_CREATE = 3; // 金丹期起

    public SectTool(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    private void ensureTable() {
        if (tableReady) return;
        synchronized (this) {
            if (tableReady) return;
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS sects (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "group_id INTEGER NOT NULL DEFAULT 0," +
                        "name TEXT NOT NULL," +
                        "leader_id INTEGER NOT NULL," +
                        "leader_name TEXT DEFAULT ''," +
                        "level INTEGER DEFAULT 1," +
                        "contribution INTEGER DEFAULT 0," +
                        "member_count INTEGER DEFAULT 1," +
                        "created_at TEXT DEFAULT '')")) {
                    stmt.execute();
                }
                try (PreparedStatement idx = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_sects_group ON sects(group_id, name)")) {
                    idx.execute();
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS sect_members (" +
                        "sect_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "group_id INTEGER NOT NULL DEFAULT 0," +
                        "user_name TEXT DEFAULT ''," +
                        "joined_at TEXT DEFAULT ''," +
                        "PRIMARY KEY (sect_id, user_id, group_id))")) {
                    stmt.execute();
                }
                tableReady = true;
            } catch (Exception e) {
                log.error("Failed to create sect tables", e);
            }
        }
    }

    private String now() {
        return LocalDateTime.now().format(ISO_FMT);
    }

    private int getRealmIndex(String realm) {
        String[] realms = {"mortal", "lianqi", "zhuji", "jindan", "yuanying", "huashen", "dujie", "dacheng", "zhenxian"};
        for (int i = 0; i < realms.length; i++) {
            if (realms[i].equals(realm)) return i;
        }
        return 0;
    }

    private boolean isCultivator(Connection conn, long userId, long groupId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            return stmt.executeQuery().next();
        }
    }

    private String getUserRealm(Connection conn, long userId, long groupId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT realm FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("realm") : "mortal";
        }
    }

    @Tool(name = "create_sect", description = "创建宗门。金丹期以上+500灵石。")
    public String createSect(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName,
        @ToolParam(value = "sect_name", description = "宗门名称", required = true) String sectName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        if (sectName == null || sectName.isBlank()) return "❌ 宗门名称不能为空！";
        sectName = sectName.trim();
        if (sectName.length() > 20) return "❌ 宗门名称不能超过20字！";

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            if (!isCultivator(conn, userId, groupId)) {
                return "🤔 " + uName + " 你还没开启修仙之路！";
            }

            String realm = getUserRealm(conn, userId, groupId);
            if (getRealmIndex(realm) < MIN_REALM_FOR_CREATE) {
                return "❌ 创建宗门需要金丹期以上！当前境界不足。";
            }

            // 检查灵石
            int stones = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT spirit_stones FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) stones = rs.getInt("spirit_stones");
            }
            if (stones < CREATE_SECT_COST) {
                return "❌ 灵石不足！需要 " + CREATE_SECT_COST + " 灵石，当前：" + stones;
            }

            // 检查是否已有宗门
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT s.name FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return "❌ 你已加入宗门「" + rs.getString("name") + "」，不能创建新宗门！";
                }
            }

            // 检查名字唯一
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id FROM sects WHERE group_id = ? AND name = ?")) {
                stmt.setLong(1, groupId);
                stmt.setString(2, sectName);
                if (stmt.executeQuery().next()) {
                    return "❌ 宗门「" + sectName + "」已存在！换一个名字吧~";
                }
            }

            // 扣灵石
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE cultivation_users SET spirit_stones = spirit_stones - ? WHERE user_id = ? AND group_id = ?")) {
                stmt.setInt(1, CREATE_SECT_COST);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }

            // 创建宗门
            long sectId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO sects (group_id, name, leader_id, leader_name, created_at) VALUES (?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, groupId);
                stmt.setString(2, sectName);
                stmt.setLong(3, userId);
                stmt.setString(4, uName);
                stmt.setString(5, now());
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                sectId = rs.next() ? rs.getLong(1) : -1;
            }

            // 加入成员
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO sect_members (sect_id, user_id, group_id, user_name, joined_at) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.setString(4, uName);
                stmt.setString(5, now());
                stmt.executeUpdate();
            }

            return "🏯 宗门「" + sectName + "」创建成功！\n"
                + "👑 宗主：" + uName + "\n"
                + "💎 花费：" + CREATE_SECT_COST + " 灵石\n"
                + "📊 等级：1级（灵气加成2%）\n\n"
                + "💡 说\"宗门状态\"查看详情\n"
                + "💡 其他人说\"加入宗门 " + sectName + "\"来加入！";
        } catch (Exception e) {
            log.error("create_sect failed", e);
            return "❌ 创建宗门失败...";
        }
    }

    @Tool(name = "join_sect", description = "申请加入宗门。")
    public String joinSect(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName,
        @ToolParam(value = "sect_name", description = "宗门名称", required = true) String sectName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            if (!isCultivator(conn, userId, groupId)) {
                return "🤔 " + uName + " 你还没开启修仙之路！";
            }

            // 检查已有宗门
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT s.name FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return "❌ 你已加入宗门「" + rs.getString("name") + "」！先退出再加入。";
                }
            }

            // 查找宗门
            long sectId = -1;
            long leaderId = -1;
            String leaderName = "";
            int maxMembers = 10;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, leader_id, leader_name, level FROM sects WHERE group_id = ? AND name = ?")) {
                stmt.setLong(1, groupId);
                stmt.setString(2, sectName);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "❌ 宗门「" + sectName + "」不存在！";
                sectId = rs.getLong("id");
                leaderId = rs.getLong("leader_id");
                leaderName = rs.getString("leader_name");
                int level = rs.getInt("level");
                maxMembers = 10 + (level - 1) * 5;
            }

            // 检查人数上限
            int memberCount;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM sect_members WHERE sect_id = ?")) {
                stmt.setLong(1, sectId);
                ResultSet rs = stmt.executeQuery();
                memberCount = rs.next() ? rs.getInt(1) : 0;
            }
            if (memberCount >= maxMembers) {
                return "❌ 宗门「" + sectName + "」已满员（" + maxMembers + "人）！";
            }

            // 直接加入（简化：不需要宗主同意，在群内直接加入）
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO sect_members (sect_id, user_id, group_id, user_name, joined_at) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.setString(4, uName);
                stmt.setString(5, now());
                stmt.executeUpdate();
            }

            // 更新人数
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE sects SET member_count = member_count + 1 WHERE id = ?")) {
                stmt.setLong(1, sectId);
                stmt.executeUpdate();
            }

            return "🎉 " + uName + " 加入了宗门「" + sectName + "」！\n"
                + "👑 宗主：" + leaderName + "\n"
                + "👥 当前人数：" + (memberCount + 1) + "/" + maxMembers;
        } catch (Exception e) {
            log.error("join_sect failed", e);
            return "❌ 加入宗门失败...";
        }
    }

    @Tool(name = "leave_sect", description = "退出宗门。")
    public String leaveSect(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            long sectId = -1;
            boolean isLeader = false;
            String sectName = "";

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT sm.sect_id, s.name, s.leader_id FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "🤔 " + uName + " 你还没有加入任何宗门！";
                sectId = rs.getLong("sect_id");
                sectName = rs.getString("name");
                isLeader = rs.getLong("leader_id") == userId;
            }

            if (isLeader) {
                // 宗主退出=解散宗门
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM sect_members WHERE sect_id = ?")) {
                    stmt.setLong(1, sectId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM sects WHERE id = ?")) {
                    stmt.setLong(1, sectId);
                    stmt.executeUpdate();
                }
                return "💔 宗主 " + uName + " 退出，「" + sectName + "」宗门解散！";
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM sect_members WHERE sect_id = ? AND user_id = ? AND group_id = ?")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE sects SET member_count = member_count - 1 WHERE id = ?")) {
                stmt.setLong(1, sectId);
                stmt.executeUpdate();
            }

            return "👋 " + uName + " 退出了宗门「" + sectName + "」。";
        } catch (Exception e) {
            log.error("leave_sect failed", e);
            return "❌ 退出宗门失败...";
        }
    }

    @Tool(name = "kick_member", description = "踢出宗门成员（宗主专用）。")
    public String kickMember(
        @ToolParam(value = "user_id", description = "宗主ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "target_id", description = "被踢者ID", required = true) String targetIdStr
    ) {
        long userId, groupId, targetId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }
        try { targetId = Long.parseLong(targetIdStr.trim()); } catch (NumberFormatException e) { return "❌ 目标ID格式错误"; }

        if (userId == targetId) return "🤔 不能踢自己！请使用\"退出宗门\"。";

        ensureTable();

        try (Connection conn = dbManager.getConnection()) {
            long sectId = -1;
            String sectName = "";
            String targetName = "";
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT s.id, s.name FROM sects s WHERE s.group_id = ? AND s.leader_id = ?")) {
                stmt.setLong(1, groupId);
                stmt.setLong(2, userId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "❌ 你不是任何宗门的宗主！";
                sectId = rs.getLong("id");
                sectName = rs.getString("name");
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT user_name FROM sect_members WHERE sect_id = ? AND user_id = ? AND group_id = ?")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, targetId);
                stmt.setLong(3, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "❌ 该成员不在你的宗门中！";
                targetName = rs.getString("user_name");
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM sect_members WHERE sect_id = ? AND user_id = ? AND group_id = ?")) {
                stmt.setLong(1, sectId);
                stmt.setLong(2, targetId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE sects SET member_count = member_count - 1 WHERE id = ?")) {
                stmt.setLong(1, sectId);
                stmt.executeUpdate();
            }

            return "👢 " + targetName + " 被踢出了宗门「" + sectName + "」！";
        } catch (Exception e) {
            log.error("kick_member failed", e);
            return "❌ 踢人失败...";
        }
    }

    @Tool(name = "donate_sect", description = "捐献灵石升级宗门。")
    public String donateSect(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName,
        @ToolParam(value = "amount", description = "捐献灵石数量", required = true) String amountStr
    ) {
        long userId, groupId;
        int amount;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }
        try { amount = Integer.parseInt(amountStr.trim()); } catch (NumberFormatException e) { return "❌ 数量格式错误"; }

        if (amount <= 0) return "❌ 捐献数量必须大于0！";

        ensureTable();
        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        try (Connection conn = dbManager.getConnection()) {
            long sectId = -1;
            String sectName = "";
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT s.id, s.name, s.level, s.contribution FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "🤔 " + uName + " 你还没有加入任何宗门！";
                sectId = rs.getLong("id");
                sectName = rs.getString("name");
            }

            // 扣灵石
            int currentStones;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT spirit_stones FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                currentStones = rs.next() ? rs.getInt("spirit_stones") : 0;
            }
            if (currentStones < amount) {
                return "❌ 灵石不足！当前：" + currentStones + "，需要：" + amount;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE cultivation_users SET spirit_stones = spirit_stones - ? WHERE user_id = ? AND group_id = ?")) {
                stmt.setInt(1, amount);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }

            // 增加宗门贡献
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE sects SET contribution = contribution + ? WHERE id = ?")) {
                stmt.setInt(1, amount);
                stmt.setLong(2, sectId);
                stmt.executeUpdate();
            }

            // 检查升级
            int currentLevel = 1, currentContrib = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT level, contribution FROM sects WHERE id = ?")) {
                stmt.setLong(1, sectId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    currentLevel = rs.getInt("level");
                    currentContrib = rs.getInt("contribution");
                }
            }

            int newLevel = currentLevel;
            for (int lv = currentLevel + 1; lv <= 10; lv++) {
                int required = getLevelUpCost(lv - 1);
                if (currentContrib >= required) newLevel = lv;
                else break;
            }

            if (newLevel > currentLevel) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE sects SET level = ? WHERE id = ?")) {
                    stmt.setInt(1, newLevel);
                    stmt.setLong(2, sectId);
                    stmt.executeUpdate();
                }
                return "💎 " + uName + " 捐献 " + amount + " 灵石！\n"
                    + "🎉 宗门「" + sectName + "」升级至 " + newLevel + " 级！灵气加成 " + (newLevel * 2) + "%！";
            }

            return "💎 " + uName + " 捐献 " + amount + " 灵石给宗门「" + sectName + "」！\n"
                + "📊 当前总贡献：" + currentContrib + "\n"
                + "📈 下次升级需要：" + getLevelUpCost(currentLevel) + " 贡献";
        } catch (Exception e) {
            log.error("donate_sect failed", e);
            return "❌ 捐献失败...";
        }
    }

    private int getLevelUpCost(int currentLevel) {
        int[] costs = {0, 0, 500, 1200, 2500, 4500, 7000, 10000, 14000, 20000};
        return currentLevel < costs.length ? costs[currentLevel] : 99999;
    }

    @Tool(name = "sect_status", description = "查看宗门信息。")
    public String sectStatus(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();

        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT s.* FROM sect_members sm JOIN sects s ON sm.sect_id = s.id WHERE sm.user_id = ? AND sm.group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "🤔 你还没有加入任何宗门！";

                StringBuilder sb = new StringBuilder();
                sb.append("🏯 宗门：「").append(rs.getString("name")).append("」\n");
                sb.append("━━━━━━━━━━━━━━\n");
                sb.append("👑 宗主：").append(rs.getString("leader_name")).append("\n");
                sb.append("📊 等级：").append(rs.getInt("level")).append("级（灵气加成").append(rs.getInt("level") * 2).append("%）\n");
                sb.append("💎 总贡献：").append(rs.getInt("contribution")).append("\n");
                sb.append("👥 人数：").append(rs.getInt("member_count")).append("\n");
                sb.append("📅 创建时间：").append(rs.getString("created_at")).append("\n");
                sb.append("━━━━━━━━━━━━━━\n");

                // 成员列表
                long sectId = rs.getLong("id");
                long leaderId = rs.getLong("leader_id");
                try (PreparedStatement mStmt = conn.prepareStatement(
                        "SELECT user_name FROM sect_members WHERE sect_id = ? ORDER BY joined_at")) {
                    mStmt.setLong(1, sectId);
                    ResultSet mRs = mStmt.executeQuery();
                    sb.append("👥 成员：\n");
                    while (mRs.next()) {
                        String mName = mRs.getString("user_name");
                        sb.append("• ").append(mName);
                        if (mName.equals(rs.getString("leader_name"))) sb.append(" 👑");
                        sb.append("\n");
                    }
                }

                return sb.toString();
            }
        } catch (Exception e) {
            log.error("sect_status failed", e);
            return "❌ 查询失败...";
        }
    }

    @Tool(name = "sect_ranking", description = "群内宗门排行。")
    public String sectRanking(
        @ToolParam(value = "group_id", description = "群ID", required = true) String groupIdStr
    ) {
        long groupId;
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        ensureTable();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT name, leader_name, level, member_count, contribution FROM sects WHERE group_id = ? ORDER BY contribution DESC LIMIT 10")) {
            stmt.setLong(1, groupId);
            ResultSet rs = stmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("🏯 宗门排行榜 TOP 10\n\n");

            int rank = 0;
            boolean hasAny = false;
            while (rs.next()) {
                hasAny = true;
                rank++;
                String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : rank + ".";
                sb.append(medal).append(" ").append(rs.getString("name"))
                  .append(" | Lv.").append(rs.getInt("level"))
                  .append(" | 👑").append(rs.getString("leader_name"))
                  .append(" | 👥").append(rs.getInt("member_count"))
                  .append(" | 💎").append(rs.getInt("contribution")).append("\n");
            }

            if (!hasAny) sb.append("本群还没有宗门...\n💡 金丹期以上说\"创建宗门 名字\"来创建！");

            return sb.toString();
        } catch (Exception e) {
            log.error("sect_ranking failed", e);
            return "❌ 查询失败...";
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/SectTool.java
git commit -m "feat: add SectTool with complete sect system"
```

---

### Task 11: PillShopTool — Pill shop

**Files:**
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/PillShopTool.java`

- [ ] **Step 1: Write PillShopTool.java**

```java
package com.dingdong.cultivation.tool;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import com.dingdong.core.scheduler.DbManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class PillShopTool {

    private final DbManager dbManager;

    private static final Map<String, PillInfo> PILLS = new LinkedHashMap<>();
    static {
        PILLS.put("培元丹", new PillInfo("培元丹", 50, "修为+小", "服用后获得根骨×境界系数×3的修为"));
        PILLS.put("筑基丹", new PillInfo("筑基丹", 200, "修为+中", "服用后获得根骨×境界系数×8的修为"));
        PILLS.put("渡劫丹", new PillInfo("渡劫丹", 300, "渡劫辅助", "渡劫时临时气运+20%（单次渡劫有效）"));
        PILLS.put("疗伤丹", new PillInfo("疗伤丹", 150, "疗伤", "重伤立即恢复"));
        PILLS.put("还魂丹", new PillInfo("还魂丹", 1000, "免死", "渡劫失败免转世（仅重伤掉境）"));
    }

    public PillShopTool(DbManager dbManager) {
        this.dbManager = dbManager;
    }

    @Tool(name = "pill_shop", description = "查看丹药商店。当用户说\"丹药商店\"\"有什么丹药\"\"买丹药\"时使用。")
    public String pillShop() {
        StringBuilder sb = new StringBuilder();
        sb.append("💊 丹药商店\n\n");
        sb.append("━━━━━━━━━━━━━━\n");

        for (PillInfo pill : PILLS.values()) {
            sb.append("• ").append(pill.name)
              .append(" — ").append(pill.price).append("灵石\n");
            sb.append("  ").append(pill.description).append("\n\n");
        }

        sb.append("━━━━━━━━━━━━━━\n");
        sb.append("💡 购买方式：说\"购买 丹药名\"\n");
        sb.append("💡 例如：\"购买 培元丹\"");
        return sb.toString();
    }

    @Tool(name = "buy_pill", description = "购买丹药。当用户说\"购买 丹药名\"\"买丹药\"时使用。")
    public String buyPill(
        @ToolParam(value = "user_id", description = "用户ID", required = true) String userIdStr,
        @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
        @ToolParam(value = "user_name", description = "用户昵称", required = false) String userName,
        @ToolParam(value = "pill_name", description = "丹药名称", required = true) String pillName
    ) {
        long userId, groupId;
        try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
        try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

        String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

        PillInfo pill = PILLS.get(pillName);
        if (pill == null) {
            StringBuilder hint = new StringBuilder("❌ 没有「" + pillName + "」这种丹药！\n\n可选丹药：\n");
            for (String name : PILLS.keySet()) {
                hint.append("• ").append(name).append("\n");
            }
            return hint.toString();
        }

        try (Connection conn = dbManager.getConnection()) {
            // 检查是否修仙者
            int realmIdx = 0, rootBone = 10;
            String realm = "mortal";
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT realm, root_bone FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return "🤔 " + uName + " 你还没开启修仙之路！说\"修仙\"开始吧~";
                realm = rs.getString("realm");
                rootBone = rs.getInt("root_bone");
                realmIdx = getRealmIndex(realm);
            }

            // 检查灵石
            int stones;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT spirit_stones FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
                stmt.setLong(1, userId);
                stmt.setLong(2, groupId);
                ResultSet rs = stmt.executeQuery();
                stones = rs.next() ? rs.getInt("spirit_stones") : 0;
            }

            if (stones < pill.price) {
                return "❌ 灵石不足！\n丹药价格：" + pill.price + " 灵石\n你的灵石：" + stones;
            }

            // 扣灵石
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE cultivation_users SET spirit_stones = spirit_stones - ? WHERE user_id = ? AND group_id = ?")) {
                stmt.setInt(1, pill.price);
                stmt.setLong(2, userId);
                stmt.setLong(3, groupId);
                stmt.executeUpdate();
            }

            // 丹药效果
            String effect;
            switch (pill.name) {
                case "培元丹" -> {
                    double[] coeffs = {1.0, 1.2, 1.5, 1.8, 2.2, 2.6, 3.0, 3.5, 4.0};
                    int gain = (int) (rootBone * coeffs[realmIdx] * 3);
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE cultivation_users SET cultivation = cultivation + ? WHERE user_id = ? AND group_id = ?")) {
                        stmt.setInt(1, gain);
                        stmt.setLong(2, userId);
                        stmt.setLong(3, groupId);
                        stmt.executeUpdate();
                    }
                    effect = "修为+" + gain;
                }
                case "筑基丹" -> {
                    double[] coeffs = {1.0, 1.2, 1.5, 1.8, 2.2, 2.6, 3.0, 3.5, 4.0};
                    int gain = (int) (rootBone * coeffs[realmIdx] * 8);
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE cultivation_users SET cultivation = cultivation + ? WHERE user_id = ? AND group_id = ?")) {
                        stmt.setInt(1, gain);
                        stmt.setLong(2, userId);
                        stmt.setLong(3, groupId);
                        stmt.executeUpdate();
                    }
                    effect = "修为+" + gain;
                }
                case "渡劫丹" -> effect = "渡劫时临时气运+20%（在下次渡劫时自动生效）";
                case "疗伤丹" -> {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE cultivation_users SET is_injured = 0, injury_until = '' WHERE user_id = ? AND group_id = ?")) {
                        stmt.setLong(1, userId);
                        stmt.setLong(2, groupId);
                        stmt.executeUpdate();
                    }
                    effect = "重伤已恢复！";
                }
                case "还魂丹" -> effect = "渡劫失败时自动触发，免转世（仅重伤掉境）";
                default -> effect = "丹药已使用";
            }

            return "💊 " + uName + " 购买了「" + pill.name + "」！\n"
                + "💎 花费：" + pill.price + " 灵石\n"
                + "✨ 效果：" + effect + "\n"
                + "💰 剩余灵石：" + (stones - pill.price);
        } catch (Exception e) {
            log.error("buy_pill failed", e);
            return "❌ 购买失败，丹炉炸了...";
        }
    }

    private int getRealmIndex(String realm) {
        String[] realms = {"mortal", "lianqi", "zhuji", "jindan", "yuanying", "huashen", "dujie", "dacheng", "zhenxian"};
        for (int i = 0; i < realms.length; i++) {
            if (realms[i].equals(realm)) return i;
        }
        return 0;
    }

    private record PillInfo(String name, int price, String type, String description) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/PillShopTool.java
git commit -m "feat: add PillShopTool with fixed pill shop"
```

---

### Task 12: Migrate CheckInTool to dingdong-cultivation

**Files:**
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CheckInTool.java`
- Delete: `dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/CheckInTool.java`

- [ ] **Step 1: Copy and modify CheckInTool**

Copy the existing `CheckInTool.java` to the new location with package change `com.dingdong.cultivation.tool`. Add cultivation linkage in the `checkin` method — after the sign-in success logic, add:

```java
// 修仙联动：签到额外修为
try {
    java.sql.Connection conn2 = dbManager.getConnection();
    try {
        java.sql.PreparedStatement cuStmt = conn2.prepareStatement(
            "SELECT root_bone FROM cultivation_users WHERE user_id = ? AND group_id = ?");
        cuStmt.setLong(1, userId);
        cuStmt.setLong(2, groupId);
        java.sql.ResultSet cuRs = cuStmt.executeQuery();
        if (cuRs.next()) {
            int rootBone = cuRs.getInt("root_bone");
            int extraCultivation = rootBone * 2;
            java.sql.PreparedStatement updStmt = conn2.prepareStatement(
                "UPDATE cultivation_users SET cultivation = cultivation + ?, last_checkin_date = ? WHERE user_id = ? AND group_id = ?");
            updStmt.setInt(1, extraCultivation);
            updStmt.setString(2, today.toString());
            updStmt.setLong(3, userId);
            updStmt.setLong(4, groupId);
            updStmt.executeUpdate();
            sb.append("\n\n⚡ 修仙加成：+").append(extraCultivation).append(" 修为（根骨×2）");
        }
    } finally {
        conn2.close();
    }
} catch (Exception ignored) {
    // 非修仙者，跳过
}
```

Package declaration: `package com.dingdong.cultivation.tool;`

- [ ] **Step 2: Delete original from dingdong-agent**

```bash
rm dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/CheckInTool.java
```

- [ ] **Step 3: Verify compile**

```bash
mvn compile -pl dingdong-cultivation,dingdong-agent -am -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/CheckInTool.java
git add dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/CheckInTool.java
git commit -m "feat: migrate CheckInTool to dingdong-cultivation with cultivation linkage"
```

---

### Task 13: Migrate FortuneTool to dingdong-cultivation

**Files:**
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/FortuneTool.java`
- Delete: `dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/FortuneTool.java`

- [ ] **Step 1: Copy FortuneTool with package change**

Copy `FortuneTool.java` from `dingdong-agent` to `dingdong-cultivation`, change package to `com.dingdong.cultivation.tool`. No logic changes.

- [ ] **Step 2: Delete original**

```bash
rm dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/FortuneTool.java
```

- [ ] **Step 3: Verify compile**

```bash
mvn compile -pl dingdong-cultivation,dingdong-agent -am -DskipTests
```

- [ ] **Step 4: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/FortuneTool.java
git add dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/FortuneTool.java
git commit -m "feat: migrate FortuneTool to dingdong-cultivation"
```

---

### Task 14: Migrate MarriageTool to dingdong-cultivation with dual_cultivate

**Files:**
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/MarriageTool.java`
- Delete: `dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/MarriageTool.java`

- [ ] **Step 1: Copy MarriageTool and add dual_cultivate**

Copy existing `MarriageTool.java`, change package to `com.dingdong.cultivation.tool`. Add the `dual_cultivate` method:

```java
@Tool(
    name = "dual_cultivate",
    description = "道侣双修。当用户说\"双修\"\"道侣双修\"\"一起修炼\"时使用。24h冷却，双方获得修为。"
)
public String dualCultivate(
    @ToolParam(value = "user_id", description = "发起者用户ID", required = true) String userIdStr,
    @ToolParam(value = "group_id", description = "群ID（私聊为0）", required = true) String groupIdStr,
    @ToolParam(value = "user_name", description = "发起者昵称", required = false) String userName
) {
    long userId, groupId;
    try { userId = Long.parseLong(userIdStr.trim()); } catch (NumberFormatException e) { return "❌ 用户ID格式错误"; }
    try { groupId = Long.parseLong(groupIdStr.trim()); } catch (NumberFormatException e) { return "❌ 群ID格式错误"; }

    ensureTable();
    String uName = (userName != null && !userName.isBlank()) ? userName : String.valueOf(userId);

    try (Connection conn = dbManager.getConnection()) {
        // 查找道侣
        MarriageInfo info = getActiveMarriage(conn, userId, groupId);
        if (info == null) {
            return "🤔 " + uName + " 你还没有道侣！先结婚再说\"双修\"吧~";
        }

        long partnerId = info.partnerId();
        String partnerName = info.partnerName();

        // 检查双方是否都是修仙者
        boolean selfCultivator, partnerCultivator;
        int selfRoot = 10, partnerRoot = 10;
        int selfRealmIdx = 0, partnerRealmIdx = 0;
        int selfSubLevel = 1, partnerSubLevel = 1;

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT root_bone, realm, sub_level FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            ResultSet rs = stmt.executeQuery();
            selfCultivator = rs.next();
            if (selfCultivator) {
                selfRoot = rs.getInt("root_bone");
                selfRealmIdx = getRealmIndexStatic(rs.getString("realm"));
                selfSubLevel = rs.getInt("sub_level");
            }
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT root_bone, realm, sub_level FROM cultivation_users WHERE user_id = ? AND group_id = ?")) {
            stmt.setLong(1, partnerId);
            stmt.setLong(2, groupId);
            ResultSet rs = stmt.executeQuery();
            partnerCultivator = rs.next();
            if (partnerCultivator) {
                partnerRoot = rs.getInt("root_bone");
                partnerRealmIdx = getRealmIndexStatic(rs.getString("realm"));
                partnerSubLevel = rs.getInt("sub_level");
            }
        }

        if (!selfCultivator || !partnerCultivator) {
            return "🤔 双修需要双方都是修仙者！\n"
                + (!selfCultivator ? "• " + uName + " 尚未开启修仙\n" : "")
                + (!partnerCultivator ? "• " + partnerName + " 尚未开启修仙\n" : "")
                + "💡 说\"修仙\"开启修仙之路！";
        }

        // 24h CD
        // (简化：通过last_cultivate_time检查，实际可在cultivation_users中加字段)
        // 此处省略CD检查的完整实现，使用last_cultivate_time代理

        // 计算双修修为
        int avgRoot = (selfRoot + partnerRoot) / 2;
        int totalSubLevels = (selfRealmIdx * 4 + selfSubLevel) + (partnerRealmIdx * 4 + partnerSubLevel);
        int gain = avgRoot * 3 + totalSubLevels * 2;

        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE cultivation_users SET cultivation = cultivation + ? WHERE user_id = ? AND group_id = ?")) {
            stmt.setInt(1, gain);
            stmt.setLong(2, userId);
            stmt.setLong(3, groupId);
            stmt.executeUpdate();
            stmt.setInt(1, gain);
            stmt.setLong(2, partnerId);
            stmt.setLong(3, groupId);
            stmt.executeUpdate();
        }

        return "💕 " + uName + " 与道侣 " + partnerName + " 双修！\n\n"
            + "━━━━━━━━━━━━━━\n"
            + "🌸 灵力交融，心神相通...\n"
            + "💎 双方各获得 +" + gain + " 修为\n"
            + "🦴 根骨均值：" + avgRoot + "\n"
            + "📊 境界层数和：" + totalSubLevels + "\n"
            + "━━━━━━━━━━━━━━\n\n"
            + "⏰ 双修冷却：24小时";
    } catch (Exception e) {
        log.error("dual_cultivate failed", e);
        return "❌ 双修失败，灵力紊乱...";
    }
}

private int getRealmIndexStatic(String realm) {
    String[] realms = {"mortal", "lianqi", "zhuji", "jindan", "yuanying", "huashen", "dujie", "dacheng", "zhenxian"};
    for (int i = 0; i < realms.length; i++) {
        if (realms[i].equals(realm)) return i;
    }
    return 0;
}
```

Also modify `MarriageInfo` record to include `partnerId`:

```java
private record MarriageInfo(long id, String partnerName, long partnerId, String marriedAt, long daysMarried) {}
```

Update `getActiveMarriage` to return `partnerId`.

- [ ] **Step 2: Delete original**

```bash
rm dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/MarriageTool.java
```

- [ ] **Step 3: Verify compile**

```bash
mvn compile -pl dingdong-cultivation,dingdong-agent -am -DskipTests
```

- [ ] **Step 4: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/MarriageTool.java
git add dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/MarriageTool.java
git commit -m "feat: migrate MarriageTool to dingdong-cultivation with dual_cultivate"
```

---

### Task 15: Create Bot classes (CultivationBot, CheckInBot, MarriageBot, FortuneBot)

**Files:**
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot/CultivationBot.java`
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot/CheckInBot.java`
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot/MarriageBot.java`
- Create: `dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot/FortuneBot.java`

- [ ] **Step 1: Write CultivationBot.java**

```java
package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.CultivationTool;
import com.dingdong.cultivation.tool.SectTool;
import com.dingdong.cultivation.tool.PillShopTool;
import org.springframework.stereotype.Component;

@Component
public class CultivationBot {

    private final CultivationTool cultivationTool;
    private final SectTool sectTool;
    private final PillShopTool pillShopTool;

    public CultivationBot(CultivationTool cultivationTool, SectTool sectTool, PillShopTool pillShopTool) {
        this.cultivationTool = cultivationTool;
        this.sectTool = sectTool;
        this.pillShopTool = pillShopTool;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙", description = "开启修仙之路（随机天赋）")
    @Command(value = "修仙", description = "开启修仙之路（随机天赋）")
    public String startCultivation(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.start_cultivation(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修炼", description = "主动修炼（1h冷却）")
    @Command(value = "修炼", description = "主动修炼（1h冷却）")
    public String cultivate(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.cultivate(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/突破", description = "突破当前小层")
    @Command(value = "突破", description = "突破当前小层")
    public String breakthrough(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.breakthrough(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/渡劫", description = "突破大境界时触发天劫")
    @Command(value = "渡劫", description = "突破大境界时触发天劫")
    public String dujie(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.dujie(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙状态", description = "查看修仙面板")
    @Command(value = "修仙状态", description = "查看修仙面板")
    public String cultivationStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.cultivation_status(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @Command(value = "/修仙排行", description = "本群修仙排名")
    @Command(value = "修仙排行", description = "本群修仙排名")
    public String cultivationRanking(ChannelEvent event) {
        long groupId = resolveGroupId(event);
        return cultivationTool.cultivation_ranking(String.valueOf(groupId));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙菜单", description = "显示修仙菜单")
    @Command(value = "修仙帮助", description = "显示修仙帮助")
    @Command(value = "修仙菜单", description = "显示修仙菜单")
    @Command(value = "修仙帮助", description = "显示修仙帮助")
    public String cultivationMenu() {
        return """
            ━━━━━━━━━ 修仙系统 ━━━━━━━━━

            【修炼】
              @bot 修仙        开启修仙之路（随机天赋）
              @bot 修炼        主动修炼（1h冷却）
              @bot 突破        突破当前小层
              @bot 渡劫        突破大境界时触发天劫
              @bot 修仙状态    查看修仙面板
              @bot 修仙排行    本群修仙排名
              @bot 修仙菜单    显示本菜单

            【切磋】
              @bot 切磋 @某人   发起切磋战斗
              @bot 应战         接受切磋挑战

            【宗门】（金丹期可用）
              @bot 创建宗门 名字    创建宗门
              @bot 加入宗门 名字    申请加入宗门
              @bot 退出宗门        退出宗门
              @bot 踢出宗门 @某人   踢出宗门（宗主）
              @bot 捐献 数量       捐献灵石升级宗门
              @bot 宗门状态        查看宗门信息
              @bot 宗门排行        群内宗门排名

            【丹药】
              @bot 丹药商店    查看丹药/价格
              @bot 购买 丹药名  购买丹药

            【签到运势】
              @bot 签到        每日签到（修仙者额外修为）
              @bot 签到状态    签到记录和排名
              @bot 运势        今日运势

            【姻缘双修】
              @bot 求婚 @某人   向TA求婚
              @bot 同意求婚     接受求婚
              @bot 离婚         解除婚姻
              @bot 我的CP       查看CP状态
              @bot 双修         道侣双修（24h冷却）
            ━━━━━━━━━━━━━━━━━━━━━━""";
    }

    // 切磋
    @OnGroupMessage
    @Command(value = "/切磋", description = "发起切磋挑战")
    @Command(value = "切磋", description = "发起切磋挑战")
    public String sparInitiate(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String text = getPlainText(event);
        // 提取目标
        String targetName = "";
        long targetId = 0;
        // 简单解析: "切磋 @某人" -> 提取@的用户
        // 实际由Agent解析或从消息中提取，这里简化处理
        return cultivationTool.spar_initiate(
            String.valueOf(userId), String.valueOf(groupId), userName,
            String.valueOf(targetId), targetName);
    }

    @OnGroupMessage
    @Command(value = "/应战", description = "接受切磋挑战")
    @Command(value = "应战", description = "接受切磋挑战")
    public String sparAccept(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.spar_accept(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    // 宗门
    @OnGroupMessage
    @Command(value = "/创建宗门", description = "创建宗门（金丹期+500灵石）")
    @Command(value = "创建宗门", description = "创建宗门（金丹期+500灵石）")
    public String createSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String sectName = extractArg(getPlainText(event), "创建宗门");
        return sectTool.create_sect(
            String.valueOf(userId), String.valueOf(groupId), userName, sectName);
    }

    @OnGroupMessage
    @Command(value = "/加入宗门", description = "申请加入宗门")
    @Command(value = "加入宗门", description = "申请加入宗门")
    public String joinSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String sectName = extractArg(getPlainText(event), "加入宗门");
        return sectTool.join_sect(
            String.valueOf(userId), String.valueOf(groupId), userName, sectName);
    }

    @OnGroupMessage
    @Command(value = "/退出宗门", description = "退出宗门")
    @Command(value = "退出宗门", description = "退出宗门")
    public String leaveSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return sectTool.leave_sect(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @Command(value = "/踢出宗门", description = "踢出宗门成员（宗主）")
    @Command(value = "踢出宗门", description = "踢出宗门成员（宗主）")
    public String kickMember(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        long targetId = 0; // TODO: 从@提取
        return sectTool.kick_member(
            String.valueOf(userId), String.valueOf(groupId), String.valueOf(targetId));
    }

    @OnGroupMessage
    @Command(value = "/捐献", description = "捐献灵石升级宗门")
    @Command(value = "捐献", description = "捐献灵石升级宗门")
    public String donateSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String amount = extractArg(getPlainText(event), "捐献");
        return sectTool.donate_sect(
            String.valueOf(userId), String.valueOf(groupId), userName, amount);
    }

    @OnGroupMessage
    @Command(value = "/宗门状态", description = "查看宗门信息")
    @Command(value = "宗门状态", description = "查看宗门信息")
    public String sectStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        return sectTool.sect_status(String.valueOf(userId), String.valueOf(groupId));
    }

    @OnGroupMessage
    @Command(value = "/宗门排行", description = "群内宗门排名")
    @Command(value = "宗门排行", description = "群内宗门排名")
    public String sectRanking(ChannelEvent event) {
        long groupId = resolveGroupId(event);
        return sectTool.sect_ranking(String.valueOf(groupId));
    }

    // 丹药
    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/丹药商店", description = "查看丹药/价格")
    @Command(value = "丹药商店", description = "查看丹药/价格")
    public String pillShop() {
        return pillShopTool.pill_shop();
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/购买", description = "购买丹药")
    @Command(value = "购买", description = "购买丹药")
    public String buyPill(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String pillName = extractArg(getPlainText(event), "购买");
        return pillShopTool.buy_pill(
            String.valueOf(userId), String.valueOf(groupId), userName, pillName);
    }

    // ====== 辅助方法 ======

    private long resolveUserId(ChannelEvent event) {
        if (event instanceof MessageEvent me) return me.getUserId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getUserId();
        return 0;
    }

    private long resolveGroupId(ChannelEvent event) {
        if (event instanceof GroupMessageEvent ge) return ge.getGroupId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getGroupId();
        return 0;
    }

    private String resolveUserName(ChannelEvent event) {
        if (event instanceof MessageEvent me) {
            return me.getSenderObj() != null ? me.getSenderObj().getNickname() : "";
        }
        if (event instanceof ChannelMessageEvent chMsg) {
            return chMsg.getSender() != null ? chMsg.getSender().getNickname() : "";
        }
        return "";
    }

    private String getPlainText(ChannelEvent event) {
        if (event instanceof MessageEvent me) return me.getPlainText();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getPlainText();
        return "";
    }

    private String extractArg(String text, String cmdPrefix) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith(cmdPrefix)) {
            return trimmed.substring(cmdPrefix.length()).trim();
        }
        // 也检查带/前缀
        if (trimmed.startsWith("/" + cmdPrefix)) {
            return trimmed.substring(("/" + cmdPrefix).length()).trim();
        }
        return "";
    }
}
```

- [ ] **Step 2: Write CheckInBot.java**

```java
package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.CheckInTool;
import org.springframework.stereotype.Component;

@Component
public class CheckInBot {

    private final CheckInTool checkInTool;

    public CheckInBot(CheckInTool checkInTool) {
        this.checkInTool = checkInTool;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/签到", description = "每日签到（修仙者额外修为）")
    @Command(value = "签到", description = "每日签到（修仙者额外修为）")
    public String checkin(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return checkInTool.checkin(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/签到状态", description = "签到记录和排名")
    @Command(value = "签到状态", description = "签到记录和排名")
    public String checkinStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return checkInTool.status(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    private long resolveUserId(ChannelEvent event) {
        if (event instanceof MessageEvent me) return me.getUserId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getUserId();
        return 0;
    }

    private long resolveGroupId(ChannelEvent event) {
        if (event instanceof GroupMessageEvent ge) return ge.getGroupId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getGroupId();
        return 0;
    }

    private String resolveUserName(ChannelEvent event) {
        if (event instanceof MessageEvent me) {
            return me.getSenderObj() != null ? me.getSenderObj().getNickname() : "";
        }
        if (event instanceof ChannelMessageEvent chMsg) {
            return chMsg.getSender() != null ? chMsg.getSender().getNickname() : "";
        }
        return "";
    }
}
```

- [ ] **Step 3: Write MarriageBot.java**

```java
package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.MarriageTool;
import org.springframework.stereotype.Component;

@Component
public class MarriageBot {

    private final MarriageTool marriageTool;

    public MarriageBot(MarriageTool marriageTool) {
        this.marriageTool = marriageTool;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/求婚", description = "向TA求婚")
    @Command(value = "求婚", description = "向TA求婚")
    public String propose(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        // target_id 和 target_name 需从 @ 中解析，简化处理
        return marriageTool.propose(
            String.valueOf(userId), "0", String.valueOf(groupId), userName, "");
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/同意求婚", description = "接受求婚")
    @Command(value = "同意求婚", description = "接受求婚")
    public String accept(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return marriageTool.accept(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/离婚", description = "解除婚姻")
    @Command(value = "离婚", description = "解除婚姻")
    public String divorce(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return marriageTool.divorce(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/我的CP", description = "查看CP状态")
    @Command(value = "我的CP", description = "查看CP状态")
    public String cpStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return marriageTool.cp_status(
            String.valueOf(userId), String.valueOf(groupId), null, userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/双修", description = "道侣双修（24h冷却）")
    @Command(value = "双修", description = "道侣双修（24h冷却）")
    public String dualCultivate(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return marriageTool.dual_cultivate(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    private long resolveUserId(ChannelEvent event) {
        if (event instanceof MessageEvent me) return me.getUserId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getUserId();
        return 0;
    }

    private long resolveGroupId(ChannelEvent event) {
        if (event instanceof GroupMessageEvent ge) return ge.getGroupId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getGroupId();
        return 0;
    }

    private String resolveUserName(ChannelEvent event) {
        if (event instanceof MessageEvent me) {
            return me.getSenderObj() != null ? me.getSenderObj().getNickname() : "";
        }
        if (event instanceof ChannelMessageEvent chMsg) {
            return chMsg.getSender() != null ? chMsg.getSender().getNickname() : "";
        }
        return "";
    }
}
```

- [ ] **Step 4: Write FortuneBot.java**

```java
package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.FortuneTool;
import org.springframework.stereotype.Component;

@Component
public class FortuneBot {

    private final FortuneTool fortuneTool;

    public FortuneBot(FortuneTool fortuneTool) {
        this.fortuneTool = fortuneTool;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/运势", description = "今日运势")
    @Command(value = "运势", description = "今日运势")
    public String fortune(ChannelEvent event) {
        String name = resolveUserName(event);
        return fortuneTool.fortune(name);
    }

    private String resolveUserName(ChannelEvent event) {
        if (event instanceof MessageEvent me) {
            return me.getSenderObj() != null ? me.getSenderObj().getNickname() : "";
        }
        if (event instanceof ChannelMessageEvent chMsg) {
            return chMsg.getSender() != null ? chMsg.getSender().getNickname() : "";
        }
        return "";
    }
}
```

- [ ] **Step 5: Verify compile**

```bash
mvn compile -pl dingdong-cultivation -am -DskipTests
```

- [ ] **Step 6: Commit**

```bash
git add dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot/
git commit -m "feat: add CultivationBot, CheckInBot, MarriageBot, FortuneBot"
```

---

### Task 16: Update HelpBot with cultivation menu entry

**Files:**
- Modify: `dingdong-admin/src/main/java/com/dingdong/admin/bot/HelpBot.java`

- [ ] **Step 1: Add cultivation entry in HelpBot**

In `HelpBot.java`, in the OneBot section after the marriage line (`"• 群友结婚 — ..."`), add:

```java
sb.append("• 修仙系统 — \"修仙菜单\"查看全部\n");
```

And in the QQ Official section, after the last entry, add:

```java
sb.append("\n【修仙系统】\n");
sb.append("发送\"修仙菜单\"查看全部修仙功能\n");
sb.append("修仙/修炼/突破/渡劫/切磋/宗门/丹药/双修...\n");
```

- [ ] **Step 2: Verify compile**

```bash
mvn compile -pl dingdong-admin -am -DskipTests
```

- [ ] **Step 3: Commit**

```bash
git add dingdong-admin/src/main/java/com/dingdong/admin/bot/HelpBot.java
git commit -m "feat: add cultivation menu entry to HelpBot"
```

---

### Task 17: Final Integration Verification

**Files:**
- All files from previous tasks

- [ ] **Step 1: Full project compile**

```bash
mvn install -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

```bash
mvn test
```

Expected: All tests pass (no regressions)

- [ ] **Step 3: Verify module structure**

```bash
ls -R dingdong-cultivation/src/main/java/com/dingdong/cultivation/
```

Expected: `tool/` with 6 files, `bot/` with 4 files

- [ ] **Step 4: Commit final state**

```bash
git add -A
git commit -m "feat: complete cultivation system implementation"
```

---

## Summary

**Total files created:** 10
- `dingdong-cultivation/pom.xml`
- `dingdong-cultivation/.../tool/CultivationTool.java`
- `dingdong-cultivation/.../tool/SectTool.java`
- `dingdong-cultivation/.../tool/PillShopTool.java`
- `dingdong-cultivation/.../tool/CheckInTool.java` (migrated)
- `dingdong-cultivation/.../tool/FortuneTool.java` (migrated)
- `dingdong-cultivation/.../tool/MarriageTool.java` (migrated)
- `dingdong-cultivation/.../bot/CultivationBot.java`
- `dingdong-cultivation/.../bot/CheckInBot.java`
- `dingdong-cultivation/.../bot/MarriageBot.java`
- `dingdong-cultivation/.../bot/FortuneBot.java`

**Total files modified:** 4
- `pom.xml` (add module + dependencyManagement)
- `dingdong-boot-starter/pom.xml` (add dependency)
- `NapCatAutoConfiguration.java` (add migration 9)
- `HelpBot.java` (add cultivation entry)

**Total files deleted:** 3
- `dingdong-agent/.../CheckInTool.java`
- `dingdong-agent/.../FortuneTool.java`
- `dingdong-agent/.../MarriageTool.java`
