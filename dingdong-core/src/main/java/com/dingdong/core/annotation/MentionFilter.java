package com.dingdong.core.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MentionFilter {
    /** 优先级，数值越小优先级越高。默认 10。 */
    int priority() default 10;
}
