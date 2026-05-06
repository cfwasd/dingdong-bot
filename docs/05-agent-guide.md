# Agent 使用指南

本文档描述框架的 AI Agent 功能，包括 ReAct 循环、Tool 注册、LLM 对接和会话管理。

---

## 一、Agent 概述

框架内置轻量级 ReAct Agent，核心能力：

- **多轮思考**：收到用户消息后，Agent 可以多次调用 LLM，每轮决定直接回复或调用工具
- **工具调用**：自动将 `@Tool` 标记的方法转换为 LLM 的 Function Calling Schema
- **会话隔离**：按用户 QQ 号隔离会话上下文，支持过期清理
- **多 LLM 后端**：OpenAI、Claude、Ollama、自定义 OpenAI 端点

默认最大思考轮数为 5 轮，超过则返回提示信息。

---

## 二、启用 Agent

### 2.1 基础配置

```yaml
napcat:
  agent:
    enabled: true
    max-react-rounds: 5
    system-prompt: "你是一个有用的 QQ 机器人助手，回答简洁。"

  llm:
    provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
```

### 2.2 触发方式

**方式一：被 @ 时自动触发**

```java
@Component
public class AgentBot {

    @Autowired
    private NapCatAgent agent;

    @OnGroupMessage
    @MentionFilter
    public void onAt(GroupMessageEvent event) {
        String plainText = event.getMessage().toPlainText();
        agent.chat(event.getUserId(), plainText)
            .thenAccept(reply -> event.reply(reply));
    }
}
```

**方式二：私聊自动触发**

```java
@OnPrivateMessage
public void onPrivate(PrivateMessageEvent event) {
    agent.chat(event.getUserId(), event.getPlainText())
        .thenAccept(event::reply);
}
```

**方式三：命令触发**

```java
@OnGroupMessage
@Command("/ai {prompt}")
public void aiCommand(GroupMessageEvent event, @Param("prompt") String prompt) {
    agent.chat(event.getUserId(), prompt)
        .thenAccept(event::reply);
}
```

**方式四：全局兜底**

```yaml
napcat:
  bot:
    at-me-trigger: true  # 被 @ 时自动走 Agent，无需写 Handler
```

开启后，所有被 @ 的群消息会自动进入 Agent 流程，无需额外代码。

---

## 三、注册工具（Tool）

工具是让 Agent 具备外部能力的关键。框架自动扫描 `@Tool` 注解的方法。

### 3.1 基础工具

```java
@Component
public class CalculatorTool {

    @Tool(
        name = "calculate",
        description = "执行数学计算，如加减乘除、平方根等"
    )
    public String calculate(
        @ToolParam(description = "数学表达式，如 15 * 3 + 2", required = true) String expression
    ) {
        try {
            // 简单实现，实际可用更安全的表达式引擎
            return String.valueOf(new ScriptEngineManager()
                .getEngineByName("JavaScript")
                .eval(expression));
        } catch (Exception e) {
            return "计算错误：" + e.getMessage();
        }
    }
}
```

### 3.2 带外部依赖的工具

```java
@Component
public class WeatherTool {

    @Autowired
    private WeatherService weatherService;

    @Tool(
        name = "get_weather",
        description = "查询指定城市的当前天气"
    )
    public String getWeather(
        @ToolParam(description = "城市名称，如北京、上海", required = true) String city
    ) {
        return weatherService.query(city);
    }
}
```

### 3.3 返回结构化数据

```java
@Component
public class SearchTool {

    @Tool(
        name = "search_user",
        description = "在群成员中搜索用户"
    )
    @ToolResponse(description = "返回用户列表，每个用户包含 qq、nickname、role")
    public List<UserInfo> searchUser(
        @ToolParam(description = "搜索关键词，支持昵称或 QQ 号模糊匹配") String keyword,
        @Event GroupMessageEvent event   // 可注入当前事件获取群号
    ) {
        long groupId = event.getGroupId();
        // ... 搜索逻辑
        return userList;
    }
}
```

### 3.4 工具参数注解

```java
public @interface ToolParam {
    String description();      // 参数描述，LLM 据此决定如何传值
    boolean required() default false;  // 是否必填
    String[] enums() default {};       // 枚举值（如 ["celsius", "fahrenheit"]）
    String type() default "string";    // json schema 类型：string/number/integer/boolean/array/object
}
```

---

## 四、ReAct 循环详解

### 4.1 执行流程

```
用户输入："北京天气怎么样，然后帮我算一下 25 乘 3"
  │
  ▼
┌─────────────────────────────────────────┐
│ Round 1                                 │
│ LLM 分析：需要查询天气 + 计算            │
│ 决定：调用 get_weather(city="北京")      │
└─────────────────────────────────────────┘
  │
  ▼ 执行工具
  返回："北京 晴 25°C"
  │
  ▼
┌─────────────────────────────────────────┐
│ Round 2                                 │
│ LLM 分析：天气已获取，还需要计算          │
│ 决定：调用 calculate(expression="25*3")  │
└─────────────────────────────────────────┘
  │
  ▼ 执行工具
  返回："75"
  │
  ▼
┌─────────────────────────────────────────┐
│ Round 3                                 │
│ LLM 分析：所有信息已收集完毕              │
│ 决定：直接回复用户                        │
│ 回复："北京今天晴天，25°C。25乘3等于75。"│
└─────────────────────────────────────────┘
```

### 4.2 自定义系统提示词

```yaml
napcat:
  agent:
    system-prompt: |
      你是一个群聊助手，性格活泼。
      回答要简短，尽量不超过 100 字。
      如果用户问天气，要同时提醒穿衣建议。
```

### 4.3 运行时控制

```java
@Autowired
private NapCatAgent agent;

public void handle(Event event) {
    // 单次调用，使用默认配置
    agent.chat(userId, "你好").thenAccept(event::reply);

    // 自定义参数
    AgentConfig config = AgentConfig.builder()
        .maxRounds(3)
        .systemPrompt("你是专业客服")
        .timeout(10000)
        .build();

    agent.chat(userId, "问题", config).thenAccept(event::reply);
}
```

---

## 五、会话管理

### 5.1 默认行为

- 按 `userId` 隔离会话
- 会话上下文包含：历史消息、工具调用记录、中间结果
- 默认 TTL 为 3600 秒，过期自动清理

### 5.2 手动管理会话

```java
@Autowired
private SessionManager sessionManager;

// 清除某用户会话
sessionManager.clear(userId);

// 获取会话上下文（用于调试或持久化）
Session session = sessionManager.get(userId);
List<Message> history = session.getHistory();

// 设置全局会话 TTL
napcat:
  agent:
    session-ttl: 7200  # 2 小时
```

### 5.3 会话事件监听

```java
@Component
public class SessionListener implements SessionEventListener {

    @Override
    public void onSessionCreated(long userId) {
        System.out.println("新会话：" + userId);
    }

    @Override
    public void onSessionExpired(long userId) {
        System.out.println("会话过期：" + userId);
    }

    @Override
    public void onSessionCleared(long userId) {
        System.out.println("会话清除：" + userId);
    }
}
```

---

## 六、LLM Provider 详解

### 6.1 OpenAI（含兼容端点）

```yaml
napcat:
  llm:
    provider: openai
    openai:
      base-url: https://api.openai.com/v1
      api-key: sk-xxx
      model: gpt-4o-mini
```

兼容端点（DeepSeek、通义千问、本地 vLLM 等）：

```yaml
napcat:
  llm:
    provider: custom
    custom:
      base-url: https://api.deepseek.com/v1
      api-key: sk-xxx
      model: deepseek-chat
```

### 6.2 Anthropic Claude

```yaml
napcat:
  llm:
    provider: anthropic
    anthropic:
      base-url: https://api.anthropic.com
      api-key: sk-ant-xxx
      model: claude-sonnet-4-6
```

### 6.3 Ollama

```yaml
napcat:
  llm:
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: llama3:8b
```

Ollama 无需 API Key，适合本地开发测试。

### 6.4 自定义 Provider

实现 `LlmProvider` 接口：

```java
@Component
public class MyLlmProvider implements LlmProvider {

    @Override
    public String getProviderName() {
        return "my-llm";
    }

    @Override
    public CompletableFuture<LlmResponse> chat(Session session, String input, List<ToolSchema> tools) {
        // 实现调用逻辑
        return CompletableFuture.completedFuture(new LlmResponse("回复内容"));
    }
}
```

然后在配置中指定：

```yaml
napcat:
  llm:
    provider: my-llm
```

---

## 七、错误处理

### 7.1 常见异常

| 异常 | 原因 | 处理 |
|------|------|------|
| `LlmTimeoutException` | LLM 调用超时 | 提示用户网络繁忙 |
| `LlmRateLimitException` | 触发限流 | 提示用户稍后再试 |
| `ToolExecutionException` | 工具执行出错 | Agent 会收到错误信息，尝试修复或告知用户 |
| `MaxRoundsExceededException` | 超过最大思考轮数 | 返回提示：思考次数过多 |

### 7.2 全局 Agent 异常处理

```java
@Component
public class AgentExceptionHandler {

    @EventListener
    public void onAgentError(AgentErrorEvent event) {
        Throwable ex = event.getCause();
        long userId = event.getUserId();

        if (ex instanceof LlmTimeoutException) {
            api.sendPrivateMessage(userId, "LLM 响应超时，请稍后再试");
        }
    }
}
```

---

## 八、调试与观测

### 8.1 日志

开启 debug 日志查看 Agent 思考过程：

```yaml
logging:
  level:
    com.napcat.agent: DEBUG
```

输出示例：

```
[Agent] User(123456): 北京天气怎么样
[Agent] Round 1/5 -> Tool: get_weather(city=北京)
[Agent] Tool result: 北京 晴 25°C
[Agent] Round 2/5 -> Reply: 北京今天晴天，25°C。
[Agent] Completed in 2 rounds, cost 1.2s
```

### 8.2 指标

框架暴露以下 Micrometer 指标（Spring Boot Actuator）：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `napcat.agent.chat.total` | Counter | Agent 调用总次数 |
| `napcat.agent.chat.duration` | Timer | Agent 调用耗时 |
| `napcat.agent.rounds` | DistributionSummary | 每轮对话的思考轮数分布 |
| `napcat.agent.tool.calls` | Counter | 工具调用总次数 |
| `napcat.agent.tool.errors` | Counter | 工具调用错误次数 |
