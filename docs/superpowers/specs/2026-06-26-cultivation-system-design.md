# 修仙系统设计文档

**日期**: 2026-06-26
**版本**: 1.0.0
**状态**: 设计已确认，待实施

---

## 一、概述

在群聊中实现修仙RPG系统（中度互动），包含修炼/渡劫/切磋/宗门/丹药/双修等完整修仙循环。同时将修仙相关的签到、运势、婚姻系统从 `dingdong-agent` 迁入新模块 `dingdong-cultivation`，实现独立管理和扩展。

## 二、新模块：`dingdong-cultivation`

### 2.1 Maven 坐标

```xml
<groupId>com.napcat</groupId>
<artifactId>dingdong-cultivation</artifactId>
<version>1.0.0</version>
```

### 2.2 依赖

```xml
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
```

不依赖 `dingdong-agent`，不依赖 `dingdong-channel-api`。只依赖 `dingdong-core`（`@Tool`/`@Command` 注解、`DbManager`、`HandlerRegistry`）。

### 2.3 目录结构

```
dingdong-cultivation/
  pom.xml
  src/main/java/com/dingdong/cultivation/
    tool/                         ← @Tool 方法（Agent自动调用）
      CultivationTool.java       修仙核心（修炼/突破/渡劫/属性/排行/意外事件）
      SectTool.java              宗门系统（创建/加入/踢人/捐献/排行）
      PillShopTool.java          丹药商店（查看/购买）
      CheckInTool.java           [迁入] 签到（含修仙修为加成）
      FortuneTool.java           [迁入] 运势
      MarriageTool.java          [迁入] 婚姻（含双修）
    bot/                          ← @Command 方法（用户直接输入）
      CultivationBot.java        修仙菜单 + 各命令转发
      CheckInBot.java            签到命令
      MarriageBot.java           婚姻命令
      FortuneBot.java            运势命令
```

### 2.4 迁出清单

以下文件从 `dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/` 迁移到 `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/`，包名同步修改：

| 源文件 | 目标文件 | 变更 |
|--------|---------|------|
| `CheckInTool.java` | `CheckInTool.java` | 增加修仙联动（签到额外修为） |
| `FortuneTool.java` | `FortuneTool.java` | 不变 |
| `MarriageTool.java` | `MarriageTool.java` | 增加双修功能 |
| — | `CultivationTool.java` | 新增：修仙核心 |
| — | `SectTool.java` | 新增：宗门系统 |
| — | `PillShopTool.java` | 新增：丹药商店 |

以下纯娱乐工具 **不迁移**，留在 `dingdong-agent/tool/builtin/`：
`TarotTool`, `PunishmentWheelTool`, `DriftingBottleTool`, `DiceTool`, `RPSTool`, `EightBallTool`, `NumberGuessTool`, `TruthOrDareTool`, `RiddleTool`, `TongueTwisterTool`, `RandomChoiceTool`, `TextEffectTool`, `EncodingTool`, `MemeTool`, `JokeTool`

### 2.5 `dingdong-agent` 清理

迁出后：
- 删除 `dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/CheckInTool.java`
- 删除 `dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/FortuneTool.java`
- 删除 `dingdong-agent/src/main/java/com/dingdong/agent/tool/builtin/MarriageTool.java`

### 2.6 父 POM 注册

在 `pom.xml` 的 `<modules>` 中添加：
```xml
<module>dingdong-cultivation</module>
```

### 2.7 模块依赖链

```
dingdong-cultivation
  └── dingdong-core

dingdong-boot-starter
  ├── dingdong-core
  ├── dingdong-agent (optional)
  ├── dingdong-qqofficial (optional)
  └── dingdong-cultivation (新增, optional)

dingdong-admin
  └── dingdong-boot-starter
```

`dingdong-boot-starter/pom.xml` 新增依赖：
```xml
<dependency>
    <groupId>com.napcat</groupId>
    <artifactId>dingdong-cultivation</artifactId>
    <optional>true</optional>
</dependency>
```

`NapCatAutoConfiguration` 已有 `@ComponentScan("com.dingdong")`，新模块的 `com.dingdong.cultivation` 包自动被扫描，`@Tool` 和 `@Command` 自动注册，无需额外配置。

---

## 三、修仙核心设计

### 3.1 境界体系

```
凡人 → 练气 → 筑基 → 金丹 → 元婴 → 化神 → 渡劫 → 大乘 → 真仙
```

- 每大境界 4 小层：初期(1) → 中期(2) → 后期(3) → 圆满(4)
- 突破小层：消耗指定修为值，100%成功，清空修为累进下一层
- 突破大境界（圆满→下一大境界初期）：触发多轮天劫
- 修为需求：`baseCost × 1.5^(境界层数)`，baseCost 每大境界递增

### 3.2 属性体系

| 属性 | 范围 | 初始值 | 用途 |
|------|------|--------|------|
| 根骨 | 5-15 | 随机 | 修炼效率（修为获取量=根骨×境界系数） |
| 气运 | 5-15 | 随机 | 渡劫成功率、奇遇概率、规避意外 |
| 灵力 | 5-15 | 随机 | 切磋伤害、护法效果 |
| 灵石 | — | 100 | 丹药购买、宗门创建/捐献 |
| 声望 | — | 0 | 切磋获胜+、宗门排行权重 |

首次"修仙"时随机生成三维属性（ThreadLocalRandom 5-15），不可更改。属性可通过奇遇事件永久提升。

### 3.3 修炼机制

三种修为获取方式：

1. **主动修炼**（"修炼"/"修仙"）：1小时 CD，获得 `根骨 × 境界系数` 修为
2. **签到联动**（"签到"）：签到成功额外获得 `根骨 × 2` 修为（仅修仙者）
3. **后台收益**：两次查询间的时间差自动计算累积修为。`被动修为 = 时间差(小时) × 根骨 × 境界系数 × 0.5`，上限 8 小时

加成来源：
- 道侣持续加成：+10% 修为获取
- 宗门灵气加成：宗门等级 × 2%
- 丹药临时加成：培元丹/筑基丹

### 3.4 意外事件

每次"修炼"触发时概率判定（基础概率 10%，气运每高1点 -0.5%）：

| 事件 | 概率 | 效果 |
|------|------|------|
| 走火入魔 | ~5% | 重伤状态，当前修为减半，修炼效率-50%（持续1小时或吃疗伤丹） |
| 天降奇遇 | ~3% | 修为×3暴击 + "天降祥瑞！" |
| 上古遗迹 | ~1% | 随机属性永久+1 + "你在修炼中感应到上古遗迹..." |
| 无事发生 | ~91% | 正常修炼 |

### 3.5 渡劫机制

突破大境界（小层圆满→下一大境界初期）时触发：

- 天雷轮数：练气3轮 → 真仙9轮（每大境界+1轮）
- 每轮基础成功率：`60% + 气运×2%`（上限90%）
- 丹药加成：渡劫丹临时+20%成功率（单次渡劫有效，多轮持续）
- 护法加成：同宗门群友可"护法"消耗自己修为，每人+5%成功率（上限3人=+15%）
- 连续成功才算渡劫成功
- 失败：80%概率转世重修（修为归零、境界重置凡人、根骨保留50%、灵石和宗门保留），20%概率重伤掉1-2大境界（随机）

渡劫过程逐轮输出，如：
```
⚡ 天劫降临！第1/5轮天雷...
✅ 第1轮：成功通过！（剩余4轮）
❌ 第3轮：被天雷击中！渡劫失败！
💀 身死道消...进入转世重修...
```

### 3.6 重伤/死亡机制

- **重伤**（走火入魔/渡劫失败小概率）：修炼效率-50%，持续1小时。可用疗伤丹立即恢复
- **转世重修**（渡劫失败大概率/爆体而亡）：修为归零、境界重置凡人、根骨保留50%（向上取整）、灵石和宗门关系保留、声望清零
- 转世重修后自动获得"轮回印记"buff：修炼效率+20%（仅一次转世有效，再死则无）

---

## 四、切磋系统

### 4.1 战斗模型

伪回合制，双方轮流攻击。每回合一方攻击另一方承受伤害。

### 4.2 功法招式池

```java
// 每个修仙者随机3个招式
MOVE_POOL = [
    {name: "天雷破", baseDmg: 20, desc: "引天雷之力，直劈而下"},
    {name: "玄冰咒", baseDmg: 18, desc: "凝结玄冰，刺骨寒心"},
    {name: "烈焰掌", baseDmg: 22, desc: "掌心烈焰，焚尽万物"},
    {name: "万剑诀", baseDmg: 25, desc: "万剑齐发，无处可逃"},
    {name: "风刃术", baseDmg: 15, desc: "无形风刃，削铁如泥"},
    {name: "裂地斩", baseDmg: 24, desc: "一刀裂地，山河震动"},
    {name: "噬魂诀", baseDmg: 19, desc: "吞噬神魂，直击元神"},
    {name: "紫电青光", baseDmg: 21, desc: "紫电环绕，青光一闪"},
    {name: "太虚步", baseDmg: 16, desc: "身形飘忽，出其不意"},
    {name: "金刚印", baseDmg: 23, desc: "金刚伏魔，一掌定乾坤"},
    {name: "星辰陨", baseDmg: 28, desc: "引星辰之力，陨落凡尘"},
    {name: "苍龙吟", baseDmg: 26, desc: "苍龙咆哮，声震九霄"},
]
```

### 4.3 伤害计算

```
实际伤害 = (招式基础伤害 + 灵力×2) × 随机波动(0.8~1.2)
HP = 100 + 境界层数×50
```

### 4.4 战斗流程

1. 发起方说"切磋 @某人"
2. 目标说"应战"
3. 双方各有3个随机招式，轮流选择释放
4. 每回合输出招式名+效果描述+伤害数值+剩余HP
5. HP先归零的一方输
6. 赢者+10声望，输者+少量修为（"在战斗中领悟"）

### 4.5 切磋输出示例

```
⚔️ 张三 VS 李四 — 切磋开始！

【张三】HP: 250/250   【李四】HP: 200/200

第1回合：
🗡️ 张三 使出「天雷破」— 引天雷之力，直劈而下！
   造成 42 点伤害！
   【李四】HP: 158/200

⚡ 李四 使出「紫电青光」— 紫电环绕，青光一闪！
   造成 38 点伤害！
   【张三】HP: 212/250

...（后续回合）...

🏆 张三 获胜！
   张三 +10声望 | 李四 +15修为（在战斗中领悟）
```

---

## 五、宗门系统

### 5.1 核心规则

- **创建**：金丹期以上 + 500灵石，群内唯一宗门名
- **加入**：任意修仙者可申请，需宗主同意
- **退出**：自由退宗（不可退自己创建的，需先转让或解散）
- **踢人**：仅宗主
- **解散**：宗主退出时自动解散
- **升级**：成员捐献灵石，累积贡献达阈值自动升级（1-10级）

### 5.2 宗门等级

| 等级 | 升级所需贡献 | 灵气加成 | 最大人数 |
|------|-------------|---------|---------|
| 1 | 0 | 2% | 10 |
| 2 | 500 | 4% | 15 |
| 3 | 1200 | 6% | 20 |
| ... | ... | ... | ... |
| 10 | 20000 | 20% | 50 |

### 5.3 排行榜

按 `成员声望总和 × 宗门等级系数` 排序，显示 TOP 10。

---

## 六、丹药商店

### 6.1 固定丹药

| 丹药 | 价格（灵石） | 效果 |
|------|------------|------|
| 培元丹 | 50 | +修为(小)：根骨×境界系数×3 |
| 筑基丹 | 200 | +修为(中)：根骨×境界系数×8 |
| 渡劫丹 | 300 | 渡劫临时气运+20%（单次渡劫有效） |
| 疗伤丹 | 150 | 重伤立即恢复 |
| 还魂丹 | 1000 | 渡劫失败免转世（仅重伤掉境） |

### 6.2 命令

- "丹药商店"：列出所有丹药及价格
- "购买 丹药名"：购买指定丹药（灵石不足时提示）

### 6.3 扩展预留

代码中预留 `PillRecipe` 类和 `materials` 字段（不启用），后续可扩展为炼丹系统。

---

## 七、双修系统

### 7.1 与婚姻联动

- 必须先通过 `MarriageTool` 结婚
- 结婚后自动成为"道侣"
- 道侣加成：双方+10%修为获取（持续被动）
- 双修指令（"双修"）：双方各获一次性修为，24h CD
- 离婚 → 道侣关系自动解除，加成消失
- 一方转世 → 道侣关系自动解除

### 7.2 双修修为计算

```
修为 = (双方根骨平均值 × 3) + (双方境界层数之和 × 2)
```

---

## 八、签到运势（迁入）

### 8.1 CheckInTool 变更

在原有签到基础上增加：
- 签到成功后，如果用户是修仙者，额外获得 `根骨 × 2` 修为
- 签到状态中显示修仙境界信息（如果有）

### 8.2 FortuneTool

不变，保持原样迁入。

### 8.3 MarriageTool 变更

在原有婚姻基础上增加：
- 查看CP状态时，如果双方都是修仙者，显示道侣加成信息
- 新增 `dual_cultivate` tool："双修"指令

---

## 九、触发方式（双通道统一）

### 9.1 统一规则

所有修仙命令通过 `@bot 命令` 或 `唤醒词 命令` 触发。

| 通道 | 触发方式 | 示例 |
|------|---------|------|
| OneBot11 (QQ) | `@bot 修仙` 或 `唤醒词 修仙` | `@小丁 修仙` |
| QQ Official | `@bot 修仙` 或 `唤醒词 修仙` | `@小丁 修仙` |

### 9.2 双入口架构

每个功能同时提供两种入口：

- **`@Tool` 注解方法**（在 `tool/` 包）：Agent 通过 AI 判断用户意图后自动调用。参数为 `(user_id, group_id, user_name, ...)` 的 String 形式
- **`@Command` 注解方法**（在 `bot/` 包）：用户直接输入命令，`HandlerRegistry` 匹配后调用。方法接收 `ChannelEvent`，从中提取 userId/groupId 后调用同一套业务逻辑

### 9.3 修仙菜单命令

"修仙菜单"/"修仙帮助" 返回以下完整菜单：

```
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
  @bot 同意入宗 @某人   同意申请（宗主）
  @bot 踢出宗门 @某人   踢出宗门（宗主）
  @bot 退出宗门        退出宗门
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
━━━━━━━━━━━━━━━━━━━━━━
```

---

## 十、SQLite 表设计

### 10.1 修仙用户表

```sql
CREATE TABLE IF NOT EXISTS cultivation_users (
    user_id INTEGER NOT NULL,
    group_id INTEGER NOT NULL DEFAULT 0,
    user_name TEXT DEFAULT '',
    realm TEXT NOT NULL DEFAULT 'mortal',     -- 境界
    sub_level INTEGER DEFAULT 1,              -- 小层 1-4
    cultivation INTEGER DEFAULT 0,            -- 当前修为值
    root_bone INTEGER DEFAULT 10,             -- 根骨 5-15
    luck INTEGER DEFAULT 10,                  -- 气运 5-15
    spirit INTEGER DEFAULT 10,                -- 灵力 5-15
    spirit_stones INTEGER DEFAULT 100,        -- 灵石
    reputation INTEGER DEFAULT 0,             -- 声望
    last_cultivate_time TEXT DEFAULT '',      -- 上次修炼时间(ISO)
    last_checkin_date TEXT DEFAULT '',        -- 上次签到日期
    is_injured INTEGER DEFAULT 0,            -- 重伤状态
    has_reborn INTEGER DEFAULT 0,            -- 是否已转世过（轮回印记）
    created_at TEXT DEFAULT '',
    PRIMARY KEY (user_id, group_id)
);
```

### 10.2 宗门表

```sql
CREATE TABLE IF NOT EXISTS sects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL DEFAULT 0,
    name TEXT NOT NULL,
    leader_id INTEGER NOT NULL,
    leader_name TEXT DEFAULT '',
    level INTEGER DEFAULT 1,
    contribution INTEGER DEFAULT 0,
    member_count INTEGER DEFAULT 1,
    created_at TEXT DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_sects_group ON sects(group_id, name);
```

### 10.3 宗门成员表

```sql
CREATE TABLE IF NOT EXISTS sect_members (
    sect_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    group_id INTEGER NOT NULL DEFAULT 0,
    user_name TEXT DEFAULT '',
    joined_at TEXT DEFAULT '',
    PRIMARY KEY (sect_id, user_id, group_id)
);
```

### 10.4 签到表（迁入，不变）

```sql
CREATE TABLE IF NOT EXISTS daily_checkin (
    user_id INTEGER NOT NULL,
    group_id INTEGER NOT NULL DEFAULT 0,
    user_name TEXT DEFAULT '',
    last_checkin TEXT NOT NULL DEFAULT '',
    streak INTEGER DEFAULT 0,
    total_points INTEGER DEFAULT 0,
    total_checkins INTEGER DEFAULT 0,
    PRIMARY KEY (user_id, group_id)
);
```

### 10.5 婚姻表（迁入，不变）

```sql
CREATE TABLE IF NOT EXISTS marriages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL DEFAULT 0,
    user1_id INTEGER NOT NULL,
    user1_name TEXT DEFAULT '',
    user2_id INTEGER NOT NULL,
    user2_name TEXT DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending',
    proposed_at TEXT NOT NULL DEFAULT '',
    married_at TEXT DEFAULT '',
    divorced_at TEXT DEFAULT ''
);
```

### 10.6 切磋挑战临时表

```sql
CREATE TABLE IF NOT EXISTS spar_challenges (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL DEFAULT 0,
    challenger_id INTEGER NOT NULL,
    challenger_name TEXT DEFAULT '',
    target_id INTEGER NOT NULL,
    target_name TEXT DEFAULT '',
    created_at TEXT DEFAULT '',
    status TEXT DEFAULT 'pending'    -- pending/accepted/expired
);
```

---

## 十一、@Tool 方法清单

### CultivationTool

| Tool Name | 描述 | 参数 |
|-----------|------|------|
| `start_cultivation` | 开启修仙 | user_id, group_id, user_name |
| `cultivate` | 主动修炼 | user_id, group_id, user_name |
| `breakthrough` | 突破小层 | user_id, group_id, user_name |
| `dujie` | 渡劫 | user_id, group_id, user_name |
| `cultivation_status` | 查看修仙状态 | user_id, group_id, user_name |
| `cultivation_ranking` | 修仙排行榜 | group_id |
| `spar_initiate` | 发起切磋 | user_id, group_id, user_name, target_id, target_name |
| `spar_accept` | 应战 | user_id, group_id, user_name |

### SectTool

| Tool Name | 描述 | 参数 |
|-----------|------|------|
| `create_sect` | 创建宗门 | user_id, group_id, user_name, sect_name |
| `join_sect` | 申请加入 | user_id, group_id, user_name, sect_name |
| `accept_join` | 同意入宗 | user_id, group_id, target_id |
| `kick_member` | 踢出宗门 | user_id, group_id, target_id |
| `leave_sect` | 退出宗门 | user_id, group_id, user_name |
| `donate_sect` | 捐献灵石 | user_id, group_id, user_name, amount |
| `sect_status` | 查看宗门 | user_id, group_id |
| `sect_ranking` | 宗门排行 | group_id |

### PillShopTool

| Tool Name | 描述 | 参数 |
|-----------|------|------|
| `pill_shop` | 查看丹药 | — |
| `buy_pill` | 购买丹药 | user_id, group_id, user_name, pill_name |

### CheckInTool（迁入 + 修仙联动）

| Tool Name | 描述 | 参数 |
|-----------|------|------|
| `daily_checkin` | 签到 | user_id, group_id, user_name |
| `checkin_status` | 签到状态 | user_id, group_id, user_name |

### FortuneTool（迁入，不变）

| Tool Name | 描述 | 参数 |
|-----------|------|------|
| `daily_fortune` | 运势 | name |

### MarriageTool（迁入 + 双修）

| Tool Name | 描述 | 参数 |
|-----------|------|------|
| `marry_propose` | 求婚 | user_id, target_id, group_id, user_name, target_name |
| `marry_accept` | 同意求婚 | user_id, group_id, user_name |
| `marry_divorce` | 离婚 | user_id, group_id, user_name |
| `marry_cp_status` | CP状态 | user_id, group_id, target_id?, user_name |
| `marry_stats` | 婚姻统计 | group_id |
| `dual_cultivate` | 双修 | user_id, group_id, user_name |

---

## 十二、@Command Bot 类清单

### CultivationBot
- `@Command("/修仙")` / `@Command("修仙")` → 转发到 `CultivationTool.start_cultivation`
- `@Command("/修炼")` / `@Command("修炼")` → 转发到 `CultivationTool.cultivate`
- `@Command("/突破")` / `@Command("突破")` → 转发到 `CultivationTool.breakthrough`
- `@Command("/渡劫")` / `@Command("渡劫")` → 转发到 `CultivationTool.dujie`
- `@Command("/修仙状态")` / `@Command("修仙状态")` → 转发到 `CultivationTool.cultivation_status`
- `@Command("/修仙排行")` / `@Command("修仙排行")` → 转发到 `CultivationTool.cultivation_ranking`
- `@Command("/修仙菜单")` / `@Command("修仙帮助")` / `@Command("修仙菜单")` → 返回菜单
- `@Command("/切磋")` / `@Command("切磋")` → 转发到 `CultivationTool.spar_initiate`
- `@Command("/应战")` / `@Command("应战")` → 转发到 `CultivationTool.spar_accept`
- `@Command("/创建宗门")` / `@Command("创建宗门")` → 转发到 `SectTool.create_sect`
- `@Command("/加入宗门")` / `@Command("加入宗门")` → 转发到 `SectTool.join_sect`
- `@Command("/同意入宗")` / `@Command("同意入宗")` → 转发到 `SectTool.accept_join`
- `@Command("/踢出宗门")` / `@Command("踢出宗门")` → 转发到 `SectTool.kick_member`
- `@Command("/退出宗门")` / `@Command("退出宗门")` → 转发到 `SectTool.leave_sect`
- `@Command("/捐献")` / `@Command("捐献")` → 转发到 `SectTool.donate_sect`
- `@Command("/宗门状态")` / `@Command("宗门状态")` → 转发到 `SectTool.sect_status`
- `@Command("/宗门排行")` / `@Command("宗门排行")` → 转发到 `SectTool.sect_ranking`
- `@Command("/丹药商店")` / `@Command("丹药商店")` → 转发到 `PillShopTool.pill_shop`
- `@Command("/购买")` / `@Command("购买")` → 转发到 `PillShopTool.buy_pill`

### CheckInBot
- `@Command("/签到")` / `@Command("签到")` → 转发到 `CheckInTool.daily_checkin`
- `@Command("/签到状态")` / `@Command("签到状态")` → 转发到 `CheckInTool.checkin_status`

### MarriageBot
- `@Command("/求婚")` / `@Command("求婚")` → 转发到 `MarriageTool.marry_propose`
- `@Command("/同意求婚")` / `@Command("同意求婚")` → 转发到 `MarriageTool.marry_accept`
- `@Command("/离婚")` / `@Command("离婚")` → 转发到 `MarriageTool.marry_divorce`
- `@Command("/我的CP")` / `@Command("我的CP")` → 转发到 `MarriageTool.marry_cp_status`
- `@Command("/双修")` / `@Command("双修")` → 转发到 `MarriageTool.dual_cultivate`

### FortuneBot
- `@Command("/运势")` / `@Command("运势")` → 转发到 `FortuneTool.fortune`

---

## 十三、Bot 类通用模式

每个 `bot/` 下的类遵循统一模式：

```java
@Component
public class CultivationBot {

    private final CultivationTool cultivationTool;
    // ... 注入其他 Tool

    public CultivationBot(CultivationTool cultivationTool, ...) {
        this.cultivationTool = cultivationTool;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command("/修仙")  // 含 "/" 前缀
    @Command("修仙")   // 不含 "/" 前缀（兼容两种写法）
    public String startCultivation(ChannelEvent event) {
        long userId = extractUserId(event);
        long groupId = extractGroupId(event);
        String userName = extractUserName(event);
        return cultivationTool.start_cultivation(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }
    // ...
}
```

`extractUserId/extractGroupId/extractUserName` 工具方法处理 `MessageEvent` 和 `ChannelMessageEvent` 两种类型。

---

## 十四、HelpBot 更新

在 `HelpBot.java` 中新增修仙入口提示：
```java
sb.append("• 修仙系统 — \"修仙菜单\"查看全部\n");
sb.append("• 每日签到 — \"签到\" \"签到状态\"\n");
sb.append("• 群友结婚 — \"求婚\" \"我的CP\"\n");
```

---

## 十五、实施步骤

按优先级分为 5 个阶段：

### 阶段1：模块骨架
1. 创建 `dingdong-cultivation` 目录和 `pom.xml`
2. 在父 `pom.xml` 注册模块
3. 在 `dingdong-boot-starter/pom.xml` 添加依赖
4. 验证 `mvn compile -pl dingdong-cultivation` 通过

### 阶段2：迁入现有工具
5. 从 `dingdong-agent` 迁入 `CheckInTool` → `dingdong-cultivation`
6. 从 `dingdong-agent` 迁入 `FortuneTool` → `dingdong-cultivation`
7. 从 `dingdong-agent` 迁入 `MarriageTool` → `dingdong-cultivation`
8. 在 `dingdong-agent` 中删除以上三个文件
9. 创建 `CheckInBot`, `MarriageBot`, `FortuneBot`
10. 验证编译 + 功能正常

### 阶段3：修仙核心
11. 创建 `CultivationTool.java`（修炼/突破/渡劫/状态/排行/意外事件/切磋/双修）
12. 创建 `SectTool.java`（宗门系统）
13. 创建 `PillShopTool.java`（丹药商店）
14. 创建 `CultivationBot.java`（命令转发）
15. 验证编译 + 功能正常

### 阶段4：修仙联动
16. 修改 `CheckInTool` 增加修仙联动（签到额外修为）
17. 修改 `MarriageTool` 增加双修功能
18. 修改 `HelpBot` 增加修仙菜单入口

### 阶段5：集成验证
19. 在 OneBot11 通道测试所有命令
20. 在 QQ Official 通道测试所有命令
21. 全量编译 `mvn install -DskipTests` 通过

---

## 十六、关键文件清单

### 新建文件（11个）
| 文件 | 路径 |
|------|------|
| pom.xml | `dingdong-cultivation/pom.xml` |
| CultivationTool.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/` |
| SectTool.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/` |
| PillShopTool.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/` |
| CheckInTool.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/` (迁入) |
| FortuneTool.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/` (迁入) |
| MarriageTool.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/tool/` (迁入) |
| CultivationBot.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot/` |
| CheckInBot.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot/` |
| MarriageBot.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot/` |
| FortuneBot.java | `dingdong-cultivation/src/main/java/com/dingdong/cultivation/bot/` |

### 修改文件（5个）
| 文件 | 变更 |
|------|------|
| `pom.xml` (父) | 添加 `<module>dingdong-cultivation</module>` |
| `dingdong-boot-starter/pom.xml` | 添加 dingdong-cultivation 依赖 |
| `dingdong-admin/src/.../HelpBot.java` | 增加修仙入口 |
| `dingdong-agent/.../CheckInTool.java` | 删除 |
| `dingdong-agent/.../FortuneTool.java` | 删除 |
| `dingdong-agent/.../MarriageTool.java` | 删除 |

### 数据库迁移（MigrationManager）
在 `NapCatAutoConfiguration` 中注册新的 migration 编号（需递增）：
```java
mm.register(9, "create cultivation tables", 
    CultivationTool.cultivationUsersDdl() + 
    SectTool.sectsDdl() + SectTool.sectMembersDdl() +
    CultivationTool.sparChallengesDdl());
```

注意：`daily_checkin` 和 `marriages` 表在迁入前已由 `dingdong-agent` 创建，迁入后 `ensureTable()` 中的 `CREATE TABLE IF NOT EXISTS` 不会冲突。

---

## 十七、待定/预留

- 炼丹系统（材料+配方）：`PillRecipe` 类预留，暂不启用
- 宗门领地（洞府升级、仓库）：SectTool 预留接口，暂不实现
- 宗门战争（宗门间切磋）：预留 `sect_war` tool 名称，暂不实现
- 法宝系统：暂不规划
