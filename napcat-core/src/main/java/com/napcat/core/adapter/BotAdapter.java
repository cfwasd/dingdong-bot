package com.napcat.core.adapter;

import com.napcat.core.api.ApiRequest;
import com.napcat.core.api.ApiResponse;

import java.util.function.Consumer;

/**
 * 通信适配器抽象接口。
 * 
 * 消息流向：
 * <pre>
 * 网络 → adapter → messageHandler (MessageRouter) → EventDispatcher / NapCatApi
 * NapCatApi → adapter.sendApiRequest() → 网络
 * </pre>
 */
public interface BotAdapter {

    /** 适配器唯一标识 */
    String getId();

    /** 启动适配器，建立连接 */
    void start();

    /** 停止适配器，释放资源 */
    void stop();

    /** 是否处于连接状态 */
    boolean isConnected();

    /**
     * 发送 API 请求。
     * 响应通过 {@link #setMessageHandler} 设置的路由器异步返回。
     * 对于 HTTP Client 模式，响应在 HTTP 回调中直接送达 messageHandler。
     */
    void sendApiRequest(ApiRequest<?> request);

    /**
     * 设置消息处理器。所有从网络接收的原始 JSON 字符串均通过此 handler 分发。
     * 适配器本身不关心消息是事件还是 API 响应——交由 MessageRouter 统一判定。
     */
    void setMessageHandler(Consumer<String> handler);
}
