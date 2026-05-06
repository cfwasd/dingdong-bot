package com.napcat.starter.config;

import com.napcat.core.handler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;

@Slf4j
@RequiredArgsConstructor
public class NapCatBeanPostProcessor implements BeanPostProcessor {

    private final HandlerRegistry registry;
    private final ApplicationContext ctx;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 注册注解 handler
        registry.registerBean(bean);

        // 注册接口 handler
        if (bean instanceof EventHandler<?> eh) {
            registry.registerEventHandler(eh);
        }
        if (bean instanceof CommandHandler ch) {
            registry.registerCommandHandler(ch);
        }
        if (bean instanceof BotInitializer bi) {
            registry.registerInitializer(bi);
        }

        return bean;
    }
}
