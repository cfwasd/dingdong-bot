package com.napcat.core.annotation;

import com.napcat.core.event.NoticeEvent;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnNotice {
    Class<? extends NoticeEvent> value() default NoticeEvent.class;
    /** 优先级，数值越小优先级越高。默认 100。 */
    int priority() default 100;
}
