# 内部架构

本文档面向框架开发者，描述各模块职责、核心流程和扩展点。

---

## 一、模块职责

### napcat-core

- **协议层**：OneBot11 事件/消息数据模型、JSON 反序列化（Jackson）
- **通信层**：`BotAdapter` 抽象及四种实现（WS/HTTP）
- **路由层**：`HandlerRegistry` 注解扫描、接口收集、路由表构建、事件分发
- **API 层**：`NapCatApi` 封装所有 OneBot11 + NapCat 扩展 API，基于 echo 机制的异步请求-响应匹配

### napcat-agent

- **Agent 引擎**：`NapCatAgent` 驱动 ReAct 循环
- **会话管理**：`SessionManager` 按 `SessionKey(userId, groupId)` 隔离上下文
- **Tool 注册**：`ToolRegistry` 扫描 `@Tool` 注解，生成 LLM JSON Schema，含 JSON 容错修复与模糊匹配
- **抽象接口**：`LlmProvider`、`Session`、`ToolSchema` 等

### napcat-llm-providers

各 LLM 厂商的具体实现，每个子模块依赖 `napcat-agent` 并实现 `LlmProvider`。

- **napcat-llm-openai**：OpenAI 协议兼容实现（含 reasoning_content、tool_calls）
- **napcat-llm-anthropic**：Anthropic Claude Messages API 实现
- **napcat-llm-ollama**：Ollama HTTP API 实现（`/api/chat`，stream=false）

### napcat-spring-boot-starter

- **自动配置**：`NapCatAutoConfiguration` 根据 `application.yml` 装配 Bean
- **属性绑定**：`NapCatProperties` `@ConfigurationProperties(prefix = "napcat")`
- **Bean 扫描**：`NapCatBeanPostProcessor` 自动注册所有注解方法、`EventHandler`、`CommandHandler`、`BotInitializer`
- **生命周期**：`NapCatLifecycle` Spring 启动时启动 Adapter，关闭时优雅停止，注册 `at-me-trigger` 兜底 Agent

### napcat-admin

示例应用，演示注解方式、接口方式、Agent 模式的完整用法。

---

## 二、核心流程

### 2.1 启动流程

```
Spring ApplicationContext 初始化
  │
  ├─ NapCatAutoConfiguration
  │     ├─ 解析 NapCatProperties
  │     ├─ 创建 BotAdapter（根据 adapter.type）
  │     ├─ 创建 NapCatApi（使用第一个 Adapter）
  │     ├─ 创建 MessageRouter
  │     ├─ 创建 EventDispatcher（含线程池）
  │     ├─ 创建 HandlerRegistry
  │     ├─ 如 agent.enabled=true：
  │     │     ├─ 扫描所有 Bean 的 @Tool 方法 → ToolRegistry
  │     │     ├─ 创建 SessionManager
  │     │     └─ 创建 NapCatAgent
  │     └─ 注册 NapCatBeanPostProcessor + NapCatLifecycle
  │
  ├─ NapCatBeanPostProcessor
  │     ├─ 扫描所有 @Component Bean 的方法注解
  │     ├─ 注册 EventHandler / CommandHandler / BotInitializer 接口实现
  │     └─ 生成 HandlerMethod 注册到 HandlerRegistry
  │
  └─ NapCatLifecycle.start()
        ├─ 绑定 MessageRouter → EventDispatcher 管道
        ├─ 如 at-me-trigger=true：注册兜底 Agent Handler
        └─ 启动所有 BotAdapter
```

### 2.2 事件处理流程

```
NapCat 上报事件（JSON）
  │
  ▼
BotAdapter 收到原始 JSON
  │
  ▼
MessageRouter 区分事件（post_type）与 API 响应（echo）
  │
  ▼ 事件分支
EventDispatcher.dispatch(event)
  │
  ├─ 注入 api 到 MessageEvent（跨线程安全）
  ├─ 设置 EventContext（ThreadLocal）
  ├─ 忽略自身消息（如配置 ignore-self-message）
  ├─ 查找 HandlerRegistry 中匹配的 Handlers
  │     按优先级排序，精确匹配优先
  │
  ├─ 顺序执行 Handlers（线程池异步或同步）
  │     第一个命中即执行，除非抛出 StopRoutingException
  │
  ├─ 处理返回值（String/MessageChain → 自动回复）
  └─ 清理 EventContext
```

### 2.3 ReAct Agent 流程

```
NapCatAgent.chat(SessionKey, input, config)
  │
  ├─ 获取/创建 Session
  ├─ 新会话时注入 system prompt + 可用工具清单
  ├─ 添加 user message
  ├─ 执行 ackCallback（如有）
  │
  ▼
Round 1..N（N <= maxRounds）
  ├─ 调用 LlmProvider.chat(session, null, tools)
  ├─ 解析响应
  │     ├─ 纯文本 → 直接返回
  │     └─ ToolCall 列表 → 继续
  ├─ 遍历 ToolCall
  │     ├─ ToolRegistry.invoke(name, argumentsJson)
  │     ├─ 含 JSON 容错修复、模糊匹配
  │     └─ 收集结果
  ├─ 将 tool result 加入 Session History
  ├─ 如 showToolProcess=true，回调 toolProcessConsumer
  └─ 进入下一轮
  │
  ▼
超过 maxRounds → 返回 "思考次数过多，请简化问题。"
```

---

## 三、核心类图

### 3.1 事件体系

```
OB11Event (abstract)
  ├─ time: long
  ├─ selfId: long
  └─ postType: String

MessageEvent (abstract) extends OB11Event
  ├─ messageId: int
  ├─ userId: long
  ├─ message: MessageChain
  ├─ rawMessage: String
  ├─ sender: Sender
  ├─ reply(String): void
  └─ getPlainText(): String

GroupMessageEvent extends MessageEvent
  ├─ groupId: long
  ├─ subType: String
  ├─ messageSeq: long
  └─ anonymous: Anonymous

PrivateMessageEvent extends MessageEvent
  └─ subType: String
```

### 3.2 消息链

```
MessageChain implements List<MessageSegment>
  ├─ text(String): MessageChain
  ├─ at(long): MessageChain
  ├─ image(String): MessageChain
  ├─ toPlainText(): String
  ├─ containsImage(): boolean
  ├─ isAt(long): boolean
  ├─ isAtAll(): boolean
  ├─ getImages(): List<String>
  └─ getAts(): List<Long>

MessageSegment (abstract)
  ├─ type: String
  └─ data: Map<String, Object>

TextSegment / ImageSegment / AtSegment / FaceSegment
ReplySegment / RecordSegment / VideoSegment / FileSegment
MarkdownSegment / JsonSegment / XmlSegment / NodeSegment
ForwardSegment / UnknownSegment
```

### 3.3 路由体系

```
HandlerRegistry
  ├─ 注解方法注册表
  ├─ 事件处理器注册表
  ├─ 命令处理器注册表
  ├─ fallbackHandler: Consumer<OB11Event>
  ├─ registerBean(Object, Class): void
  ├─ registerEventHandler(EventHandler): void
  ├─ registerCommandHandler(CommandHandler): void
  └─ setFallbackHandler(Consumer): void

EventDispatcher
  ├─ handlerRegistry: HandlerRegistry
  ├─ botProperties: BotProperties
  ├─ api: NapCatApi
  ├─ sync: boolean
  ├─ executor: Executor
  └─ dispatch(OB11Event): void
```

### 3.4 Agent 体系

```
NapCatAgent
  ├─ llmProvider: LlmProvider
  ├─ toolRegistry: ToolRegistry
  ├─ sessionManager: SessionManager
  ├─ defaultSystemPrompt: String
  ├─ defaultMaxRounds: int
  ├─ chat(long userId, long groupId, String input): CompletableFuture<String>
  ├─ chat(long, long, String, AgentConfig, Consumer<String>): CompletableFuture<String>
  └─ reactLoop(Session, AgentConfig, int, Consumer): CompletableFuture<String>

LlmProvider (interface)
  ├─ getProviderName(): String
  └─ chat(Session, String, List<ToolSchema>): CompletableFuture<LlmResponse>

ToolRegistry
  ├─ tools: Map<String, ToolMethod>
  ├─ register(Object): void
  ├─ getSchemas(): List<ToolSchema>
  └─ invoke(String, String): Object  // 含 JSON 容错修复

SessionManager
  ├─ sessions: Map<SessionKey, Session>
  ├─ ttlSeconds: long
  ├─ maxHistoryMessages: int
  ├─ get(SessionKey): Session
  ├─ clear(SessionKey): void
  └─ clearExpired(): void

Session
  ├─ key: SessionKey
  ├─ history: List<ChatMessage>
  ├─ maxHistory: int
  ├─ addMessage(ChatMessage): void
  ├─ truncateHistory(): void
  └─ isExpired(long ttl): boolean
```

---

## 四、扩展点

### 4.1 自定义通信适配器

实现 `BotAdapter` 接口，注册为 Spring Bean：

```java
@Component
public class MyAdapter implements BotAdapter {
    @Override public String getId() { return "my-adapter"; }
    @Override public void start() { /* 建立连接 */ }
    @Override public void stop() { /* 关闭连接 */ }
    @Override public boolean isConnected() { return true; }
    @Override public void sendApiRequest(ApiRequest<?> req) { /* 发送 API */ }
    @Override public void setMessageHandler(Consumer<String> handler) { /* 设置回调 */ }
}
```

### 4.2 自定义 LLM Provider

```java
@Component
public class MyLlmProvider implements LlmProvider {
    @Override public String getProviderName() { return "my-llm"; }
    @Override public CompletableFuture<LlmResponse> chat(Session s, String input, List<ToolSchema> tools) {
        // 实现调用逻辑
    }
}
```

### 4.3 自定义消息段

```java
public class CustomSegment extends MessageSegment {
    public CustomSegment() {
        super("custom");
    }
}
```

反序列化由 `MessageChainDeserializer` 根据 `type` 字段自动映射，未知类型会降级为 `UnknownSegment`。

---

## 五、线程模型

### 5.1 事件处理线程池

```yaml
napcat:
  core:
    event-executor:
      core-pool-size: 4
      max-pool-size: 16
      queue-capacity: 1000
```

默认使用 `ThreadPoolExecutor`：
- 核心线程数：4
- 最大线程数：16
- 队列：有界队列 1000
- 拒绝策略：`CallerRunsPolicy`
- 线程名前缀：`napcat-event-pool`

同步模式：`sync-event-processing: true`，事件在 Adapter 读取线程中直接处理。

### 5.2 WebSocket 线程

- `WsClientAdapter`：内部使用 `Java-WebSocket` 库，每个连接一个读取线程
- `WsServerAdapter`：一个 Selector 线程 + 连接线程

### 5.3 Agent 线程

Agent 的每轮思考在 `CompletableFuture` 异步链中执行，不阻塞事件处理线程。

---

## 六、序列化约定

### 6.1 JSON 字段映射

框架使用 Jackson，字段命名遵循 Java 驼峰，通过 `@JsonProperty` 映射 OneBot11 的 snake_case：

```java
public class GroupMessageEvent extends MessageEvent {
    @JsonProperty("group_id")
    private long groupId;

    @JsonProperty("message_id")
    private int messageId;
}
```

### 6.2 消息段序列化

OneBot11 的消息段格式：

```json
{
  "type": "text",
  "data": { "text": "Hello" }
}
```

框架反序列化时：
1. 读取 `type` 字段
2. 根据 type 查找对应的 `MessageSegment` 子类
3. 将 `data` 映射到子类字段
4. 未知类型降级为 `UnknownSegment`，保留原始数据

### 6.3 API 请求格式

```json
{
  "action": "send_group_msg",
  "params": {
    "group_id": 123456789,
    "message": [{"type":"text","data":{"text":"Hello"}}]
  },
  "echo": "uuid"
}
```

响应通过 `echo` 字段匹配到 `NapCatApi` 中的 `pending` 请求表。
