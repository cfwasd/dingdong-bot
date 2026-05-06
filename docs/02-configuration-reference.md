# 配置参考

所有配置项通过 `application.yml` 或 `application.properties` 设置，前缀为 `napcat`。

---

## 完整配置示例

```yaml
napcat:
  # ========== 通信适配器 ==========
  adapter:
    # 类型：websocket-client / websocket-server / http-client / http-server
    type: websocket-client

    websocket-client:
      url: ws://127.0.0.1:3001
      token: ""
      reconnect-interval: 5000          # 断线重连间隔，毫秒
      heart-interval: 30000             # 心跳间隔，毫秒
      debug: false                      # 打印原始帧

    websocket-server:
      host: 0.0.0.0
      port: 3001
      token: ""
      debug: false

    http-client:
      url: http://127.0.0.1:3000
      token: ""
      timeout: 30000                    # HTTP 请求超时，毫秒

    http-server:
      host: 0.0.0.0
      port: 8080
      token: ""
      path: /napcat/webhook             # 接收上报的路径

  # ========== Bot 基础配置 ==========
  bot:
    self-id: 123456789                  # 当前机器人 QQ 号
    command-prefix: "/"                 # 命令前缀，空字符串表示无前缀
    at-me-trigger: true                 # 被 @ 时是否自动触发 Agent（需 agent.enabled=true）
    ignore-self-message: true           # 是否过滤自己发的消息
    super-users:                        # 超级管理员 QQ 号列表
      - 111111111
      - 222222222

  # ========== LLM 配置 ==========
  llm:
    provider: openai                    # openai / anthropic / ollama / custom

    openai:
      base-url: https://api.openai.com/v1
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
      max-tokens: 2000
      temperature: 0.7
      timeout: 60000

    anthropic:
      base-url: https://api.anthropic.com
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-sonnet-4-6
      max-tokens: 2000
      temperature: 0.7
      timeout: 60000

    ollama:
      base-url: http://localhost:11434
      model: llama3
      timeout: 120000

    custom:
      base-url: http://localhost:8000/v1  # 任意兼容 OpenAI 协议的端点
      api-key: ""                         # 可为空
      model: default
      max-tokens: 2000
      timeout: 60000

  # ========== Agent 配置 ==========
  agent:
    enabled: true
    max-react-rounds: 5                 # ReAct 最大思考轮数
    system-prompt: "你是一个有用的 QQ 机器人助手。"
    timeout-per-round: 30000            # 每轮 LLM 调用超时
    session-ttl: 3600                   # 会话过期时间，秒

  # ========== 高级配置 ==========
  core:
    event-executor:                     # 事件处理线程池
      core-pool-size: 4
      max-pool-size: 16
      queue-capacity: 1000
    message-post-format: array          # array / string，OneBot11 上报格式
```

---

## 配置项详解

### napcat.adapter

控制与 NapCat 的通信方式，四选一。

#### type = `websocket-client`（推荐）

主动连接 NapCat 的 WebSocket Server，双工通信，性能最好。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `url` | String | 必填 | NapCat WS 地址 |
| `token` | String | `""` | 鉴权 Token |
| `reconnect-interval` | long | `5000` | 断线重连间隔（ms） |
| `heart-interval` | long | `30000` | 心跳间隔（ms），0 表示不发送心跳 |
| `debug` | boolean | `false` | 是否打印原始 WS 帧 |

**对应 NapCat 配置：**

```json
{
  "network": {
    "websocketServers": [{
      "enable": true,
      "port": 3001,
      "token": ""
    }]
  }
}
```

#### type = `websocket-server`

等待 NapCat 主动连接。适合多 NapCat 实例连接同一个 Bot 服务的场景。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `host` | String | `"0.0.0.0"` | 监听地址 |
| `port` | int | `3001` | 监听端口 |
| `token` | String | `""` | 鉴权 Token |
| `debug` | boolean | `false` | 是否打印原始 WS 帧 |

**对应 NapCat 配置：**

```json
{
  "network": {
    "websocketClients": [{
      "enable": true,
      "url": "ws://bot-server:3001",
      "token": ""
    }]
  }
}
```

#### type = `http-client`

主动轮询/调用 NapCat 的 HTTP API。事件接收需配合 HTTP Server 或其他机制。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `url` | String | 必填 | NapCat HTTP 地址 |
| `token` | String | `""` | 鉴权 Token |
| `timeout` | long | `30000` | HTTP 请求超时（ms） |

**注意：** 纯 HTTP Client 模式下事件推送需要额外配置 NapCat 的 HTTP Client 上报到本服务。

#### type = `http-server`

被动接收 NapCat 的 HTTP 上报。适合 Webhook 风格的部署。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `host` | String | `"0.0.0.0"` | 监听地址 |
| `port` | int | `8080` | 监听端口 |
| `token` | String | `""` | 鉴权 Token |
| `path` | String | `"/napcat/webhook"` | 接收上报的 URL 路径 |

**对应 NapCat 配置：**

```json
{
  "network": {
    "httpClients": [{
      "enable": true,
      "url": "http://bot-server:8080/napcat/webhook",
      "token": ""
    }]
  }
}
```

---

### napcat.bot

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `self-id` | long | `0` | 当前机器人 QQ 号，用于过滤自身消息和 @ 判断 |
| `command-prefix` | String | `"/"` | 命令前缀。设为 `""` 表示无前缀，直接匹配命令模板开头 |
| `at-me-trigger` | boolean | `true` | 被 @ 时是否尝试走 Agent 流程 |
| `ignore-self-message` | boolean | `true` | 是否忽略机器人自己发送的消息 |
| `super-users` | List<long> | `[]` | 超级管理员 QQ 号，用于 `Role.SUPERUSER` 判断 |

---

### napcat.llm

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `provider` | String | 必填 | LLM 提供商：`openai` / `anthropic` / `ollama` / `custom` |

各提供商的专有配置见上方完整示例。通用字段：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `base-url` | String | 必填 | API 基础地址 |
| `api-key` | String | `""` | API Key（Ollama 可为空） |
| `model` | String | 必填 | 模型名称 |
| `max-tokens` | int | `2000` | 最大生成 Token 数 |
| `temperature` | double | `0.7` | 采样温度 |
| `timeout` | long | `60000` | 单次请求超时（ms） |

---

### napcat.agent

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用 Agent 功能 |
| `max-react-rounds` | int | `5` | ReAct 循环最大轮数，超过则返回提示 |
| `system-prompt` | String | `""` | Agent 系统提示词，空则使用默认提示 |
| `timeout-per-round` | long | `30000` | 每轮 LLM 调用超时（ms） |
| `session-ttl` | long | `3600` | 会话上下文过期时间（秒） |

---

### napcat.core

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `event-executor.core-pool-size` | int | `4` | 事件处理线程池核心线程数 |
| `event-executor.max-pool-size` | int | `16` | 最大线程数 |
| `event-executor.queue-capacity` | int | `1000` | 任务队列容量 |
| `message-post-format` | String | `"array"` | OneBot11 消息上报格式：`array` 或 `string` |

---

## 多环境配置

Spring Boot 原生支持：

```yaml
# application-dev.yml
napcat:
  adapter:
    type: websocket-client
    websocket-client:
      url: ws://127.0.0.1:3001

# application-prod.yml
napcat:
  adapter:
    type: websocket-client
    websocket-client:
      url: ws://napcat.internal:3001
      token: ${NAPCAT_TOKEN}
  llm:
    openai:
      api-key: ${OPENAI_API_KEY}
```

---

## 配置验证

框架启动时会校验配置，以下情况会报错并阻止启动：

- `adapter.type` 未设置或非法
- `adapter.*.url` 格式错误
- `llm.provider` 已启用但对应配置缺失 `base-url`
- `bot.self-id` 为 0 且启用了 `ignore-self-message`（警告级别）
