package com.napcat.core.handler;

import com.napcat.core.event.MessageEvent;

import java.util.Map;

public interface CommandHandler {
    String getCommand();
    void handle(MessageEvent event, CommandArgs args);

    interface FilterableCommandHandler extends CommandHandler {
        default boolean filter(MessageEvent event) {
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
