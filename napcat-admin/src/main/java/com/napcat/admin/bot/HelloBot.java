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
        if (event.getMessage().contains("在吗")) {
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
