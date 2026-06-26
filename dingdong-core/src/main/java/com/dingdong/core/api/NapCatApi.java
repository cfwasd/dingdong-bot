package com.dingdong.core.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dingdong.core.adapter.BotAdapter;
import com.dingdong.core.message.MessageChain;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;

/**
 * NapCat API 客户端 — OneBot11 + NapCat 扩展 API 统一入口。
 * 
 * 通过 echo 机制匹配请求与响应，支持超时。
 * MessageRouter 将 API 响应路由到 {@link #onResponse(String)}。
 */
@Slf4j
public class NapCatApi {

    private static final long DEFAULT_TIMEOUT_MS = 30000;

    private final BotAdapter adapter;
    private final ObjectMapper mapper;
    private final long defaultTimeoutMs;

    /** echo → CompletableFuture，核心的请求-响应匹配表 */
    private final ConcurrentHashMap<String, PendingRequest> pending = new ConcurrentHashMap<>();

    /** 定期清理超时的 pending 请求 */
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dingdong-api-cleaner");
        t.setDaemon(true);
        return t;
    });

    public NapCatApi(BotAdapter adapter) {
        this(adapter, new ObjectMapper(), DEFAULT_TIMEOUT_MS);
    }

    public NapCatApi(BotAdapter adapter, ObjectMapper mapper, long defaultTimeoutMs) {
        this.adapter = adapter;
        this.mapper = mapper;
        this.defaultTimeoutMs = defaultTimeoutMs;
        cleaner.scheduleAtFixedRate(this::cleanExpired, 10, 10, TimeUnit.SECONDS);
    }

    // ================================================================
    // 响应处理入口 — 由 MessageRouter 调用
    // ================================================================

    public void onResponse(String rawJson) {
        try {
            ApiResponse response = mapper.readValue(rawJson, ApiResponse.class);
            String echo = response.getEcho();
            if (echo == null || echo.isEmpty()) {
                log.debug("Received API response without echo: {}", rawJson);
                return;
            }
            PendingRequest pr = pending.remove(echo);
            if (pr != null) {
                pr.future.complete(response);
            } else {
                log.debug("Received API response for unknown echo: {}", echo);
            }
        } catch (Exception e) {
            log.error("Failed to parse API response: {}", rawJson, e);
        }
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(entry -> {
            if (now - entry.getValue().createdAt > entry.getValue().timeoutMs) {
                entry.getValue().future.completeExceptionally(
                        new TimeoutException("API call timed out: echo=" + entry.getKey()));
                return true;
            }
            return false;
        });
    }

    // ================================================================
    // 底层调用
    // ================================================================

    public boolean isAvailable() {
        return adapter != null;
    }

    public CompletableFuture<ApiResponse> call(String action, Object... keyValues) {
        return call(action, defaultTimeoutMs, keyValues);
    }

    public CompletableFuture<ApiResponse> call(String action, long timeoutMs, Object... keyValues) {
        if (adapter == null) {
            CompletableFuture<ApiResponse> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException(
                    "No BotAdapter available. Cannot send API requests."));
            return future;
        }

        ApiRequest<Map<String, Object>> req = ApiRequest.of(action, keyValues);
        CompletableFuture<ApiResponse> future = new CompletableFuture<>();
        pending.put(req.getEcho(), new PendingRequest(future, timeoutMs));

        adapter.sendApiRequest(req);
        return future;
    }

    // ================================================================
    // 消息发送
    // ================================================================

    public CompletableFuture<ApiResponse> sendPrivateMessage(long userId, Object message) {
        return call("send_private_msg",
                "user_id", userId, "message", toMessageParam(message));
    }

    public CompletableFuture<ApiResponse> sendGroupMessage(long groupId, Object message) {
        return call("send_group_msg",
                "group_id", groupId, "message", toMessageParam(message));
    }

    public CompletableFuture<ApiResponse> sendMessage(String messageType, long targetId, Object message) {
        return call("send_msg",
                "message_type", messageType,
                "group".equals(messageType) ? "group_id" : "user_id", targetId,
                "message", toMessageParam(message));
    }

    public CompletableFuture<ApiResponse> deleteMessage(int messageId) {
        return call("delete_msg", "message_id", messageId);
    }

    public CompletableFuture<ApiResponse> getMessage(int messageId) {
        return call("get_msg", "message_id", messageId);
    }

    public CompletableFuture<ApiResponse> getForwardMsg(String messageId) {
        return call("get_forward_msg", "message_id", messageId);
    }

    public CompletableFuture<ApiResponse> sendLike(long userId, int times) {
        return call("send_like", "user_id", userId, "times", times);
    }

    // ================================================================
    // 群管理
    // ================================================================

    public CompletableFuture<ApiResponse> setGroupKick(long groupId, long userId, boolean rejectAddRequest) {
        return call("set_group_kick", "group_id", groupId, "user_id", userId, "reject_add_request", rejectAddRequest);
    }

    public CompletableFuture<ApiResponse> setGroupBan(long groupId, long userId, int duration) {
        return call("set_group_ban", "group_id", groupId, "user_id", userId, "duration", duration);
    }

    public CompletableFuture<ApiResponse> setGroupWholeBan(long groupId, boolean enable) {
        return call("set_group_whole_ban", "group_id", groupId, "enable", enable);
    }

    public CompletableFuture<ApiResponse> setGroupAdmin(long groupId, long userId, boolean enable) {
        return call("set_group_admin", "group_id", groupId, "user_id", userId, "enable", enable);
    }

    public CompletableFuture<ApiResponse> setGroupCard(long groupId, long userId, String card) {
        return call("set_group_card", "group_id", groupId, "user_id", userId, "card", card);
    }

    public CompletableFuture<ApiResponse> setGroupName(long groupId, String groupName) {
        return call("set_group_name", "group_id", groupId, "group_name", groupName);
    }

    public CompletableFuture<ApiResponse> setGroupLeave(long groupId, boolean isDismiss) {
        return call("set_group_leave", "group_id", groupId, "is_dismiss", isDismiss);
    }

    public CompletableFuture<ApiResponse> setGroupSpecialTitle(long groupId, long userId, String specialTitle) {
        return call("set_group_special_title", "group_id", groupId, "user_id", userId, "special_title", specialTitle);
    }

    public CompletableFuture<ApiResponse> setGroupPortrait(long groupId, String file) {
        return call("set_group_portrait", "group_id", groupId, "file", file);
    }

    // ================================================================
    // 请求处理
    // ================================================================

    public CompletableFuture<ApiResponse> setFriendAddRequest(String flag, boolean approve, String remark) {
        return call("set_friend_add_request", "flag", flag, "approve", approve, "remark", remark);
    }

    public CompletableFuture<ApiResponse> setGroupAddRequest(String flag, String subType, boolean approve, String reason) {
        return call("set_group_add_request", "flag", flag, "sub_type", subType, "approve", approve, "reason", reason);
    }

    // ================================================================
    // 账号信息
    // ================================================================

    public CompletableFuture<ApiResponse> getLoginInfo() {
        return call("get_login_info");
    }

    public CompletableFuture<ApiResponse> getStrangerInfo(long userId, boolean noCache) {
        return call("get_stranger_info", "user_id", userId, "no_cache", noCache);
    }

    public CompletableFuture<ApiResponse> getFriendList() {
        return call("get_friend_list");
    }

    // ================================================================
    // 群信息
    // ================================================================

    public CompletableFuture<ApiResponse> getGroupInfo(long groupId, boolean noCache) {
        return call("get_group_info", "group_id", groupId, "no_cache", noCache);
    }

    public CompletableFuture<ApiResponse> getGroupList() {
        return call("get_group_list");
    }

    public CompletableFuture<ApiResponse> getGroupMemberInfo(long groupId, long userId, boolean noCache) {
        return call("get_group_member_info", "group_id", groupId, "user_id", userId, "no_cache", noCache);
    }

    public CompletableFuture<ApiResponse> getGroupMemberList(long groupId) {
        return call("get_group_member_list", "group_id", groupId);
    }

    public CompletableFuture<ApiResponse> getGroupHonorInfo(long groupId, String type) {
        return call("get_group_honor_info", "group_id", groupId, "type", type);
    }

    // ================================================================
    // 系统
    // ================================================================

    public CompletableFuture<ApiResponse> getCookies(String domain) {
        return call("get_cookies", "domain", domain);
    }

    public CompletableFuture<ApiResponse> getCsrfToken() {
        return call("get_csrf_token");
    }

    public CompletableFuture<ApiResponse> getCredentials(String domain) {
        return call("get_credentials", "domain", domain);
    }

    public CompletableFuture<ApiResponse> getRecord(String file, String outFormat) {
        return call("get_record", "file", file, "out_format", outFormat);
    }

    public CompletableFuture<ApiResponse> getImage(String file) {
        return call("get_image", "file", file);
    }

    public CompletableFuture<ApiResponse> canSendImage() {
        return call("can_send_image");
    }

    public CompletableFuture<ApiResponse> canSendRecord() {
        return call("can_send_record");
    }

    public CompletableFuture<ApiResponse> getStatus() {
        return call("get_status");
    }

    public CompletableFuture<ApiResponse> getVersionInfo() {
        return call("get_version_info");
    }

    public CompletableFuture<ApiResponse> setRestart() {
        return call("set_restart");
    }

    public CompletableFuture<ApiResponse> cleanCache() {
        return call("clean_cache");
    }

    // ================================================================
    // NapCat 扩展 API
    // ================================================================

    public CompletableFuture<ApiResponse> setEssenceMessage(int messageId) {
        return call("set_essence_msg", "message_id", messageId);
    }

    public CompletableFuture<ApiResponse> deleteEssenceMessage(int messageId) {
        return call("delete_essence_msg", "message_id", messageId);
    }

    public CompletableFuture<ApiResponse> getEssenceMessageList(long groupId) {
        return call("get_essence_msg_list", "group_id", groupId);
    }

    public CompletableFuture<ApiResponse> sendGroupNotice(long groupId, String content) {
        return call("_send_group_notice", "group_id", groupId, "content", content);
    }

    public CompletableFuture<ApiResponse> getGroupNotice(long groupId) {
        return call("_get_group_notice", "group_id", groupId);
    }

    public CompletableFuture<ApiResponse> uploadGroupFile(long groupId, String file, String name, String folder) {
        return call("upload_group_file", "group_id", groupId, "file", file, "name", name, "folder", folder);
    }

    public CompletableFuture<ApiResponse> deleteGroupFile(long groupId, String fileId, String busid) {
        return call("delete_group_file", "group_id", groupId, "file_id", fileId, "busid", busid);
    }

    public CompletableFuture<ApiResponse> getGroupFileSystemInfo(long groupId) {
        return call("get_group_file_system_info", "group_id", groupId);
    }

    public CompletableFuture<ApiResponse> getGroupRootFiles(long groupId) {
        return call("get_group_root_files", "group_id", groupId);
    }

    public CompletableFuture<ApiResponse> getGroupFilesByFolder(long groupId, String folderId) {
        return call("get_group_files_by_folder", "group_id", groupId, "folder_id", folderId);
    }

    public CompletableFuture<ApiResponse> setSelfPortrait(String file) {
        return call("set_self_longnick", "file", file);
    }

    public CompletableFuture<ApiResponse> getGuildList() {
        return call("get_guild_list");
    }

    public CompletableFuture<ApiResponse> getGuildMemberList(String guildId) {
        return call("get_guild_member_list", "guild_id", guildId);
    }

    public CompletableFuture<ApiResponse> sendGuildMessage(String guildId, String channelId, Object message) {
        return call("send_guild_channel_msg",
                "guild_id", guildId, "channel_id", channelId, "message", toMessageParam(message));
    }

    public CompletableFuture<ApiResponse> getAiVoice(String characterId, String text) {
        return call("get_ai_voice", "character_id", characterId, "text", text);
    }

    public CompletableFuture<ApiResponse> getAiRecord(String characterId, String text) {
        return call("get_ai_record", "character_id", characterId, "text", text);
    }

    public CompletableFuture<ApiResponse> ocrImage(String image) {
        return call("ocr_image", "image", image);
    }

    public CompletableFuture<ApiResponse> translate(String text, String sourceLang, String targetLang) {
        return call("translate", "text", text, "source_lang", sourceLang, "target_lang", targetLang);
    }

    public CompletableFuture<ApiResponse> arkSharePeer(String id, String peerId, int sceneType) {
        return call("ArkSharePeer", "id", id, "peerId", peerId, "sceneType", sceneType);
    }

    // ================================================================
    // 内部
    // ================================================================

    private Object toMessageParam(Object message) {
        if (message instanceof MessageChain) {
            return message;
        }
        if (message instanceof String) {
            return MessageChain.ofText((String) message);
        }
        return message;
    }

    /** 关闭清理线程，应在应用 shutdown 时调用 */
    public void shutdown() {
        cleaner.shutdown();
    }

    private static class PendingRequest {
        final CompletableFuture<ApiResponse> future;
        final long timeoutMs;
        final long createdAt;

        PendingRequest(CompletableFuture<ApiResponse> future, long timeoutMs) {
            this.future = future;
            this.timeoutMs = timeoutMs;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
