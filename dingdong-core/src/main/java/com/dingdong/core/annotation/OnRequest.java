package com.dingdong.core.annotation;

import com.dingdong.core.event.RequestEvent;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnRequest {
    Class<? extends RequestEvent> value() default RequestEvent.class;
    /** 优先级，数值越小优先级越高。默认 100。 */
    int priority() default 100;
}
