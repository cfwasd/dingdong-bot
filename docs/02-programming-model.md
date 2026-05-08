# 编程模型

框架同时支持**注解驱动**和**接口驱动**两种编程模型，两者最终汇入同一路由表，行为完全一致。

---

## 一、注解驱动

所有注解位于包 `com.napcat.core.annotation` 下。

### 1.1 事件监听注解

在方法上标注，表示监听对应类型的事件。

| 注解 | 适用参数类型 | 说明 |
|------|------------|------|
| `@OnGroupMessage` | `GroupMessageEvent` | 群聊消息事件 |
| `@OnPrivateMessage` | `PrivateMessageEvent` | 私聊消息事件 |
| `@OnNotice` | `NoticeEvent` 子类 | 通知事件（群成员变动等） |
| `@OnRequest` | `RequestEvent` 子类 | 请求事件（加群/加好友） |
| `@OnMetaEvent` | `MetaEvent` | 元事件（心跳、生命周期） |

**源码定义：**

```java
public @interface OnGroupMessage {
    long[] botId() default {};
}

public @interface OnPrivateMessage {
    long[] botId() default {};
}

public @interface OnNotice {
    Class<? extends NoticeEvent> value() default NoticeEvent.class;
}

public @interface OnRequest {
    Class<? extends RequestEvent> value() default RequestEvent.class;
}

public @interface OnMetaEvent {
}
```

**示例：**

```java
@Component
public class HelloBot {

    @OnGroupMessage
    public void onGroup(GroupMessageEvent event) {
        if (event.getRawMessage().contains("hello")) {
            event.reply("Hello!");
        }
    }

    @OnPrivateMessage
    public void onPrivate(PrivateMessageEvent event) {
        event.reply("私聊收到：" + event.getPlainText());
    }
}
```

**多事件类型叠加：**

同一个方法可以加多个事件注解，满足**任一**即触发（OR 关系）。

```java
@OnGroupMessage
@OnPrivateMessage
public void onAny(MessageEvent event) {
    // 群聊或私聊都会进入这里
}
```

---

### 1.2 命令注解

`@Command` 用于匹配固定格式的指令，**必须与事件注解叠加使用**。

```java
public @interface Command {
    String value();
}
```

**规则：**

- 叠加在事件注解上时，表示**同时满足**（AND 关系）
- 消息必须匹配命令模板，参数用 `{}` 包裹
- 不匹配的参数化命令会继续向下路由

**示例：**

```java
@Component
public class CommandBot {

    // 匹配："/天气 北京"
    // 不匹配："/天气"（缺少 city）、"/帮助"
    @OnGroupMessage
    @Command("/天气 {city}")
    public void weather(GroupMessageEvent event, @Param("city") String city) {
        event.reply("查询 " + city + " 的天气");
    }

    // 匹配："/帮助"
    @OnGroupMessage
    @Command("/帮助")
    public void help(GroupMessageEvent event) {
        event.reply("可用指令：/天气 /帮助");
    }
}
```

**命令参数提取：**

```java
@OnGroupMessage
@Command("/禁言 {user} {minutes}")
public void ban(GroupMessageEvent event,
                @Param("user") long userId,
                @Param("minutes") int minutes) {
    // userId 会被自动从 @user 的 QQ 号提取
    // minutes 会被自动转为 int
}
```

`CommandArgs` 实际支持的方法：

```java
public class CommandArgs {
    public String get(String key);
    public int getInt(String key);
    public long getLong(String key);
    public boolean getBoolean(String key);  // "true"/"1"/"yes" 为 true
    public boolean contains(String key);
}
```

**特殊参数类型：**

| 参数类型 | 提取方式 |
|---------|---------|
| `String` | 原文提取 |
| `int` / `long` / `double` | 自动转换 |
| `boolean` | "true"/"1"/"yes" 为 true |
| `MessageChain` | 提取命令后的完整消息链 |
| `GroupMessageEvent` / `MessageEvent` | 注入事件对象本身 |

---

### 1.3 过滤注解

与事件注解叠加，增加额外过滤条件。

| 注解 | 说明 | 适用场景 |
|------|------|---------|
| `@MentionFilter` | 要求消息中 @ 了当前机器人 | Agent 自动回复 |
| `@RoleFilter(Role.ADMIN)` | 要求发送者是指定角色 | 管理员命令 |
| `@WakeFilter` | 要求消息包含配置的唤醒词 | 关键词唤醒 |

**源码定义：**

```java
public @interface MentionFilter {
}

public @interface RoleFilter {
    Role value();
    enum Role { OWNER, ADMIN, MEMBER, SUPERUSER }
}

/**
 * 关键词唤醒过滤器。
 * 唤醒词列表在 napcat.bot.wake-words 中配置，默认包含 ["机器人", "bot"]。
 * 与 @MentionFilter 互斥时取 OR 语义（满足任一即触发）。
 */
public @interface WakeFilter {
}
```

**示例：**

```java
// 只有被 @ 时才触发
@OnGroupMessage
@MentionFilter
public void onAtMe(GroupMessageEvent event) {
    event.reply("你叫我？");
}

// 只有管理员才能执行
@OnGroupMessage
@Command("/清理")
@RoleFilter(RoleFilter.Role.ADMIN)
public void clear(GroupMessageEvent event) {
    // ...
}

// 消息包含"机器人"或"bot"时触发
@OnGroupMessage
@WakeFilter
public void onWake(GroupMessageEvent event) {
    event.reply("我在！");
}
```

---

### 1.4 参数注入注解

用于命令方法的参数上。

```java
public @interface Param {
    String value();
}
```

| 注解 | 说明 |
|------|------|
| `@Param("name")` | 从命令模板中提取对应参数 |

---

### 1.5 Agent 相关注解

| 注解 | 说明 |
|------|------|
| `@Tool` | 标记一个方法为 Agent 可调用的工具 |
| `@ToolParam` | 标记工具参数的描述和约束 |

**源码定义：**

```java
public @interface Tool {
    String name();
    String description();
}

public @interface ToolParam {
    String description();
    boolean required() default false;
    String[] enums() default {};
    String type() default "string";
}
```

**@Tool 示例：**

```java
@Component
public class Tools {

    @Tool(name = "get_weather", description = "查询指定城市的天气")
    public String getWeather(
        @ToolParam(description = "城市名称，如北京", required = true) String city
    ) {
        return "北京 晴 25°C";
    }

    @Tool(name = "calculate", description = "数学计算")
    public double calculate(
        @ToolParam(description = "表达式，如 1+2") String expression
    ) {
        // ...
        return 3.0;
    }
}
```

---

### 1.6 组合注解（Meta-Annotation）

框架支持自定义组合注解。定义时加 `@AliasFor` 即可。

**自定义示例：**

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@OnGroupMessage
@MentionFilter
public @interface OnGroupAtMe {
    @AliasFor(annotation = OnGroupMessage.class, attribute = "botId")
    long[] botId() default {};
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Command
@RoleFilter(RoleFilter.Role.ADMIN)
public @interface AdminCommand {
    @AliasFor(annotation = Command.class, attribute = "value")
    String value();
}
```

**使用：**

```java
@OnGroupAtMe
public void handleAt(GroupMessageEvent event) { }

@AdminCommand("/踢出 {user}")
public void kick(GroupMessageEvent event, @Param("user") long userId) { }
```

---

## 二、接口驱动

所有接口位于包 `com.napcat.core.handler` 下。

### 2.1 通用事件处理器

```java
public interface EventHandler<E extends OB11Event> {
    Class<E> getEventType();
    void handle(E event);

    interface GroupMessageHandler extends EventHandler<GroupMessageEvent> {
        @Override
        default Class<GroupMessageEvent> getEventType() {
            return GroupMessageEvent.class;
        }
    }

    interface PrivateMessageHandler extends EventHandler<PrivateMessageEvent> {
        @Override
        default Class<PrivateMessageEvent> getEventType() {
            return PrivateMessageEvent.class;
        }
    }
}
```

**示例：**

```java
@Component
public class WelcomeHandler implements EventHandler.GroupMessageHandler {
    @Override
    public void handle(GroupMessageEvent event) {
        if (event.getMessage().contains("新人")) {
            event.reply("欢迎新人！");
        }
    }
}
```

---

### 2.2 命令处理器

```java
public interface CommandHandler {
    String getCommand();
    void handle(MessageEvent event, CommandArgs args);

    interface FilterableCommandHandler extends CommandHandler {
        default boolean filter(MessageEvent event) {
            return true;
        }
    }
}
```

**示例：**

```java
@Component
public class WeatherHandler implements CommandHandler {
    @Override
    public String getCommand() {
        return "/天气 {city}";
    }

    @Override
    public void handle(MessageEvent event, CommandArgs args) {
        String city = args.get("city");
        event.reply(city + " 天气晴朗");
    }
}
```

**带过滤的命令处理器：**

```java
@Component
public class AdminClearHandler implements CommandHandler.FilterableCommandHandler {
    @Override
    public String getCommand() {
        return "/清理";
    }

    @Override
    public boolean filter(MessageEvent event) {
        return event.getSender().isAdmin();
    }

    @Override
    public void handle(MessageEvent event, CommandArgs args) {
        // ...
    }
}
```

---

### 2.3 手动注册器

如果不想用 Spring 的 `@Component` 自动扫描，也可以通过接口手动注册。

```java
public interface BotInitializer {
    void initialize(BotDispatcher dispatcher);
}

public interface BotDispatcher {
    void onGroupMessage(Consumer<GroupMessageEvent> handler);
    void onPrivateMessage(Consumer<PrivateMessageEvent> handler);
    void onEvent(Class<? extends OB11Event> type, Consumer<OB11Event> handler);
    void registerCommand(String template, BiConsumer<MessageEvent, CommandArgs> handler);
    void registerCommand(String template, BiConsumer<MessageEvent, CommandArgs> handler,
                         Predicate<MessageEvent> filter);
}
```

**示例：**

```java
@Component
public class ManualBot implements BotInitializer {
    @Override
    public void initialize(BotDispatcher dispatcher) {
        dispatcher.onGroupMessage(event -> {
            if (event.getMessage().contains("测试")) {
                event.reply("收到测试");
            }
        });

        dispatcher.registerCommand("/状态", (event, args) -> {
            event.reply("运行正常");
        });

        dispatcher.registerCommand("/管理", (event, args) -> {
            event.reply("管理员命令");
        }, event -> event.getSender().isAdmin());
    }
}
```

---

## 三、返回值处理

注解驱动的方法可以有返回值，框架会自动处理：

| 返回值类型 | 行为 |
|-----------|------|
| `void` | 无操作 |
| `String` | 自动回复文本 |
| `MessageChain` | 自动回复消息链 |
| `CompletableFuture<String>` | 异步回复 |
| `CompletableFuture<MessageChain>` | 异步回复消息链 |

**示例：**

```java
@OnGroupMessage
@Command("/时间")
public String time() {
    return new Date().toString();
}

@OnGroupMessage
@Command("/图片")
public MessageChain image() {
    return MessageChain.ofText("给你一张图：").image("https://example.com/a.jpg");
}
```

---

## 四、异常处理

框架提供全局异常处理器。方法内抛出 `StopRoutingException` 可阻止后续处理器执行：

```java
public class StopRoutingException extends RuntimeException {
    public StopRoutingException() {
        super("Stop routing");
    }
}
```

**示例：**

```java
@OnGroupMessage
public void filterSpam(GroupMessageEvent event) {
    if (isSpam(event)) {
        throw new StopRoutingException(); // 不执行后续 handler
    }
}
```

---

## 五、执行顺序与优先级

### 5.1 路由优先级

框架按**精确度从高到低**匹配，第一个命中即停止：

1. `@Command`（参数化命令最精确）
2. `@MentionFilter + @Command`
3. `@MentionFilter` / `@WakeFilter`
4. `@OnGroupMessage` / `@OnPrivateMessage`（兜底）

### 5.2 同一优先级的排序

通过 Spring 的 `@Order` 控制：

```java
@Component
@Order(1)  // 数值越小优先级越高
public class HighPriorityHandler { }
```

### 5.3 阻止后续路由

方法内抛出 `StopRoutingException` 可阻止后续处理器执行（见上文）。
