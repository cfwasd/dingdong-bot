# 通信适配器指南

本文档详细说明框架与 NapCat 的四种通信方式，以及如何选择和配置。

---

## 一、四种通信方式对比

| 维度 | WS Client | WS Server | HTTP Client | HTTP Server |
|------|-----------|-----------|-------------|-------------|
| 连接方向 | Bot → NapCat | NapCat → Bot | Bot → NapCat | NapCat → Bot |
| 双工通信 | ✅ | ✅ | ❌ | ❌ |
| 事件推送 | 实时推送 | 实时推送 | 需额外配置 | 实时推送 |
| API 调用 | 通过 WS | 通过 WS | 直接 HTTP | 需反向 HTTP Client |
| 多 NapCat 支持 | 每个 NapCat 一个连接 | 一个端口等多连接 | 每个 NapCat 一个配置 | 一个端口接收多上报 |
| 推荐场景 | 单/多 NapCat，一般推荐 | 中心化 Bot 服务 | 简单轮询场景 | Webhook 风格部署 |

---

## 二、WebSocket Client（推荐）

Bot 主动连接 NapCat 的 WebSocket Server，建立长连接后双向收发。

### 2.1 配置

**NapCat 侧：** `onebot11.json`

```json
{
  "network": {
    "websocketServers": [{
      "name": "WsServer",
      "enable": true,
      "host": "0.0.0.0",
      "port": 3001,
      "messagePostFormat": "array",
      "token": "",
      "debug": false
    }]
  }
}
```

**Bot 侧：** `application.yml`

```yaml
napcat:
  adapter:
    type: websocket-client
    websocket-client:
      url: ws://127.0.0.1:3001
      token: ""
      reconnect-interval: 5000
      heart-interval: 30000
```

### 2.2 多 NapCat 实例

只需注册多个 `WsClientAdapter` Bean：

```java
@Configuration
public class MultiBotConfig {

    @Bean
    public WsClientAdapter bot1() {
        return new WsClientAdapter("ws://napcat1:3001", "");
    }

    @Bean
    public WsClientAdapter bot2() {
        return new WsClientAdapter("ws://napcat2:3001", "");
    }
}
```

框架会自动将两个连接的事件都汇入同一路由表，事件中的 `selfId` 字段可区分来源。

### 2.3 生命周期回调

```java
@Component
public class WsListener implements BotAdapterListener {

    @Override
    public void onOpen(BotAdapter adapter) {
        System.out.println("连接成功：" + adapter.getId());
    }

    @Override
    public void onClose(BotAdapter adapter, int code, String reason) {
        System.out.println("连接关闭：" + reason);
    }

    @Override
    public void onError(BotAdapter adapter, Throwable ex) {
        System.err.println("连接异常：" + ex.getMessage());
    }

    @Override
    public void onReconnect(BotAdapter adapter, int attempt) {
        System.out.println("第 " + attempt + " 次重连...");
    }
}
```

---

## 三、WebSocket Server

Bot 开启 WebSocket Server，等待 NapCat 主动连接。

### 3.1 配置

**NapCat 侧：** `onebot11.json`

```json
{
  "network": {
    "websocketClients": [{
      "name": "WsClient",
      "enable": true,
      "url": "ws://bot-server:3001",
      "messagePostFormat": "array",
      "token": "",
      "reconnectInterval": 5000
    }]
  }
}
```

**Bot 侧：** `application.yml`

```yaml
napcat:
  adapter:
    type: websocket-server
    websocket-server:
      host: 0.0.0.0
      port: 3001
      token: ""
```

### 3.2 适用场景

- 中心化 Bot 服务：多个 NapCat 实例（不同群/号）连接同一个 Bot 后端
- 容器/K8s 环境：Bot 服务有固定域名，NapCat 动态连接

---

## 四、HTTP Client

Bot 主动发送 HTTP 请求调用 NapCat API。事件接收需配合 NapCat 的 HTTP Client 上报或其他机制。

### 4.1 配置

**NapCat 侧：** `onebot11.json`

```json
{
  "network": {
    "httpServers": [{
      "name": "httpServer",
      "enable": true,
      "port": 3000,
      "host": "0.0.0.0",
      "messagePostFormat": "array"
    }]
  }
}
```

**Bot 侧：** `application.yml`

```yaml
napcat:
  adapter:
    type: http-client
    http-client:
      url: http://127.0.0.1:3000
      token: ""
      timeout: 30000
```

### 4.2 事件接收问题

纯 HTTP Client 模式下，Bot 无法被动接收事件。解决方案：

**方案 A：** 同时启用 HTTP Server 接收上报

```yaml
napcat:
  adapter:
    type: http-server
    http-server:
      port: 8080
      path: /webhook
```

并在 NapCat 中配置 HTTP Client 上报到 `http://bot-server:8080/webhook`。

**方案 B：** 使用 `get_updates` 风格的轮询（框架暂不提供，需自行实现）

### 4.3 适用场景

- 无 WS 支持的受限网络环境
- 仅需主动发消息，不需要接收事件的推送服务

---

## 五、HTTP Server

Bot 开启 HTTP Server，被动接收 NapCat 的 HTTP 上报。

### 5.1 配置

**NapCat 侧：** `onebot11.json`

```json
{
  "network": {
    "httpClients": [{
      "name": "httpClient",
      "enable": true,
      "url": "http://bot-server:8080/napcat/webhook",
      "messagePostFormat": "array"
    }]
  }
}
```

**Bot 侧：** `application.yml`

```yaml
napcat:
  adapter:
    type: http-server
    http-server:
      host: 0.0.0.0
      port: 8080
      path: /napcat/webhook
      token: ""
```

### 5.2 API 调用问题

纯 HTTP Server 模式下，Bot 无法主动调用 NapCat API。解决方案：

**方案 A：** 同时启用 HTTP Client

```yaml
napcat:
  adapter:
    type: http-client
    http-client:
      url: http://127.0.0.1:3000
```

**方案 B：** NapCat 侧同时开启 HTTP Server，Bot 侧同时配置 HTTP Client

### 5.3 适用场景

- Webhook 风格的微服务架构
- 函数计算/Serverless 环境（冷启动快）
- 已有网关统一接收外部推送

---

## 六、混合模式

框架支持一个 Bot 服务同时运行多个适配器：

```java
@Configuration
public class HybridAdapterConfig {

    @Bean
    public BotAdapter wsAdapter() {
        return new WsClientAdapter("ws://napcat:3001", "");
    }

    @Bean
    public BotAdapter httpAdapter() {
        return new HttpClientAdapter("http://napcat:3000", "");
    }
}
```

事件从所有适配器汇入同一路由表，API 调用默认使用第一个注册的适配器，也可显式指定：

```java
@Autowired
private NapCatApi api;

public void sendViaWs() {
    api.withAdapter("wsAdapter").sendGroupMessage(123L, "msg");
}
```

---

## 七、适配器抽象接口

框架内部所有适配器实现 `BotAdapter`：

```java
public interface BotAdapter {
    String getId();
    void start();
    void stop();
    boolean isConnected();
    void sendApiRequest(ApiRequest<?> request, Consumer<ApiResponse> callback);
    void setEventConsumer(Consumer<OB11Event> consumer);
}
```

自定义适配器只需实现该接口并注册为 Spring Bean 即可接入框架。
