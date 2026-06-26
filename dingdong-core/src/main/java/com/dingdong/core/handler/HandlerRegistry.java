package com.dingdong.core.handler;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.channel.api.annotation.ChannelRestrict;
import com.dingdong.core.annotation.*;
import com.dingdong.core.config.BotProperties;
import com.dingdong.core.event.*;
import com.dingdong.core.exception.StopRoutingException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class HandlerRegistry implements BotDispatcher {

    private final BotProperties properties;
    private final List<HandlerEntry<?>> handlers = new ArrayList<>();
    private final Map<String, List<CommandEntry>> commands = new ConcurrentHashMap<>();
    private final Map<Class<? extends ChannelEvent>, List<Consumer<ChannelEvent>>> eventHandlers = new ConcurrentHashMap<>();
    private final List<CommandHelp> commandHelps = new ArrayList<>();

    /** 当事件未被任何 handler 处理时的兜底回调（用于 at-me-trigger 等场景） */
    private Consumer<ChannelEvent> fallbackHandler;

    /** 安静模式检查器：接受 groupId，返回 true 表示该群处于安静模式 */
    private java.util.function.LongPredicate silentModeChecker = groupId -> false;

    public HandlerRegistry(BotProperties properties) {
        this.properties = properties;
    }

    /**
     * 设置兜底 handler。当事件经过命令/注解/接口全部匹配后无任何 handler 命中时调用。
     */
    public void setFallbackHandler(Consumer<ChannelEvent> fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
    }

    public Consumer<ChannelEvent> getFallbackHandler() {
        return fallbackHandler;
    }

    /**
     * 设置安静模式检查器。
     * @param checker 接受 groupId，返回 true 表示该群处于安静模式
     */
    public void setSilentModeChecker(java.util.function.LongPredicate checker) {
        this.silentModeChecker = checker != null ? checker : (groupId -> false);
    }

    @Override
    public void onGroupMessage(Consumer<GroupMessageEvent> handler) {
        eventHandlers.computeIfAbsent(GroupMessageEvent.class, k -> new ArrayList<>())
                .add(e -> handler.accept((GroupMessageEvent) e));
    }

    @Override
    public void onPrivateMessage(Consumer<PrivateMessageEvent> handler) {
        eventHandlers.computeIfAbsent(PrivateMessageEvent.class, k -> new ArrayList<>())
                .add(e -> handler.accept((PrivateMessageEvent) e));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEvent(Class<? extends ChannelEvent> type, Consumer<ChannelEvent> handler) {
        eventHandlers.computeIfAbsent((Class<? extends ChannelEvent>) type, k -> new ArrayList<>())
                .add(handler);
    }

    @Override
    public void registerCommand(String template, BiConsumer<ChannelEvent, CommandHandler.CommandArgs> handler) {
        registerCommand(template, handler, e -> true);
    }

    @Override
    public void registerCommand(String template, BiConsumer<ChannelEvent, CommandHandler.CommandArgs> handler, Predicate<ChannelEvent> filter) {
        commands.computeIfAbsent(template, k -> new ArrayList<>())
                .add(new CommandEntry(template, handler, filter, null, false, new String[0]));
    }

    public void registerBean(Object bean) {
        registerBean(bean, bean.getClass());
    }

    public void registerBean(Object bean, Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            registerAnnotatedMethod(bean, method);
        }
    }

    @SuppressWarnings("unchecked")
    public void registerEventHandler(EventHandler<?> handler) {
        Class<?> eventType = handler.getEventType();
        onEvent((Class<? extends ChannelEvent>) eventType, e -> {
            try {
                ((EventHandler<ChannelEvent>) handler).handle(e);
            } catch (Exception ex) {
                log.error("Event handler error", ex);
            }
        });
    }

    public void registerCommandHandler(CommandHandler handler) {
        registerCommand(handler.getCommand(), (event, args) -> {
            if (handler instanceof CommandHandler.FilterableCommandHandler) {
                if (!((CommandHandler.FilterableCommandHandler) handler).filter(event)) {
                    return;
                }
            }
            handler.handle(event, args);
        });
    }

    public void registerInitializer(BotInitializer initializer) {
        initializer.initialize(this);
    }

    private void registerAnnotatedMethod(Object bean, Method method) {
        // 收集所有注解（包括元注解）
        Set<Annotation> annotations = getMergedAnnotations(method);

        boolean hasGroupMessage = annotations.stream().anyMatch(a -> a instanceof OnGroupMessage);
        boolean hasPrivateMessage = annotations.stream().anyMatch(a -> a instanceof OnPrivateMessage);
        boolean hasNotice = annotations.stream().anyMatch(a -> a instanceof OnNotice);
        boolean hasRequest = annotations.stream().anyMatch(a -> a instanceof OnRequest);
        boolean hasMeta = annotations.stream().anyMatch(a -> a instanceof OnMetaEvent);

        // 收集所有 @Command 注解（支持 @Repeatable 多命令别名）
        List<Command> commandList = new ArrayList<>();
        for (Annotation ann : method.getAnnotations()) {
            if (ann instanceof Command cmd) {
                commandList.add(cmd);
            } else if (ann instanceof Commands container) {
                commandList.addAll(java.util.Arrays.asList(container.value()));
            }
        }

        boolean hasMention = annotations.stream().anyMatch(a -> a instanceof MentionFilter);

        if (!hasGroupMessage && !hasPrivateMessage && !hasNotice && !hasRequest && !hasMeta) {
            return;
        }

        method.setAccessible(true);

        if (!commandList.isEmpty()) {
            Predicate<Object> eventFilter = createEventFilter(annotations);
            Predicate<ChannelEvent> msgFilter = eventFilter == null ? null :
                    eventFilter::test;
            Class<? extends ChannelEvent> eventType = null;
            if (hasGroupMessage && !hasPrivateMessage) eventType = GroupMessageEvent.class;
            if (hasPrivateMessage && !hasGroupMessage) eventType = PrivateMessageEvent.class;
            for (Command cmd : commandList) {
                String template = properties.getCommandPrefix() + cmd.value();
                CommandEntry entry = new CommandEntry(template,
                        (event, args) -> invokeMethod(bean, method, event, args),
                        msgFilter, eventType, cmd.silentModeAllowed(), cmd.channels());
                commands.computeIfAbsent(template, k -> new ArrayList<>()).add(entry);
                commandHelps.add(new CommandHelp(template, cmd.description(), cmd.adminOnly(), cmd.channels()));
                log.debug("Registered command handler: {} on method {} type={} silentModeAllowed={}",
                        template, method.getName(), eventType, cmd.silentModeAllowed());
            }
            return;
        }

        int priority = calculatePriority(annotations);
        Predicate<Object> condition = createEventFilter(annotations);

        if (hasGroupMessage) {
            handlers.add(new HandlerEntry<>(GroupMessageEvent.class, priority, condition, e -> invokeMethod(bean, method, e), method));
        }
        if (hasPrivateMessage) {
            handlers.add(new HandlerEntry<>(PrivateMessageEvent.class, priority, condition, e -> invokeMethod(bean, method, e), method));
        }
    }

    private void invokeMethod(Object bean, Method method, ChannelEvent event, CommandHandler.CommandArgs args) {
        try {
            Parameter[] params = method.getParameters();
            Object[] argsArray = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];
                Class<?> paramType = param.getType();

                if (ChannelEvent.class.isAssignableFrom(paramType)) {
                    if (paramType.isInstance(event)) {
                        argsArray[i] = event;
                    } else {
                        log.warn("Event type mismatch: expected {}, got {}. Setting to null.",
                                paramType.getSimpleName(), event.getClass().getSimpleName());
                        argsArray[i] = null;
                    }
                } else if (CommandHandler.CommandArgs.class.isAssignableFrom(paramType)) {
                    argsArray[i] = args;
                } else if (param.isAnnotationPresent(Param.class)) {
                    String key = param.getAnnotation(Param.class).value();
                    String value = args.get(key);
                    try {
                        argsArray[i] = convertType(value, paramType);
                    } catch (Exception e) {
                        log.error("Failed to convert parameter '{}' with value '{}' to type {}: {}",
                                key, value, paramType.getSimpleName(), e.getMessage());
                        argsArray[i] = null;
                    }
                } else {
                    argsArray[i] = null;
                }
            }
            Object result = method.invoke(bean, argsArray);
            handleReturnValue(result, event);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof StopRoutingException) {
                throw (StopRoutingException) cause;
            }
            log.error("Method invoke error: {}.{}", bean.getClass().getName(), method.getName(), ite);
        } catch (Exception e) {
            log.error("Method invoke error: {}.{}", bean.getClass().getName(), method.getName(), e);
        }
    }

    private void invokeMethod(Object bean, Method method, ChannelEvent event) {
        try {
            Parameter[] params = method.getParameters();
            Object[] argsArray = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];
                Class<?> paramType = param.getType();

                if (ChannelEvent.class.isAssignableFrom(paramType)) {
                    if (paramType.isInstance(event)) {
                        argsArray[i] = event;
                    } else {
                        log.warn("Event type mismatch: expected {}, got {}. Setting to null.",
                                paramType.getSimpleName(), event.getClass().getSimpleName());
                        argsArray[i] = null;
                    }
                } else {
                    argsArray[i] = null;
                }
            }
            Object result = method.invoke(bean, argsArray);
            handleReturnValue(result, event);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof StopRoutingException) {
                throw (StopRoutingException) cause;
            }
            log.error("Method invoke error: {}.{}", bean.getClass().getName(), method.getName(), ite);
        } catch (Exception e) {
            log.error("Method invoke error: {}.{}", bean.getClass().getName(), method.getName(), e);
        }
    }

    private void handleReturnValue(Object result, ChannelEvent event) {
        if (result == null) return;
        if (result instanceof String text) {
            log.info("[回复] -> {}", text.length() > 200 ? text.substring(0, 200) + "..." : text);
            if (event instanceof MessageEvent msgEvent) {
                msgEvent.reply(text);
            } else if (event instanceof com.dingdong.channel.api.ChannelMessageEvent chMsg
                    && chMsg.getApi() != null) {
                chMsg.getApi().reply(text);
            }
        } else if (result instanceof com.dingdong.core.message.MessageChain chain) {
            if (event instanceof MessageEvent msgEvent) {
                msgEvent.reply(chain);
            } else if (event instanceof com.dingdong.channel.api.ChannelMessageEvent chMsg
                    && chMsg.getApi() != null) {
                // 纯 Markdown 消息直接发送内容文本，避免 toAgentPrompt() 的语义化标记
                if (chain.size() == 1 && chain.get(0) instanceof com.dingdong.core.message.MarkdownSegment md) {
                    chMsg.getApi().reply(md.getContent());
                } else {
                    chMsg.getApi().reply(chain.toAgentPrompt());
                }
            }
        }
    }

    private Object convertType(String value, Class<?> type) {
        if (value == null) return null;
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse integer from '{}': {}", value, e.getMessage());
                return null;
            }
        }
        if (type == long.class || type == Long.class) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse long from '{}': {}", value, e.getMessage());
                return null;
            }
        }
        if (type == double.class || type == Double.class) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse double from '{}': {}", value, e.getMessage());
                return null;
            }
        }
        if (type == boolean.class || type == Boolean.class) {
            return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public List<HandlerResult> dispatch(ChannelEvent event) {
        List<HandlerResult> results = new ArrayList<>();

        // 只记录非心跳事件的调度信息，且使用 DEBUG 级别
        if (!(event instanceof com.dingdong.core.event.HeartbeatEvent)) {
            log.debug("Dispatching event: class={}, handlers={}, commands={}",
                    event.getClass().getSimpleName(), handlers.size(), commands.size());
        }

        // 0. 安静模式检测（仅群聊）
        boolean silentActive = false;
        long silentGroupId = 0;
        if (event instanceof GroupMessageEvent groupMsg) {
            silentGroupId = groupMsg.getGroupId();
            silentActive = silentModeChecker.test(silentGroupId);
            if (silentActive) {
                log.debug("群 [{}] 处于安静模式，仅允许 silentModeAllowed 命令", silentGroupId);
            }
        }

        // 1. 命令匹配（消息事件）
        // 支持 OneBot11 的 MessageEvent 和 QQ 官方的 ChannelMessageEvent
        String plainText = null;
        if (event instanceof MessageEvent msgEvent) {
            plainText = msgEvent.getPlainText().trim();
        } else if (event instanceof com.dingdong.channel.api.ChannelMessageEvent chMsg) {
            plainText = chMsg.getPlainText() != null ? chMsg.getPlainText().trim() : "";
        }

        if (plainText != null) {
            if (event instanceof GroupMessageEvent groupEvent) {
                String senderName = groupEvent.getSender() != null ? groupEvent.getSender().getNickname() : "未知";
                log.info("[群聊 {}] {}({}) -> {}", groupEvent.getGroupId(), senderName, groupEvent.getUserId(), plainText);
            } else if (event instanceof com.dingdong.core.event.PrivateMessageEvent privateEvent) {
                String senderName = privateEvent.getSender() != null ? privateEvent.getSender().getNickname() : "未知";
                log.info("[私聊] {}({}) -> {}", senderName, privateEvent.getUserId(), plainText);
            } else {
                log.info("[渠道消息 {}] {}", event.getChannelId(), plainText);
            }

            // 命令匹配逻辑
            boolean isGroupMsg = event instanceof GroupMessageEvent
                    || (event instanceof com.dingdong.channel.api.ChannelMessageEvent chMsg
                        && chMsg.getGroupId() != 0);
            boolean isAtOrWake = isAtOrWake(event);

            for (List<CommandEntry> entries : commands.values()) {
                for (CommandEntry entry : entries) {
                    CommandHandler.CommandArgs args = matchCommand(entry.template, plainText);
                    // 如果 plainText 不以命令前缀开头，尝试加上前缀再匹配
                    // 解决 QQ Official 等渠道去掉 @ 后只剩下 "修仙菜单" 而模板是 "/修仙菜单" 的情况
                    if (args == null && !plainText.startsWith(properties.getCommandPrefix())) {
                        args = matchCommand(entry.template, properties.getCommandPrefix() + plainText);
                    }
                    if (args != null) {
                        // 群聊场景：命令需要 @ 或唤醒词（/help 和 silentModeAllowed 命令除外）
                        if (isGroupMsg && !isAtOrWake
                                && !entry.isSilentModeAllowed()) {
                            log.debug("群聊命令需@或唤醒词: {}", entry.template);
                            continue;
                        }
                        // 安静模式下：跳过不允许的命令
                        if (silentActive && !entry.isSilentModeAllowed()) {
                            log.debug("安静模式下跳过命令: {}", entry.template);
                            continue;
                        }
                        // 渠道检查
                        if (!isChannelAllowed(entry, event)) {
                            log.debug("渠道限制跳过命令: {} (channel={})", entry.template, event.getChannelId());
                            continue;
                        }
                        log.info("命令匹配: 模板='{}', 消息='{}'", entry.template, plainText);
                        try {
                            if (event instanceof MessageEvent msgEvent) {
                                entry.handler.accept(msgEvent, args);
                            } else {
                                entry.handler.accept(event, args);
                            }
                            results.add(new HandlerResult(true, null));
                            event.setCommandHandled(true);
                        } catch (StopRoutingException sre) {
                            results.add(new HandlerResult(true, null));
                            event.setCommandHandled(true);
                            return results;
                        } catch (Exception e) {
                            log.error("Command handler error", e);
                            results.add(new HandlerResult(false, e));
                        }
                        // 命令匹配成功，默认阻止后续 handler
                        return results;
                    }
                }
            }

        }

        // 安静模式下：跳过所有非命令 handler
        if (silentActive) {
            log.debug("群 [{}] 安静模式活跃，跳过注解/接口/兜底 handler", silentGroupId);
            return results;
        }

        // 2. 注解 handler 匹配
        List<HandlerEntry<?>> matchedHandlers = handlers.stream()
                .filter(h -> h.eventType.isInstance(event))
                .filter(h -> h.condition == null || h.condition.test(event))
                .sorted(Comparator.comparingInt(HandlerEntry::priority))
                .toList();
        
        if (!matchedHandlers.isEmpty() && !(event instanceof com.dingdong.core.event.HeartbeatEvent)) {
            log.debug("Matched annotation handlers: count={}", matchedHandlers.size());
        }
        
        for (HandlerEntry<?> h : matchedHandlers) {
            // 渠道限制检查
            if (h.getMethod() != null && !isChannelAllowed(h.getMethod(), event)) {
                if (!(event instanceof com.dingdong.core.event.HeartbeatEvent)) {
                    log.debug("渠道限制跳过 handler: eventType={} (channel={})",
                            h.eventType.getSimpleName(), event.getChannelId());
                }
                continue;
            }
            try {
                if (!(event instanceof com.dingdong.core.event.HeartbeatEvent)) {
                    log.debug("Executing annotation handler: eventType={}, priority={}", 
                            h.eventType.getSimpleName(), h.priority);
                }
                ((Consumer<ChannelEvent>) h.executor).accept(event);
                results.add(new HandlerResult(true, null));
            } catch (StopRoutingException sre) {
                log.debug("Annotation handler stopped routing");
                results.add(new HandlerResult(true, null));
                break;
            } catch (Exception e) {
                log.error("Handler error", e);
                results.add(new HandlerResult(false, e));
            }
        }

        // 3. 接口 handler 匹配
        List<Consumer<ChannelEvent>> consumers = eventHandlers.get(event.getClass());
        if (consumers != null && !consumers.isEmpty()) {
            if (!(event instanceof com.dingdong.core.event.HeartbeatEvent)) {
                log.debug("Matched interface handlers: count={}", consumers.size());
            }
            consumers.forEach(c -> {
                try {
                    c.accept(event);
                    results.add(new HandlerResult(true, null));
                } catch (Exception e) {
                    log.error("Interface handler error", e);
                    results.add(new HandlerResult(false, e));
                }
            });
        }

        // 4. 兜底 handler（at-me-trigger 等）
        if (results.isEmpty() && fallbackHandler != null) {
            if (!(event instanceof com.dingdong.core.event.HeartbeatEvent)) {
                log.debug("No handler matched, invoking fallback handler");
            }
            try {
                fallbackHandler.accept(event);
                results.add(new HandlerResult(true, null));
            } catch (Exception e) {
                log.error("Fallback handler error", e);
                results.add(new HandlerResult(false, e));
            }
        }

        if (!(event instanceof com.dingdong.core.event.HeartbeatEvent) && !results.isEmpty()) {
            log.debug("Dispatch completed: successCount={}, totalHandlers={}",
                    results.stream().filter(HandlerResult::success).count(), results.size());
        }
        return results;
    }

    private CommandHandler.CommandArgs matchCommand(String template, String input) {
        // 解析模板：/天气 {city} {days}
        List<String> paramNames = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        int i = 0;
        while (i < template.length()) {
            int start = template.indexOf('{', i);
            if (start == -1) {
                regex.append(Pattern.quote(template.substring(i)));
                break;
            }
            regex.append(Pattern.quote(template.substring(i, start)));
            int end = template.indexOf('}', start);
            if (end == -1) return null;
            String paramName = template.substring(start + 1, end);
            paramNames.add(paramName);
            regex.append("(.+)");
            i = end + 1;
        }
        regex.append("$");

        // 模板没有参数占位符时，支持前缀匹配（处理 @某人 等额外内容）
        if (paramNames.isEmpty()) {
            String trimmedInput = input.trim();
            if (trimmedInput.equals(template) || trimmedInput.startsWith(template + " ")) {
                return new CommandHandler.CommandArgs(new HashMap<>());
            }
            return null;
        }

        Pattern pattern = Pattern.compile(regex.toString());
        Matcher matcher = pattern.matcher(input);
        if (!matcher.matches()) return null;

        Map<String, String> args = new HashMap<>();
        for (int j = 0; j < paramNames.size(); j++) {
            args.put(paramNames.get(j), matcher.group(j + 1).trim());
        }
        return new CommandHandler.CommandArgs(args);
    }

    private Set<Annotation> getMergedAnnotations(Method method) {
        Set<Annotation> result = new HashSet<>();
        for (Annotation ann : method.getAnnotations()) {
            collectMeta(ann, result);
        }
        return result;
    }

    private void collectMeta(Annotation ann, Set<Annotation> result) {
        if (!result.add(ann)) return;
        for (Annotation meta : ann.annotationType().getAnnotations()) {
            if (meta.annotationType().getName().startsWith("java.lang.annotation")) continue;
            collectMeta(meta, result);
        }
    }

    private Predicate<Object> createEventFilter(Set<Annotation> annotations) {
        List<Predicate<Object>> filters = new ArrayList<>();

        Optional<MentionFilter> mentionOpt = annotations.stream()
                .filter(a -> a instanceof MentionFilter)
                .map(a -> (MentionFilter) a)
                .findFirst();
        if (mentionOpt.isPresent()) {
            filters.add(e -> e instanceof MessageEvent msg &&
                    msg.getMessage().isAt(properties.getSelfId()));
        }

        Optional<WakeFilter> wakeOpt = annotations.stream()
                .filter(a -> a instanceof WakeFilter)
                .map(a -> (WakeFilter) a)
                .findFirst();
        if (wakeOpt.isPresent()) {
            filters.add(e -> e instanceof MessageEvent msg &&
                    properties.matchesWakeWord(msg.getPlainText()));
        }

        Optional<RoleFilter> roleOpt = annotations.stream()
                .filter(a -> a instanceof RoleFilter)
                .map(a -> (RoleFilter) a)
                .findFirst();
        if (roleOpt.isPresent()) {
            RoleFilter.Role role = roleOpt.get().value();
            filters.add(e -> {
                if (!(e instanceof MessageEvent msg)) return false;
                long userId = msg.getUserId();
                if (e instanceof GroupMessageEvent ge) {
                    Sender sender = ge.getSenderObj();
                    return switch (role) {
                        case OWNER -> sender.isOwner();
                        case ADMIN -> sender.isAdmin();
                        case SUPERUSER -> properties.getSuperUsers().contains(userId);
                        default -> true;
                    };
                }
                // 私聊场景：仅 SUPERUSER 生效
                return role == RoleFilter.Role.SUPERUSER
                        && properties.getSuperUsers().contains(userId);
            });
        }

        return filters.isEmpty() ? null : e -> filters.stream().allMatch(f -> f.test(e));
    }

    private int calculatePriority(Set<Annotation> annotations) {
        int min = 100;
        for (Annotation ann : annotations) {
            int p = extractPriority(ann);
            if (p < min) {
                min = p;
            }
        }
        return min;
    }

    private int extractPriority(Annotation ann) {
        try {
            Method m = ann.annotationType().getMethod("priority");
            if (m.getReturnType() == int.class) {
                return (int) m.invoke(ann);
            }
        } catch (Exception ignored) {
        }
        return 100;
    }

    @Data
    private static class HandlerEntry<E> {
        private final Class<E> eventType;
        private final int priority;
        private final Predicate<Object> condition;
        private final Consumer<E> executor;
        /** 注册此 handler 的方法（用于 @ChannelRestrict 检查） */
        private final Method method;

        public HandlerEntry(Class<E> eventType, int priority, Predicate<Object> condition, Consumer<E> executor) {
            this(eventType, priority, condition, executor, null);
        }

        public HandlerEntry(Class<E> eventType, int priority, Predicate<Object> condition, Consumer<E> executor, Method method) {
            this.eventType = eventType;
            this.priority = priority;
            this.condition = condition;
            this.executor = executor;
            this.method = method;
        }

        public int priority() {
            return priority;
        }
    }

    @Data
    private static class CommandEntry {
        private final String template;
        private final BiConsumer<ChannelEvent, CommandHandler.CommandArgs> handler;
        private final Predicate<ChannelEvent> filter;
        /** 命令绑定的事件类型；null 表示不限制（同时标注了 @OnGroupMessage 和 @OnPrivateMessage 时也视为 null） */
        private final Class<? extends ChannelEvent> eventType;
        /** 是否允许在安静模式下执行 */
        private final boolean silentModeAllowed;
        /** 允许执行该命令的渠道标识数组；空数组表示全部渠道 */
        private final String[] channels;
    }

    public record HandlerResult(boolean success, Throwable error) {}

    /**
     * 获取已注册的命令帮助列表。
     * @param isAdmin true 返回全部命令（含管理员命令），false 仅返回普通命令
     */
    public List<CommandHelp> getHelpCommands(boolean isAdmin) {
        return getHelpCommands(isAdmin, null);
    }

    /**
     * 获取已注册的命令帮助列表，按渠道过滤。
     * @param isAdmin true 返回全部命令（含管理员命令），false 仅返回普通命令
     * @param channelId 渠道标识，null 表示不过滤
     */
    public List<CommandHelp> getHelpCommands(boolean isAdmin, String channelId) {
        return commandHelps.stream()
                .filter(c -> isAdmin || !c.adminOnly())
                .filter(c -> channelId == null || c.availableOn(channelId))
                .toList();
    }

    public record CommandHelp(String template, String description, boolean adminOnly, String[] channels) {
        /** 检查该命令在指定渠道是否可用。空 channels 表示全渠道可用。 */
        public boolean availableOn(String channelId) {
            if (channels == null || channels.length == 0) return true;
            for (String c : channels) {
                if (c.equals(channelId)) return true;
            }
            return false;
        }
    }

    /**
     * 检查是否匹配唤醒词列表中的任意一个。
     */
    private boolean matchesWakeWord(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        for (String w : properties.getWakeWords()) {
            if (w != null && !w.isBlank() && lower.contains(w.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * 检查事件是否包含 @bot 或唤醒词。
     * OneBot11: 检查 Message.getAts() 是否包含 selfId
     * QQ Official: 检查 QqOfficialGroupMessageEvent.isAtBot()
     */
    private boolean isAtOrWake(ChannelEvent event) {
        // 私聊：直接视为已触发
        if (event instanceof com.dingdong.core.event.PrivateMessageEvent) return true;
        if (event instanceof com.dingdong.channel.api.ChannelMessageEvent chMsg
                && chMsg.getGroupId() == 0) return true;

        // 唤醒词
        String plainText = null;
        if (event instanceof MessageEvent msgEvent) {
            plainText = msgEvent.getPlainText();
            if (msgEvent.getMessage().isAt(properties.getSelfId())) return true;
        } else if (event instanceof com.dingdong.channel.api.ChannelMessageEvent chMsg) {
            plainText = chMsg.getPlainText();
            // QQ Official 的 atBot 标记
            try {
                java.lang.reflect.Method m = event.getClass().getMethod("isAtBot");
                if ((boolean) m.invoke(event)) return true;
            } catch (Exception ignored) {}
        }
        return matchesWakeWord(plainText);
    }
    private boolean isChannelAllowed(CommandEntry entry, ChannelEvent event) {
        if (event == null) return true;
        String[] channels = entry.getChannels();
        if (channels == null || channels.length == 0) return true;
        String eventChannel = event.getChannelId();
        for (String allowed : channels) {
            if (allowed.equals(eventChannel)) return true;
        }
        return false;
    }

    /**
     * 检查注解 handler 所在的方法/类是否有 @ChannelRestrict 限制。
     */
    private boolean isChannelAllowed(Method method, ChannelEvent event) {
        if (event == null) return true;
        ChannelRestrict restrict = method.getAnnotation(ChannelRestrict.class);
        if (restrict == null) {
            restrict = method.getDeclaringClass().getAnnotation(ChannelRestrict.class);
        }
        if (restrict == null || restrict.value().length == 0) {
            return true;
        }
        String eventChannel = event.getChannelId();
        for (String allowed : restrict.value()) {
            if (allowed.equals(eventChannel)) return true;
        }
        return false;
    }
}
