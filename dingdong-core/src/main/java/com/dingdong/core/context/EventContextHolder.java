package com.dingdong.core.context;

public class EventContextHolder {

    private static final ThreadLocal<EventContext> CONTEXT = new ThreadLocal<>();

    public static void set(EventContext ctx) {
        CONTEXT.set(ctx);
    }

    public static EventContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
