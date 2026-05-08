# NapCat Java SDK 文档

基于 [NapCat](https://napneko.github.io/) OneBot11 协议的 Java Bot 开发框架，集成 AI Agent 能力，支持注解驱动与接口驱动两种编程模型。

---

## 文档导航

| 文档 | 内容 |
|------|------|
| [快速开始](01-quick-start.md) | 环境准备、依赖引入、第一个 Bot、启用 Agent |
| [编程模型](02-programming-model.md) | 所有注解、接口定义、返回值处理、路由优先级 |
| [配置参考](03-configuration-reference.md) | 完整配置项、适配器配置、多环境配置 |
| [事件与消息](04-event-message-model.md) | 事件体系、MessageChain、Sender、API 列表 |
| [通信适配器](05-adapter-guide.md) | 四种适配器对比、配置示例、混合模式 |
| [Agent 指南](06-agent-guide.md) | ReAct 循环、Tool 注册、会话管理、多模态、LLM Provider |
| [内部架构](07-internal-architecture.md) | 模块职责、启动流程、线程模型、扩展点 |

---

## 功能特性

- **全协议通信**：支持 HTTP Server / Client、WebSocket Server / Client 四种 NapCat 通信方式
- **双编程模型**：注解式（`@OnGroupMessage`、`@Command`）与接口式（`EventHandler`、`CommandHandler`）并存
- **OneBot11 完整模型**：消息链（MessageChain）、事件、API 请求/响应全覆盖；支持 array / string（CQ 码）双格式上报解析
- **AI Agent 引擎**：内置 ReAct 轻量循环（默认最多 5 轮），支持 Function Calling / Tool Use
- **多模态支持**：`MessageChain.toAgentPrompt()` 保留图片、语音、视频等富文本标记；OpenAI Provider 自动将 `[图片:url]` 提取为 `image_url` 多模态消息
- **多 LLM 后端**：OpenAI 协议兼容（含多模态/vision）、Anthropic Claude、Ollama 本地模型、自定义 OpenAI 端点
- **Spring Boot 开箱即用**：`napcat-spring-boot-starter` 自动配置，高度可配置化
- **组合注解**：支持自定义元注解，如 `@OnGroupAt`、`@AdminCommand`
- **关键词唤醒**：消息包含配置唤醒词时自动触发，无需 @
- **会话上下文**：按用户 ID + 群号隔离的会话管理，支持过期清理与手动重置

---

## 模块架构

```
napcat-java/
├── napcat-parent                  # BOM，统一依赖版本
├── napcat-core                    # OneBot11 协议、通信适配器、事件路由
├── napcat-agent                   # LLM Agent 引擎、Tool 注册、ReAct 循环
├── napcat-llm-providers           # LLM 厂商实现
│   ├── napcat-llm-openai          # OpenAI 协议兼容（含多模态/vision、reasoning_content）
│   ├── napcat-llm-anthropic       # Claude
│   └── napcat-llm-ollama          # Ollama 本地模型
├── napcat-spring-boot-starter     # Spring Boot 自动配置
└── napcat-admin                   # 示例机器人应用
```

---

## License

MIT
