package com.dingdong.channel.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 渠道限制注解。
 * 标记命令/工具只能在指定渠道使用。
 * 空数组 = 全部渠道可用。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ChannelRestrict {
    /** 允许的渠道标识数组，如 {"onebot", "qqofficial"} */
    String[] value() default {};
}
