package com.dingdong.core.handler;

import com.dingdong.channel.api.ChannelEvent;

import java.util.Map;

public interface CommandHandler {
    String getCommand();
    void handle(ChannelEvent event, CommandArgs args);

    interface FilterableCommandHandler extends CommandHandler {
        default boolean filter(ChannelEvent event) {
            return true;
        }
    }

    class CommandArgs {
        private final Map<String, String> args;

        public CommandArgs(Map<String, String> args) {
            this.args = args;
        }

        public String get(String key) {
            return args.get(key);
        }

        public int getInt(String key) {
            String v = args.get(key);
            return v == null ? 0 : Integer.parseInt(v);
        }

        public long getLong(String key) {
            String v = args.get(key);
            return v == null ? 0 : Long.parseLong(v);
        }

        public boolean getBoolean(String key) {
            String v = args.get(key);
            return v != null && ("true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v));
        }

        public boolean contains(String key) {
            return args.containsKey(key);
        }
    }
}
