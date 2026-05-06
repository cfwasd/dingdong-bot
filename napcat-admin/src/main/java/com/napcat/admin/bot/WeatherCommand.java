package com.napcat.admin.bot;

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
