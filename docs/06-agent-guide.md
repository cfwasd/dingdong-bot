# Agent 使用指南

本文档描述框架的 AI Agent 功能，包括 ReAct 循环、Tool 注册、LLM 对接、多模态和会话管理。

---

## 一、Agent 概述

框架内置轻量级 ReAct Agent，核心能力：

- **多轮思考**：收到用户消息后，Agent 可以多次调用 LLM，每轮决定直接回复或调用工具
- **工具调用**：自动将 `@Tool` 标记的方法转换为 LLM 的 Function Calling Schema
- **会话隔离**：按 `userId + groupId` 复合键隔离会话上下文，支持过期清理
- **多 LLM 后端**：OpenAI（含多模态/vision）、Claude、Ollama、自定义 OpenAI 端点
- **内置工具**：联网搜索 (DuckDuckGo)、网页抓取、日期时间查询
- **多模态理解**：当用户使用 `toAgentPrompt()` 时，图片 URL 会被自动提取为 `image_url` 发送给支持 vision 的模型
- **推理内容支持**：OpenAI Provider 支持解析 `reasoning_content`（如 DeepSeek R1 等推理模型）

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
        // toAgentPrompt() 保留图片、@等富文本，适合多模态模型
        String prompt = event.getMessage().toAgentPrompt();
        agent.chat(event.getUserId(), event.getGroupId(), prompt)
            .thenAccept(reply -> event.reply(reply));
    }
}
```

**方式二：私聊自动触发**

```java
@OnPrivateMessage
public void onPrivate(PrivateMessageEvent event) {
    agent.chat(event.getUserId(), SessionKey.PRIVATE, event.getPlainText())
        .thenAccept(event::reply);
}
```

**方式三：命令触发**

```java
@OnGroupMessage
@Command("/ai {prompt}")
public void aiCommand(GroupMessageEvent event, @Param("prompt") String prompt) {
    agent.chat(event.getUserId(), event.getGroupId(), prompt)
        .thenAccept(event::reply);
}
```

**方式四：全局兜底**

```yaml
napcat:
  bot:
    at-me-trigger: true  # 被 @ 时自动走 Agent，无需写 Handler
```

开启后，所有被 @ 的群消息或包含唤醒词的消息会自动进入 Agent 流程，无需额外代码。

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

### 3.3 工具参数注解

```java
public @interface ToolParam {
    String description();              // 参数描述，LLM 据此决定如何传值
    boolean required() default false;  // 是否必填
    String[] enums() default {};       // 枚举值
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

public void handle(GroupMessageEvent event) {
    // 单次调用，使用默认配置
    agent.chat(event.getUserId(), event.getGroupId(), "你好")
        .thenAccept(event::reply);

    // 自定义参数
    AgentConfig config = AgentConfig.builder()
        .maxRounds(3)
        .systemPrompt("你是专业客服")
        .timeoutPerRound(10000)
        .showToolProcess(true)
        .ackCallback(() -> event.reply(MessageChain.ofFace(277)))  // 立即回复表情表示已收到
        .build();

    agent.chat(event.getUserId(), event.getGroupId(), "问题", config,
        toolMsg -> event.reply(toolMsg))  // 工具执行过程回调
        .thenAccept(event::reply);
}
```

**AgentConfig 字段：**

```java
@Data
@Builder
public class AgentConfig {
    @Builder.Default
    private int maxRounds = 5;               // 最大思考轮数
    private String systemPrompt;              // 系统提示词
    @Builder.Default
    private long timeoutPerRound = 30000;     // 每轮超时（ms）
    @Builder.Default
    private boolean showToolProcess = false;  // 是否回传工具执行过程
    private Runnable ackCallback;             // 消息确认回调（如回复表情）
}
```

---

## 五、多模态支持

当消息包含图片时，使用 `MessageChain.toAgentPrompt()` 会生成 `[图片:url]` 格式的标记：

```java
String prompt = event.getMessage().toAgentPrompt();
// "你好 [图片:https://example.com/pic.jpg] 这是什么"
```

`NapCatAgent` 会自动从输入中提取 `[图片:url]` 格式的 HTTP/HTTPS 图片地址，将其作为 `image_url` 内容发送给 LLM：

```java
// OpenAI 请求中的 messages 格式
{
  "role": "user",
  "content": [
    { "type": "text", "text": "你好 [图片] 这是什么" },
    { "type": "image_url", "image_url": { "url": "https://example.com/pic.jpg" } }
  ]
}
```

**要求：**
- 仅 OpenAI Provider（及兼容的自定义端点）支持多模态
- 图片地址必须是 `http://` 或 `https://`
- 模型需支持 vision（如 `gpt-4o`、`gpt-4o-mini`、`qwen-vl` 等）

---

## 六、会话管理

### 6.1 默认行为

- 按 `SessionKey(userId, groupId)` 隔离会话。私聊时 `groupId = 0`
- 同一用户在不同群聊、或私聊与群聊之间的会话完全隔离
- 默认 TTL 为 3600 秒，过期自动清理
- 默认最大历史消息 50 条，超出时自动截断（保留 system + 最近 N 条）

### 6.2 手动管理会话

```java
@Autowired
private SessionManager sessionManager;

// 清除某用户在当前群的会话
sessionManager.clear(new SessionKey(userId, groupId));

// 清除某用户的私聊会话
sessionManager.clear(SessionKey.ofPrivate(userId));

// 获取会话上下文（用于调试）
Session session = sessionManager.get(new SessionKey(userId, groupId));
List<ChatMessage> history = session.getHistory();
```

**SessionKey 定义：**

```java
public record SessionKey(long userId, long groupId) {
    public static final long PRIVATE = 0L;
    public boolean isPrivate();
    public boolean isGroup();
    public static SessionKey ofPrivate(long userId);   // groupId = 0
    public static SessionKey ofGroup(long userId, long groupId);
}
```

---

## 七、LLM Provider 详解

### 7.1 OpenAI（含兼容端点）

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

**OpenAI Provider 额外特性：**
- 支持 `reasoning_content` 解析（DeepSeek R1 等推理模型）
- 支持多模态 `image_url`（vision 模型）

### 7.2 Anthropic Claude

```yaml
napcat:
  llm:
    provider: anthropic
    anthropic:
      base-url: https://api.anthropic.com
      api-key: sk-ant-xxx
      model: claude-sonnet-4-6
```

### 7.3 Ollama

```yaml
napcat:
  llm:
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: llama3:8b
```

Ollama 无需 API Key，适合本地开发测试。

### 7.4 自定义 Provider

实现 `LlmProvider` 接口：

```java
public interface LlmProvider {
    String getProviderName();
    CompletableFuture<LlmResponse> chat(Session session, String input, List<ToolSchema> tools);
}
```

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

---

## 八、内置工具

框架自带三个内置工具，可通过配置开启/关闭：

```yaml
napcat:
  agent:
    builtin:
      web-search:
        enabled: true
      fetch-url:
        enabled: true
      date-time:
        enabled: true
```

| 工具名 | 功能 | 说明 |
|--------|------|------|
| `web_search` | 联网搜索 | 基于 DuckDuckGo，免费 |
| `fetch_url` | 抓取网页 | HTTP 获取指定 URL 的文本内容 |
| `get_current_time` | 日期时间 | 获取当前时间、计算日期间隔等 |

---

## 九、错误处理

Agent 内部对常见错误做了处理：

- **API 请求错误（4xx）**：不返回消息给用户，避免刷屏
- **其他异常**：返回 "处理出错了，请稍后再试。" 并记录日志
- **超过最大轮数**：返回 "思考次数过多，请简化问题。"

开启 DEBUG 日志查看 Agent 思考过程：

```yaml
logging:
  level:
    com.napcat.agent: DEBUG
```
