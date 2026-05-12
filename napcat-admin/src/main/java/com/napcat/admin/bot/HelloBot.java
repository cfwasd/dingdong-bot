package com.napcat.admin.bot;

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
    @Command(value = "/hello", description = "打招呼")
    public void hello(GroupMessageEvent event) {
        event.reply("Hello NapCat!");
    }

    @OnGroupMessage
    @Command(value = "/天气 {city}", description = "查询指定城市天气")
    public void weather(GroupMessageEvent event, @Param("city") String city) {
        event.reply("查询 " + city + " 的天气：晴 25°C");
    }

    @OnGroupMessage
    @Command(value = "/图片", description = "发送一张随机图片")
    public MessageChain image() {
        return MessageChain.ofText("给你一张图：").image("https://picsum.photos/200");
    }
}
