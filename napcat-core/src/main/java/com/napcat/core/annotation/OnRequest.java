package com.napcat.core.annotation;

import com.napcat.core.event.RequestEvent;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnRequest {
    Class<? extends RequestEvent> value() default RequestEvent.class;
}
