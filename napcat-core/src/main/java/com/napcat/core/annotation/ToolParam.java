package com.napcat.core.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolParam {
    String description();
    boolean required() default false;
    String[] enums() default {};
    String type() default "string";
}
