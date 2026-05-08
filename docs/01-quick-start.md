# 快速开始

基于 NapCat OneBot11 协议的 Java Bot 开发框架，集成 AI Agent 能力。

---

## 前置要求

- JDK 17+
- Maven 3.8+
- 已部署并配置好的 NapCat（[安装指南](https://napneko.github.io/guide/start-install)）

## 创建项目

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.napcat</groupId>
    <artifactId>napcat-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

如需 Agent 能力，再添加一个 LLM Provider：

```xml
<!-- OpenAI 协议兼容（含 DeepSeek、通义千问等） -->
<dependency>
    <groupId>com.napcat</groupId>
    <artifactId>napcat-llm-openai</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 或 Anthropic Claude -->
<dependency>
    <groupId>com.napcat</groupId>
    <artifactId>napcat-llm-anthropic</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 或 Ollama 本地模型 -->
<dependency>
    <groupId>com.napcat</groupId>
    <artifactId>napcat-llm-ollama</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置 NapCat 连接

`application.yml`：

```yaml
napcat:
  adapter:
    type: websocket-client
    websocket-client:
      url: ws://127.0.0.1:3001
      token: ""
  bot:
    self-id: 123456789
```

### 3. 编写第一个 Bot（注解式）

```java
package com.example.bot;

import com.napcat.core.annotation.Command;
import com.napcat.core.annotation.OnGroupMessage;
import com.napcat.core.annotation.OnPrivateMessage;
import com.napcat.core.annotation.Param;
import com.napcat.core.event.GroupMessageEvent;
import com.napcat.core.event.PrivateMessageEvent;
import com.napcat.core.message.MessageChain;
import org.springframework.stereotype.Component;

@Component
public class HelloBot {

    @OnGroupMessage
    @Command("/hello")
    public void hello(GroupMessageEvent event) {
        event.reply("Hello NapCat!");
    }

    @OnGroupMessage
    @Command("/天气 {city}")
    public void weather(GroupMessageEvent event, @Param("city") String city) {
        event.reply("查询 " + city + " 的天气：晴 25°C");
    }

    @OnGroupMessage
    public void onGroup(GroupMessageEvent event) {
        if (event.getRawMessage().contains("在吗")) {
            event.reply("在的！");
        }
    }

    @OnPrivateMessage
    public void onPrivate(PrivateMessageEvent event) {
        event.reply("私聊收到：" + event.getPlainText());
    }

    @OnGroupMessage
    @Command("/图片")
    public MessageChain image() {
        return MessageChain.ofText("给你一张图：").image("https://picsum.photos/200");
    }
}
```

### 4. 编写第一个 Bot（接口式）

```java
package com.example.bot;

import com.napcat.core.event.MessageEvent;
import com.napcat.core.handler.CommandHandler;
import org.springframework.stereotype.Component;

@Component
public class WeatherCommand implements CommandHandler {

    @Override
    public String getCommand() {
        return "/接口天气 {city}";
    }

    @Override
    public void handle(MessageEvent event, CommandArgs args) {
        String city = args.get("city");
        event.reply("【接口方式】" + city + " 天气晴朗");
    }
}
```

### 5. 启用 AI Agent

```yaml
napcat:
  agent:
    enabled: true
    max-react-rounds: 5
  llm:
    provider: openai
    openai:
      base-url: https://api.openai.com/v1
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
```

```java
package com.example.bot;

import com.napcat.agent.agent.NapCatAgent;
import com.napcat.core.annotation.MentionFilter;
import com.napcat.core.annotation.OnGroupMessage;
import com.napcat.core.event.GroupMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentBot {

    @Autowired
    private NapCatAgent agent;

    @OnGroupMessage
    @MentionFilter
    public void onAt(GroupMessageEvent event) {
        agent.chat(event.getUserId(), event.getGroupId(), event.getMessage().toPlainText())
            .thenAccept(event::reply);
    }
}
```

配置 `napcat.bot.at-me-trigger: true` 后，被 @ 时会自动走 Agent 流程，无需额外写 Handler。

---

## 模块架构

```
napcat-java/
├── napcat-parent                  # BOM，统一依赖版本
├── napcat-core                    # OneBot11 协议、通信适配器、事件路由
├── napcat-agent                   # LLM Agent 引擎、Tool 注册、ReAct 循环
├── napcat-llm-providers           # LLM 厂商实现
│   ├── napcat-llm-openai          # OpenAI 协议兼容
│   ├── napcat-llm-anthropic       # Claude
│   └── napcat-llm-ollama          # Ollama 本地模型
├── napcat-spring-boot-starter     # Spring Boot 自动配置
└── napcat-admin                   # 示例机器人应用
```
