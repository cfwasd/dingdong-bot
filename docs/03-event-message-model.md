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
│   └── FriendRecallEvent       // 好友消息撤回
├── RequestEvent
│   ├── FriendRequestEvent      // 好友请求
│   └── GroupRequestEvent       // 群请求（加群/邀请）
└── MetaEvent
    ├── LifecycleEvent          // 生命周期
    └── HeartbeatEvent          // 心跳
```

### 1.1 通用属性

```java
public abstract class OB11Event {
    private long time;              // 事件时间戳
    private long selfId;            // 收到事件的机器人 QQ 号
    private String postType;        // 事件类型标识
}
```

### 1.2 MessageEvent

```java
public abstract class MessageEvent extends OB11Event {
    private long userId;            // 发送者 QQ
    private MessageChain message;   // 消息内容
    private Sender sender;          // 发送者信息

    // 快捷回复
    public void reply(String text);
    public void reply(MessageChain chain);
    public CompletableFuture<ApiResponse> replyAsync(String text);

    // 获取纯文本
    public String getPlainText();
}
```

### 1.3 GroupMessageEvent

```java
public class GroupMessageEvent extends MessageEvent {
    private long groupId;           // 群号
    private String subType;         // normal / anonymous / notice
    private Anonymous anonymous;    // 匿名信息（如有）

    public long getGroupId();
    public boolean isAnonymous();

    // 快捷 API
    public void kick(long userId);           // 踢人
    public void mute(long userId, int sec);  // 禁言
    public void setEssence();                // 设精华
}
```

### 1.4 PrivateMessageEvent

```java
public class PrivateMessageEvent extends MessageEvent {
    private String subType;         // friend / group / other
}
```

### 1.5 NoticeEvent 示例

```java
public class GroupIncreaseEvent extends NoticeEvent {
    private long groupId;
    private long operatorId;        // 操作者 QQ（如管理员审批）
    private long userId;            // 加入者 QQ
    private String subType;         // approve / invite
}

public class GroupBanEvent extends NoticeEvent {
    private long groupId;
    private long operatorId;
    private long userId;            // 被禁言者，0 表示全员禁言
    private long duration;          // 禁言时长（秒），0 表示解除
}
```

### 1.6 RequestEvent

```java
public class FriendRequestEvent extends RequestEvent {
    private long userId;
    private String comment;         // 验证消息
    private String flag;            // 请求标识，用于同意/拒绝

    public void approve();          // 同意
    public void reject();           // 拒绝
}

public class GroupRequestEvent extends RequestEvent {
    private long groupId;
    private long userId;
    private String subType;         // add / invite
    private String comment;
    private String flag;

    public void approve();
    public void reject(String reason);
}
```

---

## 二、消息链（MessageChain）

OneBot11 的消息是**段（Segment）数组**，框架将其封装为 `MessageChain`。

### 2.1 基础用法

```java
// 构造消息链
MessageChain chain = MessageChain.text("你好")
    .at(123456789L)
    .text("看看这个")
    .image("https://example.com/pic.jpg")
    .reply(event.getMessageId());

// 发送
event.reply(chain);
```

### 2.2 消息段类型

| 方法 | OneBot11 类型 | 说明 |
|------|--------------|------|
| `text(String)` | `text` | 纯文本 |
| `at(long)` | `at` | @某人 |
| `atAll()` | `at` | @全体成员 |
| `face(int)` | `face` | QQ 表情 ID |
| `image(String)` | `image` | 图片 URL/路径/Base64 |
| `record(String)` | `record` | 语音 |
| `video(String)` | `video` | 视频 |
| `file(String, String)` | `file` | 文件（名称 + URL） |
| `reply(int)` | `reply` | 回复某条消息 |
| `markdown(String)` | `markdown` | Markdown 消息 |
| `json(String)` | `json` | JSON 卡片 |
| `xml(String)` | `xml` | XML 消息 |
| `forward(List<Node>)` | `forward` | 合并转发消息 |

### 2.3 从事件解析

```java
@OnGroupMessage
public void onGroup(GroupMessageEvent event) {
    MessageChain msg = event.getMessage();

    // 遍历消息段
    for (MessageSegment segment : msg) {
        if (segment instanceof TextSegment text) {
            System.out.println("文本：" + text.getData());
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

### 2.4 反序列化

框架根据 NapCat 上报的 `message` 字段自动反序列化为 `MessageChain`。支持两种格式：

**array 格式（推荐）：**

```json
[
  { "type": "text", "data": { "text": "你好 " } },
  { "type": "at", "data": { "qq": "123456789" } },
  { "type": "image", "data": { "file": "https://example.com/pic.jpg" } }
]
```

**string 格式：**

```json
"[CQ:at,qq=123456789] 你好"
```

通过 `napcat.core.message-post-format` 配置上报格式，框架自动兼容解析。

---

## 三、Sender 信息

```java
public class Sender {
    private long userId;
    private String nickname;
    private String sex;           // male / female / unknown
    private int age;

    // 群聊特有
    private String card;          // 群名片
    private String role;          // owner / admin / member
    private String title;         // 专属头衔
    private int level;            // 等级

    public boolean isAdmin();
    public boolean isOwner();
}
```

---

## 四、API 请求与响应

框架封装了 OneBot11 的 HTTP API 调用。

### 4.1 API 客户端

```java
@Autowired
private NapCatApi api;

// 发送群消息
api.sendGroupMessage(123456789L, "Hello");

// 发送带消息链的群消息
api.sendGroupMessage(123456789L, MessageChain.at(111L).text("你好"));

// 发送私聊消息
api.sendPrivateMessage(111111111L, "Hello");

// 获取群成员列表
List<GroupMember> members = api.getGroupMemberList(123456789L);

// 获取消息详情
MessageInfo info = api.getMessage(12345);

// 撤回消息
api.deleteMessage(12345);

// 获取登录信息
LoginInfo info = api.getLoginInfo();
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

NapCat 在 OneBot11 基础上扩展了更多 API，框架同样封装：

| 方法 | 说明 |
|------|------|
| ` ArkSharePeer` | 发送 Ark 分享 |
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
| `setGroupPortrait` | 设置群头像 |
| `setSelfPortrait` | 设置自身头像 |
| `getGuildList` | 获取频道列表 |
| `getGuildMemberList` | 获取频道成员列表 |
| `sendGuildMessage` | 发送频道消息 |
| `getAiVoice` | AI 语音生成 |
| `getAiRecord` | AI 语音转换 |
| `OCRImage` | OCR 图片识别 |
| `translate` | 翻译 |

---

## 五、事件上下文（EventContext）

框架在处理事件时维护一个上下文对象，可通过 `EventContextHolder` 获取：

```java
public class EventContext {
    private OB11Event event;           // 当前事件
    private NapCatApi api;             // API 客户端
    private Session session;           // 当前用户会话（如有）
    private Map<String, Object> attrs; // 扩展属性
}

// 使用
@OnGroupMessage
public void handle(GroupMessageEvent event) {
    EventContext ctx = EventContextHolder.get();
    ctx.setAttr("key", value);
    // ...
}
```

上下文在同一线程内有效，跨线程需手动传递。
