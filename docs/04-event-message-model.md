# 事件与消息模型

本文档描述框架对 OneBot11 协议的事件（Event）和消息（Message）的封装。

---

## 一、事件体系

所有事件继承 `OB11Event`，位于 `com.napcat.core.event`。

```
OB11Event
├── MessageEvent
│   ├── GroupMessageEvent
│   └── PrivateMessageEvent
├── NoticeEvent
│   ├── GroupIncreaseEvent      // 群成员增加
│   ├── GroupDecreaseEvent      // 群成员减少
│   ├── GroupAdminEvent         // 群管理员变动
│   ├── GroupBanEvent           // 群禁言
│   ├── FriendAddEvent          // 好友添加
│   ├── GroupRecallEvent        // 群消息撤回
│   ├── FriendRecallEvent       // 好友消息撤回
│   ├── GroupUploadEvent        // 群文件上传
│   ├── NotifyEvent             // 通知（戳一戳等）
│   ├── LuckyKingEvent          // 运气王
│   ├── HonorEvent              // 群荣誉
│   └── GroupTitleEvent         // 群头衔变更
├── RequestEvent
│   ├── FriendRequestEvent      // 好友请求
│   └── GroupRequestEvent       // 群请求（加群/邀请）
└── MetaEvent
    ├── LifecycleEvent          // 生命周期
    └── HeartbeatEvent          // 心跳
```

### 1.1 通用属性

```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class OB11Event {
    @JsonProperty("time")
    private long time;              // 事件时间戳

    @JsonProperty("self_id")
    private long selfId;            // 收到事件的机器人 QQ 号

    @JsonProperty("post_type")
    private String postType;        // 事件类型标识
}
```

### 1.2 MessageEvent

```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class MessageEvent extends OB11Event {
    @JsonProperty("message_id")
    private int messageId;          // 消息 ID

    @JsonProperty("user_id")
    private long userId;            // 发送者 QQ

    @JsonProperty("message")
    private MessageChain message;   // 消息内容

    @JsonProperty("raw_message")
    private String rawMessage;      // 原始消息文本

    @JsonProperty("sender")
    private Sender sender;          // 发送者信息

    /** 回复纯文本 */
    public void reply(String text);

    /** 回复消息链 */
    public void reply(MessageChain chain);

    /** 获取纯文本（去掉 @、图片等） */
    public String getPlainText();
}
```

### 1.3 GroupMessageEvent

```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupMessageEvent extends MessageEvent {
    @JsonProperty("group_id")
    private long groupId;           // 群号

    @JsonProperty("sub_type")
    private String subType;         // normal / anonymous / notice

    @JsonProperty("message_seq")
    private long messageSeq;        // 消息序号

    @JsonProperty("anonymous")
    private Anonymous anonymous;    // 匿名信息（如有）

    public boolean isAnonymous();
}
```

### 1.4 PrivateMessageEvent

```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrivateMessageEvent extends MessageEvent {
    @JsonProperty("sub_type")
    private String subType;         // friend / group / other
}
```

### 1.5 RequestEvent

```java
// 好友请求
@Data
public class FriendRequestEvent extends RequestEvent {
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("comment")
    private String comment;         // 验证消息
    @JsonProperty("flag")
    private String flag;            // 请求标识
}

// 群请求
@Data
public class GroupRequestEvent extends RequestEvent {
    @JsonProperty("group_id")
    private long groupId;
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("sub_type")
    private String subType;         // add / invite
    @JsonProperty("comment")
    private String comment;
    @JsonProperty("flag")
    private String flag;
}
```

---

## 二、消息链（MessageChain）

OneBot11 的消息是**段（Segment）数组**，框架将其封装为 `MessageChain`。

`MessageChain` 实现了 `List<MessageSegment>`，支持 array/string 双格式反序列化。

### 2.1 基础用法

```java
// 构造消息链（链式调用）
MessageChain chain = MessageChain.ofText("你好")
    .at(123456789L)
    .text("看看这个")
    .image("https://example.com/pic.jpg")
    .reply(event.getMessageId());

// 发送
event.reply(chain);
```

### 2.2 静态工厂方法

```java
MessageChain.ofText(String text)
MessageChain.ofAt(long qq)
MessageChain.ofAtAll()
MessageChain.ofFace(int id)
MessageChain.ofImage(String file)
MessageChain.ofRecord(String file)
MessageChain.ofVideo(String file)
MessageChain.ofFile(String file, String name)
MessageChain.ofReply(int messageId)
MessageChain.ofMarkdown(String content)
MessageChain.ofJson(String data)
MessageChain.ofXml(String data)
MessageChain.ofForward(List<NodeSegment> nodes)
```

### 2.3 链式实例方法

```java
chain.text(String)
chain.at(long)
chain.atAll()
chain.face(int)
chain.image(String)
chain.record(String)
chain.video(String)
chain.file(String, String)
chain.reply(int)
chain.markdown(String)
chain.json(String)
chain.xml(String)
chain.forward(List<NodeSegment>)
```

### 2.4 消息段类型

| OneBot11 类型 | 说明 |
|--------------|------|
| `text` | 纯文本 |
| `at` | @某人 / @全体成员 |
| `face` | QQ 表情 ID |
| `image` | 图片 URL/路径/Base64 |
| `record` | 语音 |
| `video` | 视频 |
| `file` | 文件 |
| `reply` | 回复某条消息 |
| `markdown` | Markdown 消息 |
| `json` | JSON 卡片 |
| `xml` | XML 消息 |
| `node` | 合并转发节点 |
| `forward` | 合并转发 |

### 2.5 从事件解析

```java
@OnGroupMessage
public void onGroup(GroupMessageEvent event) {
    MessageChain msg = event.getMessage();

    // 遍历消息段
    for (MessageSegment segment : msg) {
        if (segment instanceof TextSegment text) {
            System.out.println("文本：" + text.getText());
        } else if (segment instanceof ImageSegment img) {
            System.out.println("图片：" + img.getUrl());
        } else if (segment instanceof AtSegment at) {
            System.out.println("@了：" + at.getQq());
        }
    }

    // 快捷判断
    if (msg.containsImage()) { }
    if (msg.isAt(123456789L)) { }
    if (msg.isAtAll()) { }

    // 提取纯文本（去掉 @、图片等）
    String plain = msg.toPlainText();

    // 提取所有图片 URL
    List<String> images = msg.getImages();

    // 提取被 @ 的 QQ 列表
    List<Long> ats = msg.getAts();
}
```

### 2.6 反序列化

框架根据 NapCat 上报的 `message` 字段自动反序列化为 `MessageChain`。支持两种格式：

**array 格式（推荐）：**

```json
[
  { "type": "text", "data": { "text": "你好 " } },
  { "type": "at", "data": { "qq": "123456789" } },
  { "type": "image", "data": { "file": "https://example.com/pic.jpg" } }
]
```

**string 格式（CQ 码）：**

```json
"[CQ:at,qq=123456789] 你好"
```

通过 `napcat.core.message-post-format` 配置上报格式，框架自动兼容解析。

---

## 三、Sender 信息

```java
@Data
public class Sender {
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("nickname")
    private String nickname;
    @JsonProperty("sex")
    private String sex;           // male / female / unknown
    @JsonProperty("age")
    private int age;
    @JsonProperty("card")
    private String card;          // 群名片
    @JsonProperty("area")
    private String area;
    @JsonProperty("level")
    private String level;         // 等级
    @JsonProperty("role")
    private String role;          // owner / admin / member
    @JsonProperty("title")
    private String title;         // 专属头衔

    public boolean isAdmin();
    public boolean isOwner();
}
```

---

## 四、API 请求与响应

框架封装了 OneBot11 的 HTTP API 调用，核心类为 `NapCatApi`。

### 4.1 API 客户端

```java
@Autowired
private NapCatApi api;

// 发送群消息
api.sendGroupMessage(123456789L, "Hello");

// 发送带消息链的群消息
api.sendGroupMessage(123456789L, MessageChain.ofAt(111L).text("你好"));

// 发送私聊消息
api.sendPrivateMessage(111111111L, "Hello");

// 撤回消息
api.deleteMessage(12345);

// 获取登录信息
api.getLoginInfo();
```

### 4.2 完整 API 列表

| 方法 | 对应 OneBot11 API |
|------|------------------|
| `sendPrivateMessage` | `send_private_msg` |
| `sendGroupMessage` | `send_group_msg` |
| `sendMessage` | `send_msg` |
| `deleteMessage` | `delete_msg` |
| `getMessage` | `get_msg` |
| `getForwardMsg` | `get_forward_msg` |
| `sendLike` | `send_like` |
| `setGroupKick` | `set_group_kick` |
| `setGroupBan` | `set_group_ban` |
| `setGroupWholeBan` | `set_group_whole_ban` |
| `setGroupAdmin` | `set_group_admin` |
| `setGroupCard` | `set_group_card` |
| `setGroupName` | `set_group_name` |
| `setGroupLeave` | `set_group_leave` |
| `setGroupSpecialTitle` | `set_group_special_title` |
| `setGroupPortrait` | `set_group_portrait` |
| `setFriendAddRequest` | `set_friend_add_request` |
| `setGroupAddRequest` | `set_group_add_request` |
| `getLoginInfo` | `get_login_info` |
| `getStrangerInfo` | `get_stranger_info` |
| `getFriendList` | `get_friend_list` |
| `getGroupInfo` | `get_group_info` |
| `getGroupList` | `get_group_list` |
| `getGroupMemberInfo` | `get_group_member_info` |
| `getGroupMemberList` | `get_group_member_list` |
| `getGroupHonorInfo` | `get_group_honor_info` |
| `getCookies` | `get_cookies` |
| `getCsrfToken` | `get_csrf_token` |
| `getCredentials` | `get_credentials` |
| `getRecord` | `get_record` |
| `getImage` | `get_image` |
| `canSendImage` | `can_send_image` |
| `canSendRecord` | `can_send_record` |
| `getStatus` | `get_status` |
| `getVersionInfo` | `get_version_info` |
| `setRestart` | `set_restart` |
| `cleanCache` | `clean_cache` |

### 4.3 NapCat 扩展 API

| 方法 | 说明 |
|------|------|
| `setEssenceMessage` | 设置精华消息 |
| `deleteEssenceMessage` | 移除精华消息 |
| `getEssenceMessageList` | 获取精华消息列表 |
| `sendGroupNotice` | 发送群公告 |
| `getGroupNotice` | 获取群公告 |
| `uploadGroupFile` | 上传群文件 |
| `deleteGroupFile` | 删除群文件 |
| `getGroupFileSystemInfo` | 获取群文件系统信息 |
| `getGroupRootFiles` | 获取群根目录文件 |
| `getGroupFilesByFolder` | 获取群文件夹内文件 |
| `setSelfPortrait` | 设置自身头像 |
| `getGuildList` | 获取频道列表 |
| `getGuildMemberList` | 获取频道成员列表 |
| `sendGuildMessage` | 发送频道消息 |
| `getAiVoice` | AI 语音生成 |
| `getAiRecord` | AI 语音转换 |
| `ocrImage` | OCR 图片识别 |
| `translate` | 翻译 |
| `arkSharePeer` | 发送 Ark 分享 |

---

## 五、事件上下文（EventContext）

框架在处理事件时维护一个线程本地上下文：

```java
@Data
public class EventContext {
    private final OB11Event event;           // 当前事件
    private final NapCatApi api;             // API 客户端
    private final Map<String, Object> attrs = new HashMap<>(); // 扩展属性
}

// 使用
@OnGroupMessage
public void handle(GroupMessageEvent event) {
    EventContext ctx = EventContextHolder.get();
    ctx.setAttr("key", value);
}
```

上下文在同一线程内有效。跨线程（如 Agent 异步回调）时，`MessageEvent` 自身持有 `api` 引用，可直接调用 `event.reply()`。
