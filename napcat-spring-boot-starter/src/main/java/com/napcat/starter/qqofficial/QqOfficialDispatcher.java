package com.napcat.starter.qqofficial;

import com.fasterxml.jackson.databind.JsonNode;
import com.napcat.core.adapter.BotAdapter;
import com.napcat.core.api.ApiRequest;
import com.napcat.core.api.NapCatApi;
import com.napcat.core.event.GroupMessageEvent;
import com.napcat.core.event.MessageEvent;
import com.napcat.core.event.PrivateMessageEvent;
import com.napcat.core.event.Sender;
import com.napcat.core.handler.HandlerRegistry;
import com.napcat.core.message.MessageChain;
import com.napcat.core.message.ImageSegment;
import com.napcat.core.message.FileSegment;
import com.napcat.starter.config.QqOfficialProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ 官方事件分发器。
 * 收到事件后先尝试命令匹配，未命中则交给 Agent。
 */
@Slf4j
public class QqOfficialDispatcher implements Consumer<JsonNode> {

    private static final Set<String> MESSAGE_EVENTS = Set.of(
            "C2C_MESSAGE_CREATE",
            "GROUP_AT_MESSAGE_CREATE",
            "GROUP_MESSAGE_CREATE"
    );

    private static final Pattern MEDIA_MARKER = Pattern.compile(
            "(?i)\\[(IMAGE|FILE):(?:url|path|file)=([^\\]]+)]");
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "!\\[[^\\]]*\\]\\(([^)]+)\\)");

    @FunctionalInterface
    public interface AgentInvoker {
        CompletableFuture<String> chat(long userId, long groupId, String prompt);
    }

    private final QqOfficialIdMapper idMapper;
    private final QqOfficialProperties props;
    private final HandlerRegistry handlerRegistry;
    private final AgentInvoker agentInvoker;
    private final QqOfficialApi api;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public QqOfficialDispatcher(QqOfficialIdMapper idMapper, QqOfficialProperties props,
                                HandlerRegistry handlerRegistry, AgentInvoker agentInvoker,
                                QqOfficialApi api) {
        this.idMapper = idMapper;
        this.props = props;
        this.handlerRegistry = handlerRegistry;
        this.agentInvoker = agentInvoker;
        this.api = api;
    }

    @Override
    public void accept(JsonNode event) {
        String eventType = event.path("__event_type").asText("");
        if (eventType.isEmpty()) return;

        if (!MESSAGE_EVENTS.contains(eventType)) return;

        String msgId = event.path("id").asText("");
        String msgSeq = event.path("msg_seq").asText(msgId);
        if (msgId.isBlank()) return;

        if (!inFlight.add(msgSeq)) return;

        try {
            dispatchMessage(eventType, event);
        } finally {
            inFlight.remove(msgSeq);
        }
    }

    private void dispatchMessage(String eventType, JsonNode event) {
        log.debug("QQ official dispatch: eventType={}", eventType);

        boolean isC2c = "C2C_MESSAGE_CREATE".equals(eventType);
        boolean isGroupAt = "GROUP_AT_MESSAGE_CREATE".equals(eventType);
        boolean isGroupFull = "GROUP_MESSAGE_CREATE".equals(eventType);

        JsonNode author = event.path("author");
        String rawUserOpenid = author.path("user_openid").asText("");
        String userOpenid = rawUserOpenid.isBlank() ? author.path("member_openid").asText("") : rawUserOpenid;
        if (userOpenid.isBlank()) {
            log.warn("QQ official dispatch: user_openid blank, event={}", eventType);
            return;
        }

        String content = event.path("content").asText("");
        String groupOpenid = (isGroupAt || isGroupFull) ? event.path("group_openid").asText("") : "";

        // 清理 @ 标记和唤醒词，用于命令匹配
        String cleanedContent = removeAtMentions(content);
        cleanedContent = removeWakeWords(cleanedContent);
        long userId = idMapper.toUserId(userOpenid);
        long groupId = (isGroupAt || isGroupFull) ? idMapper.toGroupId(groupOpenid) : 0L;

        // 命令优先（不受触发逻辑限制）
        if (handlerRegistry != null && dispatchCommand(event, eventType, userId, groupId, cleanedContent)) {
            log.debug("QQ official dispatch: command matched, skipping agent");
            return;
        }

        // 触发模式检查
        if (!shouldTrigger(isC2c, isGroupAt, isGroupFull, content, event)) {
            log.debug("QQ official dispatch: trigger mode skipped this message");
            return;
        }

        // Agent
        String prompt = buildPrompt(event, eventType, cleanedContent);
        if (prompt == null || prompt.isBlank()) return;

        String replyMsgId = event.path("id").asText("");
        log.debug("QQ official dispatch: invoking agent, userId={}", userId);
        agentInvoker.chat(userId, groupId, prompt)
                .thenAccept(reply -> {
                    log.debug("QQ official dispatch: agent reply received, length={}", reply != null ? reply.length() : 0);
                    sendReply(eventType, userOpenid, groupOpenid, reply, replyMsgId);
                })
                .exceptionally(ex -> {
                    log.warn("Agent reply failed for qq-official msg {}", replyMsgId, ex);
                    return null;
                });
    }

    /**
     * 根据 triggerMode 配置判断是否触发 Agent。
     * all = 所有消息都触发
     * wake-word = 仅唤醒词触发
     * mention-or-wake = @ 或唤醒词触发
     * private-all-group-mention-or-wake = 私聊全触发，群聊需 @ 或唤醒词（默认）
     */
    private boolean shouldTrigger(boolean isC2c, boolean isGroupAt, boolean isGroupFull,
                                  String content, JsonNode event) {
        String mode = props.getTriggerMode();
        if (mode == null || mode.isBlank()) {
            mode = "private-all-group-mention-or-wake";
        }

        boolean hasWakeWord = containsWakeWord(content);
        // GROUP_AT_MESSAGE_CREATE 本身就是 @ 机器人消息，直接视为 mentioned
        boolean mentioned = isGroupAt || isMentioned(event);

        switch (mode) {
            case "all":
                return true;
            case "wake-word":
                return hasWakeWord;
            case "mention-or-wake":
                return hasWakeWord || mentioned;
            case "private-all-group-mention-or-wake":
            default:
                if (isC2c) return true;
                return hasWakeWord || mentioned;
        }
    }

    private boolean dispatchCommand(JsonNode event, String eventType,
                                    long userId, long groupId, String content) {
        if (content == null || content.isBlank()) return false;

        String prefix = props.getCommandPrefix();
        if (prefix != null && !prefix.isBlank() && !content.startsWith(prefix)) {
            return false;
        }

        JsonNode author = event.path("author");
        String rawUserOpenid = author.path("user_openid").asText("");
        String userOpenid = rawUserOpenid.isBlank() ? author.path("member_openid").asText("") : rawUserOpenid;
        String groupOpenid = event.path("group_openid").asText("");
        String msgId = event.path("id").asText("");

        MessageEvent msgEvent;
        if (groupId != 0) {
            GroupMessageEvent ge = new GroupMessageEvent();
            ge.setGroupId(groupId);
            ge.setSubType("normal");
            msgEvent = ge;
        } else {
            PrivateMessageEvent pe = new PrivateMessageEvent();
            pe.setSubType("friend");
            msgEvent = pe;
        }

        fillMessageEvent(msgEvent, event, userId, content, eventType);
        msgEvent.setNapCatApi(new NapCatApi(new ReplyAdapter(userOpenid, groupOpenid, msgId, groupId != 0)));

        // 临时清除 fallbackHandler，避免 QQ 通道的 at-me-trigger 干扰命令判断
        var savedFallback = handlerRegistry.getFallbackHandler();
        handlerRegistry.setFallbackHandler(null);
        try {
            List<?> results = handlerRegistry.dispatch(msgEvent);
            return !results.isEmpty();
        } finally {
            handlerRegistry.setFallbackHandler(savedFallback);
        }
    }

    private void fillMessageEvent(MessageEvent event, JsonNode raw, long userId,
                                  String content, String eventType) {
        event.setTimestamp(System.currentTimeMillis() / 1000);
        event.setPostType("message");
        event.setSelfId(0L);
        event.setMessageId(Math.abs(raw.path("id").asText("").hashCode()));
        event.setUserId(userId);
        event.setRawMessage(content);
        event.setMessage(MessageChain.ofText(content));

        Sender sender = new Sender();
        sender.setUserId(userId);
        JsonNode author = raw.path("author");
        String nick = author.path("user_openid").asText("");
        if (nick.isEmpty()) nick = author.path("member_openid").asText("");
        sender.setNickname(nick.isEmpty() ? "QQ用户" : nick);
        event.setSenderObj(sender);
    }

    private String buildPrompt(JsonNode event, String eventType, String content) {
        JsonNode author = event.path("author");
        String rawName = author.path("user_openid").asText("");
        String senderName = rawName.isBlank() ? author.path("member_openid").asText("QQ用户") : rawName;

        if ("GROUP_AT_MESSAGE_CREATE".equals(eventType) || "GROUP_MESSAGE_CREATE".equals(eventType)) {
            String groupName = event.path("group_openid").asText("群聊");
            return "[QQ官方/群聊:" + groupName + "] [发送者:" + senderName + "] " + content;
        }
        return "[QQ官方/私聊] [发送者:" + senderName + "] " + content;
    }

    private void sendReply(String eventType, String userOpenid, String groupOpenid,
                           String reply, String msgId) {
        if (reply == null || reply.isBlank()) return;
        log.debug("QQ official sendReply: groupOpenid={}, userOpenid={}, msgId={}, replyLen={}",
                groupOpenid, userOpenid, msgId, reply.length());

        boolean isGroup = groupOpenid != null && !groupOpenid.isBlank();
        String target = isGroup ? groupOpenid : userOpenid;

        try {
            // 1. 先把 markdown 图片 ![text](url) 转为 [IMAGE:url=...] 统一处理
            String normalized = normalizeMarkdownImages(reply);
            // 2. 拆出所有媒体，文字部分用普通文本发送
            String text = convertMediaMarkers(normalized, target, isGroup, msgId);
            if (!text.isBlank()) {
                if (isGroup) {
                    api.sendGroupMessage(groupOpenid, text, msgId);
                } else {
                    api.sendC2cMessage(userOpenid, text, msgId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send qq-official reply", e);
        }
    }

    /** 将 markdown 图片 ![text](url) 转为 [IMAGE:url=url] 统一格式 */
    private String normalizeMarkdownImages(String text) {
        if (text == null || text.isBlank()) return text;
        Matcher matcher = MARKDOWN_IMAGE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group(1).trim();
            matcher.appendReplacement(sb, "[IMAGE:url=" + Matcher.quoteReplacement(url) + "]");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 将 [IMAGE:url=...] 和 [FILE:path=...] 拆出来单独用富媒体发送，返回纯文本 */
    private String convertMediaMarkers(String text, String target, boolean isGroup, String msgId) {
        Matcher matcher = MEDIA_MARKER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String type = matcher.group(1);
            String value = matcher.group(2);
            matcher.appendReplacement(result, "");
            if (value != null && !value.isBlank()) {
                sendMediaAsync(target, isGroup, type, value.trim(), msgId);
            }
        }
        matcher.appendTail(result);
        return result.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private void sendMediaAsync(String target, boolean isGroup, String type, String value, String msgId) {
        if (value == null || value.isBlank()) return;
        try {
            int fileType = "IMAGE".equalsIgnoreCase(type) ? 1 : 4;
            String fileInfo;
            if (isGroup) {
                fileInfo = api.uploadGroupFile(target, fileType, value);
                api.sendGroupMedia(target, fileInfo, msgId);
            } else {
                fileInfo = api.uploadC2cFile(target, fileType, value);
                api.sendC2cMedia(target, fileInfo, msgId);
            }
        } catch (IOException e) {
            log.warn("Failed to send {} via qq-official", type, e);
        }
    }

    private boolean containsWakeWord(String content) {
        if (props.getWakeWords() == null || props.getWakeWords().isEmpty()) return false;
        for (String word : props.getWakeWords()) {
            if (word != null && !word.isBlank() && content.contains(word)) return true;
        }
        return false;
    }

    private boolean isMentioned(JsonNode event) {
        JsonNode mentions = event.path("mentions");
        if (mentions.isArray()) {
            for (JsonNode m : mentions) {
                if (m.path("is_you").asBoolean(false)) return true;
            }
        }
        return false;
    }

    private String removeWakeWords(String content) {
        if (content == null || props.getWakeWords() == null || props.getWakeWords().isEmpty()) return content;
        String result = content;
        for (String word : props.getWakeWords()) {
            if (word != null && !word.isBlank()) {
                result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), "");
            }
        }
        return result.replaceAll("\\s+", " ").trim();
    }

    private String removeAtMentions(String content) {
        if (content == null || content.isBlank()) return content;
        return content.replaceAll("<@[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    /**
     * 伪造的 BotAdapter，让命令 handler 的 event.reply() 能通过 NapCatApi 发送消息。
     */
    private class ReplyAdapter implements BotAdapter {
        private final String userOpenid;
        private final String groupOpenid;
        private final String msgId;
        private final boolean isGroup;

        ReplyAdapter(String userOpenid, String groupOpenid, String msgId, boolean isGroup) {
            this.userOpenid = userOpenid;
            this.groupOpenid = groupOpenid;
            this.msgId = msgId;
            this.isGroup = isGroup;
        }

        @Override public String getId() { return "qq-official-reply"; }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isConnected() { return true; }

        @Override
        public void sendApiRequest(ApiRequest<?> request) {
            Object message = request.getParams() instanceof Map<?, ?> params ? params.get("message") : null;
            if (message instanceof MessageChain chain) {
                sendMessageChain(chain);
            } else if (message instanceof String text) {
                sendText(text);
            }
        }

        private void sendMessageChain(MessageChain chain) {
            StringBuilder textBuf = new StringBuilder();
            for (var seg : chain) {
                if (seg instanceof ImageSegment img) {
                    flushText(textBuf);
                    sendImage(firstNonBlank(img.getUrl(), img.getFile()));
                } else if (seg instanceof FileSegment file) {
                    flushText(textBuf);
                    sendFile(file.getFile());
                } else if (seg instanceof com.napcat.core.message.TextSegment text) {
                    textBuf.append(text.getText());
                }
            }
            flushText(textBuf);
        }

        private void flushText(StringBuilder buf) {
            if (!buf.isEmpty()) {
                sendText(buf.toString());
                buf.setLength(0);
            }
        }

        private void sendText(String text) {
            if (text == null || text.isBlank()) return;
            try {
                // 统一处理 markdown 图片和自定义标记，拆成文字+图片
                String normalized = normalizeMarkdownImages(text);
                String plain = convertMediaMarkers(normalized,
                        isGroup ? groupOpenid : userOpenid, isGroup, msgId);
                if (!plain.isBlank()) {
                    if (isGroup) {
                        api.sendGroupMessage(groupOpenid, plain, msgId);
                    } else {
                        api.sendC2cMessage(userOpenid, plain, msgId);
                    }
                }
            } catch (IOException e) {
                log.warn("ReplyAdapter sendText failed", e);
            }
        }

        private void sendImage(String imageUrl) {
            if (imageUrl == null || imageUrl.isBlank()) return;
            try {
                if (isGroup) {
                    String fileInfo = api.uploadGroupFile(groupOpenid, 1, imageUrl);
                    api.sendGroupMedia(groupOpenid, fileInfo, msgId);
                } else {
                    String fileInfo = api.uploadC2cFile(userOpenid, 1, imageUrl);
                    api.sendC2cMedia(userOpenid, fileInfo, msgId);
                }
            } catch (IOException e) {
                log.warn("ReplyAdapter sendImage failed", e);
            }
        }

        private void sendFile(String filePath) {
            if (filePath == null || filePath.isBlank()) return;
            try {
                if (isGroup) {
                    String fileInfo = api.uploadGroupFile(groupOpenid, 4, filePath);
                    api.sendGroupMedia(groupOpenid, fileInfo, msgId);
                } else {
                    String fileInfo = api.uploadC2cFile(userOpenid, 4, filePath);
                    api.sendC2cMedia(userOpenid, fileInfo, msgId);
                }
            } catch (IOException e) {
                log.warn("ReplyAdapter sendFile failed", e);
            }
        }

        @Override public void setMessageHandler(Consumer<String> handler) {}

        private static String firstNonBlank(String... values) {
            for (String v : values) {
                if (v != null && !v.isBlank()) return v;
            }
            return "";
        }
    }
}
