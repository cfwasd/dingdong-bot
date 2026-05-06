# 内部架构

本文档面向框架开发者，描述各模块职责、核心流程和扩展点。

---

## 一、模块职责

### napcat-core

- **协议层**：OneBot11 事件/消息数据模型、JSON 反序列化
- **通信层**：`BotAdapter` 抽象及四种实现（WS/HTTP）
- **路由层**：注解扫描、接口收集、路由表构建、事件分发
- **API 层**：`NapCatApi` 封装所有 OneBot11 + NapCat 扩展 API

### napcat-agent

- **Agent 引擎**：ReAct 循环实现
- **会话管理**：`SessionManager` 按用户隔离上下文
- **Tool 注册**：`ToolRegistry` 扫描 `@Tool` 注解，生成 LLM Schema
- **抽象接口**：`LlmProvider`、`Session`、`ToolSchema` 等

### napcat-llm-providers

各 LLM 厂商的具体实现，每个子模块依赖 `napcat-agent` 并实现 `LlmProvider`。

- **napcat-llm-openai**：OpenAI SDK / OkHttp 实现，兼容所有 OpenAI 协议端点
- **napcat-llm-anthropic**：Anthropic SDK 实现，支持 Messages API + Tool Use
- **napcat-llm-ollama**：Ollama HTTP API 实现，支持本地模型

### napcat-spring-boot-starter

- **自动配置**：`NapCatAutoConfiguration` 根据 `application.yml` 装配 Bean
- **属性绑定**：`NapCatProperties` 配置类
- **Bean 扫描**：自动注册所有 `EventHandler`、`CommandHandler`、`BotInitializer`
- **生命周期**：Spring 启动时启动 Adapter，关闭时优雅停止

### napcat-example

示例项目，演示注解方式、接口方式、Agent 模式的完整用法。

---

## 二、核心流程

### 2.1 启动流程

```
Spring ApplicationContext 初始化
  │
  ├─ NapCatAutoConfiguration
  │     ├─ 解析 NapCatProperties
  │     ├─ 创建 BotAdapter（根据 adapter.type）
  │     ├─ 创建 NapCatApi
  │     ├─ 创建 EventDispatcher
  │     ├─ 创建 AnnotationScanner
  │     └─ 创建 HandlerRegistry
  │
  ├─ AnnotationScanner
  │     ├─ 扫描所有 @Component Bean
  │     ├─ 解析方法上的注解（递归元注解）
  │     └─ 生成 HandlerMethod 注册到 HandlerRegistry
  │
  ├─ HandlerRegistry（收集接口实现）
  │     ├─ getBeansOfType(EventHandler.class)
  │     ├─ getBeansOfType(CommandHandler.class)
  │     ├─ getBeansOfType(BotInitializer.class)
  │     └─ 全部注册到 RouterTable
  │
  ├─ ToolRegistry（如果 agent.enabled）
  │     ├─ 扫描 @Tool 注解的方法
  ��     └─ 转换为各 LLM 格式的 ToolSchema
  │
  └─ BotAdapter.start()
        └─ 建立与 NapCat 的连接
```

### 2.2 事件处理流程

```
NapCat 上报事件
  │
  ▼
BotAdapter 收到原始 JSON
  │
  ▼
JsonEventDecoder 反序列化为 OB11Event 子类
  │
  ▼
EventDispatcher.dispatch(event)
  │
  ├─ 设置 EventContext（ThreadLocal）
  ├─ 忽略自身消息（如配置）
  ├─ 查找 RouterTable 中匹配的 Handlers
  │     按优先级排序，精确匹配优先
  │
  ├─ 顺序执行 Handlers
  │     每个 Handler 在独立线程（或线程池）中执行
  │     第一个命中即执行，除非抛出 StopRoutingException
  │
  ├─ 处理返回值（String/MessageChain -> 自动回复）
  └─ 清理 EventContext
```

### 2.3 ReAct Agent 流程

```
agent.chat(userId, input)
  │
  ├─ 获取/创建 Session
  ├─ 组装 Prompt（system + history + tools + user input）
  │
  ▼
Round 1..N（N <= maxRounds）
  ├─ 调用 LlmProvider.chat()
  ├─ 解析响应
  │     ├─ 纯文本 -> 直接返回给用户
  │     └─ ToolCall 列表 -> 继续
  ├─ 并行执行所有 ToolCall
  │     ├─ 查找 ToolRegistry 中对应的方法
  │     ├─ 参数类型转换
  │     ├─ 反射调用
  │     └─ 收集结果（成功/异常）
  ├─ 将 Tool 结果加入 Session History
  └─ 进入下一轮
  │
  ▼
超过 maxRounds -> 返回提示信息
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
  ├─ userId: long
  ├─ message: MessageChain
  ├─ sender: Sender
  ├─ reply(String): void
  └─ getPlainText(): String

GroupMessageEvent extends MessageEvent
  ├─ groupId: long
  ├─ subType: String
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
  └─ getAts(): List<Long>

MessageSegment (abstract)
  ├─ type: String
  └─ data: Map<String, Object>

TextSegment extends MessageSegment
ImageSegment extends MessageSegment
AtSegment extends MessageSegment
// ... 其他段类型
```

### 3.3 路由体系

```
RouterTable
  ├─ handlers: List<HandlerEntry>
  ├─ register(HandlerEntry): void
  └─ match(OB11Event): List<HandlerEntry>

HandlerEntry
  ├─ priority: int
  ├─ condition: Predicate<OB11Event>
  ├─ executor: Function<OB11Event, Object>
  └─ isCommand: boolean

EventDispatcher
  ├─ routerTable: RouterTable
  ├─ executor: Executor
  ├─ dispatch(OB11Event): void
  └─ execute(HandlerEntry, OB11Event): void
```

### 3.4 Agent 体系

```
NapCatAgent
  ├─ llmProvider: LlmProvider
  ├─ toolRegistry: ToolRegistry
  ├─ sessionManager: SessionManager
  ├─ chat(long, String): CompletableFuture<String>
  └─ reactLoop(Session, int): CompletableFuture<String>

LlmProvider (interface)
  ├─ getProviderName(): String
  └─ chat(Session, String, List<ToolSchema>): CompletableFuture<LlmResponse>

ToolRegistry
  ├─ tools: Map<String, ToolMethod>
  ├─ register(Object): void
  ├─ getSchemas(): List<ToolSchema>
  └─ invoke(String, Map<String, Object>): Object

Session
  ├─ userId: long
  ├─ history: List<ChatMessage>
  ├─ createdAt: long
  ├─ lastAccessedAt: long
  ├─ addMessage(ChatMessage): void
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
    @Override public void sendApiRequest(ApiRequest<?> req, Consumer<ApiResponse> cb) { /* 发送 API */ }
    @Override public void setEventConsumer(Consumer<OB11Event> consumer) { /* 设置事件回调 */ }
}
```

### 4.2 自定义注解处理器

```java
@Component
public class MyAnnotationProcessor implements AnnotationProcessor {

    @Override
    public boolean supports(Annotation annotation) {
        return annotation instanceof MyCustomFilter;
    }

    @Override
    public Predicate<OB11Event> buildCondition(Annotation annotation, Method method) {
        MyCustomFilter filter = (MyCustomFilter) annotation;
        return event -> /* 自定义过滤逻辑 */;
    }
}
```

### 4.3 自定义 LLM Provider

```java
@Component
public class MyLlmProvider implements LlmProvider {
    @Override public String getProviderName() { return "my-llm"; }
    @Override public CompletableFuture<LlmResponse> chat(Session s, String input, List<ToolSchema> tools) {
        // 实现调用逻辑
    }
}
```

### 4.4 自定义消息段

```java
public class CustomSegment extends MessageSegment {
    public CustomSegment(Map<String, Object> data) {
        super("custom", data);
    }
}

// 注册反序列化器
@Component
public class CustomSegmentRegistrar implements SegmentDeserializerRegistrar {
    @Override
    public void register(Map<String, Function<JsonNode, MessageSegment>> registry) {
        registry.put("custom", node -> new CustomSegment(/* ... */));
    }
}
```

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
- 队列：有界队列 1000，满则拒绝并记录日志
- 拒绝策略：CallerRunsPolicy（由调用线程执行）

### 5.2 WebSocket 线程

- `WsClientAdapter`：内部使用 `Java-WebSocket` 库，每个连接一个读取线程
- `WsServerAdapter`：一个 Selector 线程 + 连接线程

### 5.3 Agent 线程

Agent 的每轮思考在 `ForkJoinPool.commonPool()` 中异步执行，不阻塞事件处理线程。

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

---

## 七、版本兼容性

### OneBot11 协议

框架基于 OneBot11 标准实现，同时兼容 NapCat 的扩展字段。

### NapCat 版本

| 框架版本 | 测试过的 NapCat 版本 |
|---------|---------------------|
| 1.0.0 | NapCat v2.x |

### 升级策略

- 主版本号（x.0.0）：不兼容的 API 变更
- 次版本号（0.x.0）：新增功能，向后兼容
- 修订号（0.0.x）：Bug 修复，完全兼容
