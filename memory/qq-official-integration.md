---
name: qq-official-integration
description: QQ 官方 Bot API v2 接入方案、消息发送策略、已知限制与踩坑记录
metadata:
  type: project
---

## 架构决策

QQ 官方 Bot API v2 采用 **独立通道（Plan B）**：不接入 napcat-core 的 `BotAdapter` 抽象，直接实现 `SmartLifecycle` + `WebSocketClient` + `HTTP REST` 客户端。复用 `HandlerRegistry` 和 `NapCatAgent`，与 WeChat 通道策略一致。

- 入口：`QqOfficialLifecycle`（Spring SmartLifecycle）
- WS 事件接收：`QqOfficialWsClient`（OpCode 协议：0/1/2/6/7/9/10/11）
- HTTP 消息发送：`QqOfficialApi`（Access Token 刷新 + 富媒体上传 + 文本/富媒体发送）
- 事件分发：`QqOfficialDispatcher`（`Consumer<JsonNode>`）
- ID 映射：`QqOfficialIdMapper`（SHA-256 稳定哈希 → long，同 `WechatIdMapper`）

## 消息发送策略

QQ 官方 API 的 `msg_type=2`（Markdown）对图片渲染不可靠，已**完全弃用**。当前策略：

- **文本**：`msg_type=0`（普通文本）
- **图片/文件**：`msg_type=7`（富媒体），两步流：
  1. `POST /files` 上传（`file_type=1` 图片，`file_type=4` 文件，`srv_send_msg=false`）
  2. `POST /messages` 发送，`msg_type=7`，`media.file_info=上一步返回的 file_info`

Agent 输出中的图片标记处理链：
1. `NapCatAgent.buildEffectivePrompt()` 追加格式要求：使用 `[IMAGE:url=图片地址]`
2. `QqOfficialDispatcher.normalizeMarkdownImages()`：`![text](url)` → `[IMAGE:url=url]`
3. `QqOfficialDispatcher.convertMediaMarkers()`：剥离 `[IMAGE:url=...]` 和 `[FILE:path=...]`，纯文本单独发送，媒体异步上传+发送

## 去重机制（msg_seq）

同一 `msg_id`（原始用户消息的 ID）作为 `msg_id` 参数回复时，QQ 服务端要求每条回复必须有不同的 `msg_seq`，否则返回 `40054005`（消息被去重）。

修复：`QqOfficialApi` 内部持有 `AtomicInteger msgSeqCounter`，`post()` 方法自动为含 `msg_id` 但无 `msg_seq` 的请求体分配递增的 `msg_seq`。

## @ 提及检测

- `GROUP_AT_MESSAGE_CREATE`：原生 @ 消息，直接触发
- `GROUP_MESSAGE_CREATE`（全量群消息）：通过 `mentions[].is_you` 字段检测是否被 @
- 命令匹配前调用 `removeAtMentions()` 剥离 `<@openid>` 前缀，避免 `/testmd` 被 `<@bot>` 打断

## 触发模式（triggerMode）

`QqOfficialProperties.triggerMode` 支持四种模式：

- `all`：所有消息触发 Agent
- `wake-word`：仅唤醒词触发
- `mention-or-wake`：@ 或唤醒词触发
- `private-all-group-mention-or-wake`（默认）：私聊全触发，群聊需 @ 或唤醒词

## 命令复用

QQ 官方通道复用 `AgentDemoBot` 中的 `@Command` 定义（`/testmd`、`/persona`、`/voice`、`/clear`、`/安静` 等）。命令 dispatch 通过 `ReplyAdapter`（内部实现 `BotAdapter`）包装 `QqOfficialApi`，让 `event.reply()` 走 QQ 官方 HTTP API。

## 已知限制

1. **不支持语音**：QQ 官方 API v2 无发送语音消息的能力，TTS 功能不可用
2. **不支持合并转发**：无 `send_group_forward_msg` 等价 API，工具过程无法合并转发
3. **群成员身份不可识别**：`GROUP_MESSAGE_CREATE` 事件不带 `owner`/`admin` 等角色字段，安静模式的群主/管理员权限降级为仅超管可用
4. **接收用户图片（Vision）暂未实现**：`content` 中图片以 `<attachment>` 形式存在，尚未提取传入 Agent

## 配置示例

```yaml
napcat:
  qq-official:
    enabled: true
    app-id: "102069145"
    app-secret: ${QQ_OFFICIAL_SECRET}
    sandbox: false
    trigger-mode: all
    wake-words:
      - bot
```

## 踩坑记录

- **Lambda effectively-final 错误**：`userOpenid` 不能用 `if` 重赋值后再放入 `thenAccept`，改用三元表达式一次性赋值
- **Gateway 401**：`getGatewayUrl()` 必须带 `Authorization: QQBot <token>` 请求头
- **命令不响应**：事件未注入 `NapCatApi`，`ReplyAdapter` 解决了命令回复路径
- **私聊 /testmd 失败**：命令方法参数类型为 `GroupMessageEvent`，QQ 官方私聊创建的是 `PrivateMessageEvent`，去掉参数即可（通过 HandlerRegistry 反射调用时无参方法也能匹配）
