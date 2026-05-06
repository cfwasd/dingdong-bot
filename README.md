# NapCat Java SDK

基于 [NapCat](https://github.com/NapNeko/NapCatQQ) OneBot11 协议的 Java Bot 开发框架，集成 AI Agent 能力，支持注解驱动与接口驱动两种编程模型。

---

## 前置要求

- JDK 17+
- Maven 3.8+
- 已部署并配置好的 NapCat（[安装指南](https://napneko.github.io/guide/start-install)）

## 功能特性

- **全协议通信**：支持 HTTP Server / Client、WebSocket Server / Client 四种 NapCat 通信方式
- **双编程模型**：注解式（`@OnGroupMessage`、`@Command`）与接口式（`EventHandler`、`CommandHandler`）并存
- **OneBot11 完整模型**：消息链（MessageChain）、事件、API 请求/响应全覆盖
- **AI Agent 引擎**：内置 ReAct 轻量循环（默认最多 5 轮），支持 Function Calling / Tool Use
- **多 LLM 后端**：OpenAI 协议兼容、Anthropic Claude、Ollama 本地模型、自定义 OpenAI 端点
- **Spring Boot 开箱即用**：`napcat-spring-boot-starter` 自动配置，高度可配置化
- **组合注解**：支持自定义元注解，如 `@OnGroupAt`、`@AdminCommand`
- **会话上下文**：按用户 ID 隔离的会话管理

## 快速开始

### 1. 创建 Spring Boot 项目，添加依赖

```xml
<dependency>
    <groupId>com.napcat</groupId>
    <artifactId>napcat-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置 NapCat 连接

```yaml
napcat:
  adapter:
    type: websocket-client
    websocket-client:
      url: ws://127.0.0.1:3001
      token: ""
  bot:
    self-id: 123456789
```

### 3. 编写第一个 Bot（注解式）

```java
@Component
public class HelloBot {

    @OnGroupMessage
    @Command("/hello")
    public void hello(GroupMessageEvent event) {
        event.reply("Hello NapCat!");
    }

    @OnGroupMessage
    public void onGroup(GroupMessageEvent event) {
        if (event.getMessage().contains("在吗")) {
            event.reply("在的！");
        }
    }
}
```

### 4. 编写第一个 Bot（接口式）

```java
@Component
public class WeatherCommand implements CommandHandler {

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

### 5. 启用 AI Agent

```java
@Component
public class AgentBot {

    @Autowired
    private NapCatAgent agent;

    @OnGroupMessage
    @MentionFilter
    public void onAt(GroupMessageEvent event) {
        agent.chat(event.getUserId(), event.getMessage().toPlainText())
            .thenAccept(event::reply);
    }
}
```

配置 LLM：

```yaml
napcat:
  llm:
    provider: openai
    openai:
      base-url: https://api.openai.com/v1
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
  agent:
    enabled: true
    max-react-rounds: 5
```

## 模块架构

```
napcat-java/
├── napcat-parent                  # BOM，统一依赖版本
├── napcat-core                    # OneBot11 协议、通信适配器、事件路由
├── napcat-agent                   # LLM Agent 引擎、Tool 注册、ReAct 循环
├── napcat-llm-providers           # LLM 厂商实现
│   ├── napcat-llm-openai          # OpenAI 协议兼容
│   ├── napcat-llm-anthropic       # Claude
│   └── napcat-llm-ollama          # Ollama 本地模型
├── napcat-spring-boot-starter     # Spring Boot 自动配置
└── napcat-example                 # 示例机器人
```

## 通信适配器配置

| 类型 | 配置示例 | 说明 |
|------|---------|------|
| `websocket-client` | `url: ws://127.0.0.1:3001` | 反向 WS，连接 NapCat（推荐） |
| `websocket-server` | `port: 3001` | 正向 WS，等待 NapCat 连接 |
| `http-client` | `url: http://127.0.0.1:3000` | 主动调用 NapCat HTTP API |
| `http-server` | `port: 8080` | 被动接收 NapCat HTTP 上报 |

## 核心注解

| 注解 | 作用 |
|------|------|
| `@OnGroupMessage` | 监听群消息 |
| `@OnPrivateMessage` | 监听私聊消息 |
| `@Command("/xxx {arg}")` | 命令匹配，可与事件注解叠加 |
| `@MentionFilter` | 要求消息中 @ 当前机器人 |
| `@Param("arg")` | 命令参数注入 |
| `@Tool` | 注册 Agent 可调用的工具 |

## 相关链接

- [NapCatQQ 官方文档](https://napneko.github.io/guide/start-install)
- [NapCat API 文档 (Apifox)](https://napcat.apifox.cn/llms.txt)
- [NapCat GitHub](https://github.com/NapNeko/NapCatQQ)

## License

MIT
