package com.napcat.core.annotation;

import java.lang.annotation.*;

/**
 * 关键词唤醒过滤器。
 * 标注在 handler 方法上时，仅当消息文本包含配置的任一唤醒词时才触发。
 * <p>
 * 唤醒词列表在 {@code napcat.bot.wake-words} 中配置，默认包含 ["机器人", "bot"]。
 * 与 {@link MentionFilter} 互斥时取 OR 语义（满足任一即触发）。
 *
 * <pre>{@code
 * @OnGroupMessage
 * @WakeFilter
 * public void onWake(GroupMessageEvent event) {
 *     event.reply("我在！");
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WakeFilter {
    /** 优先级，数值越小优先级越高。默认 10。 */
    int priority() default 10;
}