package com.napcat.core.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventDecoder {

    private final ObjectMapper mapper;

    public EventDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public OB11Event decode(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String postType = getString(root, "post_type");
            if (postType == null) {
                // 可能是 API 响应，不是事件
                return null;
            }

            return switch (postType) {
                case "message" -> decodeMessage(root);
                case "notice" -> decodeNotice(root);
                case "request" -> decodeRequest(root);
                case "meta_event" -> decodeMeta(root);
                default -> mapper.treeToValue(root, OB11Event.class);
            };
        } catch (Exception e) {
            log.error("Failed to decode event: {}", json, e);
            return null;
        }
    }

    private OB11Event decodeMessage(JsonNode root) throws Exception {
        String messageType = getString(root, "message_type");
        if ("group".equals(messageType)) {
            return mapper.treeToValue(root, GroupMessageEvent.class);
        } else if ("private".equals(messageType)) {
            return mapper.treeToValue(root, PrivateMessageEvent.class);
        }
        return mapper.treeToValue(root, MessageEvent.class);
    }

    private OB11Event decodeNotice(JsonNode root) throws Exception {
        String noticeType = getString(root, "notice_type");
        return switch (noticeType) {
            case "group_increase" -> mapper.treeToValue(root, GroupIncreaseEvent.class);
            case "group_decrease" -> mapper.treeToValue(root, GroupDecreaseEvent.class);
            case "group_admin" -> mapper.treeToValue(root, GroupAdminEvent.class);
            case "group_ban" -> mapper.treeToValue(root, GroupBanEvent.class);
            case "friend_add" -> mapper.treeToValue(root, FriendAddEvent.class);
            case "group_recall" -> mapper.treeToValue(root, GroupRecallEvent.class);
            case "friend_recall" -> mapper.treeToValue(root, FriendRecallEvent.class);
            default -> mapper.treeToValue(root, NoticeEvent.class);
        };
    }

    private OB11Event decodeRequest(JsonNode root) throws Exception {
        String requestType = getString(root, "request_type");
        if ("friend".equals(requestType)) {
            return mapper.treeToValue(root, FriendRequestEvent.class);
        } else if ("group".equals(requestType)) {
            return mapper.treeToValue(root, GroupRequestEvent.class);
        }
        return mapper.treeToValue(root, RequestEvent.class);
    }

    private OB11Event decodeMeta(JsonNode root) throws Exception {
        String metaType = getString(root, "meta_event_type");
        if ("lifecycle".equals(metaType)) {
            return mapper.treeToValue(root, LifecycleEvent.class);
        } else if ("heartbeat".equals(metaType)) {
            return mapper.treeToValue(root, HeartbeatEvent.class);
        }
        return mapper.treeToValue(root, MetaEvent.class);
    }

    private String getString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null ? value.asText() : null;
    }
}
