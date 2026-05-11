package com.napcat.agent.scheduler;

import com.napcat.agent.session.SessionKey;
import com.napcat.agent.tool.ToolRegistry;
import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import com.napcat.core.scheduler.CronEvaluator;
import com.napcat.core.scheduler.SchedulePoller;
import com.napcat.core.scheduler.ScheduleStore;
import com.napcat.core.scheduler.ScheduleStore.ScheduleEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 可调用的定时任务管理工具。
 * 让 AI 通过 function calling 创建/删除/查询/启停定时任务。
 * 所有操作直接写 SQLite，SchedulePoller 自动感知变更。
 */
@Slf4j
public class ScheduleTool {

    private final ScheduleStore store;
    private final SchedulePoller poller;

    public ScheduleTool() {
        this.store = null;
        this.poller = null;
    }

    public ScheduleTool(ScheduleStore store, SchedulePoller poller) {
        this.store = store;
        this.poller = poller;
    }

    /**
     * 创建一个定时任务。
     */
    @Tool(name = "create_schedule", description = "创建一个定时任务。在指定时间执行发送消息或AI生成内容后发送。如果不指定目标ID，默认使用当前对话的群号或用户QQ。")
    public String createSchedule(
            @ToolParam(description = "cron_expression", required = true)
            String cron,
            @ToolParam(description = "task_name", required = true)
            String name,
            @ToolParam(description = "target_id", required = false)
            Long targetId,
            @ToolParam(description = "action_type", required = false)
            String action,
            @ToolParam(description = "target_type", required = false)
            String targetType,
            @ToolParam(description = "reply_text", required = false)
            String replyText,
            @ToolParam(description = "prompt_content", required = false)
            String prompt
    ) {
        if (!CronEvaluator.isValid(cron)) {
            return "❌ Cron 表达式无效：" + cron + "。请使用标准格式，如 0 0 8 * * ?";
        }

        // 验证任务名称不能是 Cron 表达式
        if (name != null && name.matches("^\\d+\\s+.*")) {
            log.warn("Task name looks like cron expression: '{}', rejecting", name);
            return "❌ 错误：任务名称不能是 Cron 表达式！请提供简短的任务描述，如'喝水提醒'、'早安问候'。";
        }

        // 验证参数完整性
        String validatedAction = action != null ? action : "ai_generate";  // 默认使用 AI 生成
        
        if ("send_message".equals(validatedAction) && (replyText == null || replyText.isBlank())) {
            return "❌ 错误：当 action=send_message 时，replyText 参数必填！请提供要发送的具体消息内容。\n" +
                   "示例：replyText=\"记得喝水哦~\"";
        }
        if ("ai_generate".equals(validatedAction) && (prompt == null || prompt.isBlank())) {
            return "❌ 错误：当 action=ai_generate 时，prompt 参数必填！请提供提醒内容，AI会据此生成个性化消息。\n" +
                   "示例：prompt=\"提醒大家喝水休息，保持健康\"\n" +
                   "示例：prompt=\"发送一段温馨的早安问候\"";
        }

        // 从 ToolRegistry 获取当前会话的 SessionKey
        SessionKey sessionKey = ToolRegistry.getCurrentSessionKey();
        
        long autoTargetId = 0;
        String autoTargetType = "group";
        Long createdBy = null;
        
        if (sessionKey != null) {
            // 根据 SessionKey 自动推断目标
            if (sessionKey.isPrivate()) {
                // 私聊场景：默认发送到当前用户
                autoTargetId = sessionKey.userId();
                autoTargetType = "private";
                createdBy = sessionKey.userId();
            } else if (sessionKey.isGroup()) {
                // 群聊场景：默认发送到当前群，创建者为当前用户
                autoTargetId = sessionKey.groupId();
                autoTargetType = "group";
                createdBy = sessionKey.userId();
            }
            log.debug("Auto-detected target from SessionKey: userId={}, groupId={}, targetType={}, createdBy={}", 
                    sessionKey.userId(), sessionKey.groupId(), autoTargetType, createdBy);
        } else {
            log.warn("SessionKey is null, cannot auto-detect target. Please specify targetId manually.");
        }

        // 如果用户未提供 targetId，使用自动获取的值
        long finalTargetId = (targetId != null && targetId > 0) ? targetId : autoTargetId;
        String finalTargetType = (targetType != null && !targetType.isBlank()) ? targetType : autoTargetType;

        if (finalTargetId <= 0) {
            return "❌ 无法确定目标任务目标。请手动指定 targetId 参数，或在群聊/私聊中使用此工具。";
        }

        // 检查是否存在同名任务（按名称去重）
        String existingId = store.findIdByName(name);
        if (existingId != null) {
            // 更新已有任务
            ScheduleEntry existingEntry = store.getById(existingId);
            if (existingEntry != null) {
                // 取消旧任务的调度
                if (poller != null) {
                    poller.cancelTask(existingId);
                }
                
                // 删除旧任务
                store.delete(existingId);
                log.info("Replaced existing schedule: name={}, oldId={}", name, existingId);
            }
        }

        ScheduleEntry entry = new ScheduleEntry();
        entry.setName(name);
        entry.setCron(cron);
        entry.setTargetId(finalTargetId);
        entry.setAction(validatedAction);
        entry.setTargetType(finalTargetType);
        entry.setReplyText(replyText);
        entry.setPrompt(prompt);
        entry.setEnabled(true);
        entry.setCreatedBy(createdBy);

        String id = store.insert(entry);

        // 立即注册到调度器
        if (poller != null) {
            entry.setId(id);
            poller.scheduleNow(entry);
        }

        String actionDesc = "ai_generate".equals(validatedAction) ? "AI生成" : "固定文本";
        String replaceInfo = existingId != null ? "（已替换同名任务）" : "";
        return "✅ 定时任务已创建" + replaceInfo + "：\n" +
                "- ID：" + id + "\n" +
                "- 名称：" + name + "\n" +
                "- Cron：" + cron + "\n" +
                "- 动作：" + actionDesc + "\n" +
                "- 目标：" + entry.getTargetType() + "/" + finalTargetId + "\n" +
                "- 创建者：" + (createdBy != null ? createdBy : "未知") +
                (targetId == null ? "（自动检测）" : "");
    }

    /**
     * 删除一个定时任务。
     */
    @Tool(name = "delete_schedule", description = "删除一个定时任务。")
    public String deleteSchedule(
            @ToolParam(description = "任务ID（创建时返回的ID）或任务名称", required = true)
            String idOrName
    ) {
        // 先尝试 ID 匹配
        ScheduleEntry entry = store.getById(idOrName);
        if (entry == null) {
            // 再尝试名称模糊匹配
            List<ScheduleEntry> all = store.listAll();
            entry = all.stream()
                    .filter(e -> e.getName() != null && e.getName().contains(idOrName))
                    .findFirst().orElse(null);
        }

        if (entry == null) {
            return "❌ 未找到任务：" + idOrName;
        }

        if (poller != null) {
            poller.cancelTask(entry.getId());
        }
        store.delete(entry.getId());
        return "✅ 已删除任务：" + entry.getName() + " (ID: " + entry.getId() + ")";
    }

    /**
     * 列出所有定时任务。
     */
    @Tool(name = "list_schedules", description = "列出所有已创建的定时任务。")
    public String listSchedules() {
        List<ScheduleEntry> all = store.listAll();
        if (all.isEmpty()) {
            return "📋 当前没有定时任务。";
        }

        String list = all.stream()
                .map(e -> "- **" + e.getName() + "** [" + (e.isEnabled() ? "启用" : "禁用") + "]\n" +
                        "  ID：" + e.getId() + " | Cron：" + e.getCron() + "\n" +
                        "  动作：" + e.getAction() + " → " + e.getTargetType() + "/" + e.getTargetId())
                .collect(Collectors.joining("\n"));

        return "📋 定时任务列表（共 " + all.size() + " 个）：\n" + list;
    }

    /**
     * 启用/禁用一个定时任务。
     */
    @Tool(name = "toggle_schedule", description = "启用或禁用一个定时任务。")
    public String toggleSchedule(
            @ToolParam(description = "任务ID或名称", required = true)
            String idOrName,
            @ToolParam(description = "true=启用，false=禁用", required = true)
            boolean enabled
    ) {
        ScheduleEntry entry = store.getById(idOrName);
        if (entry == null) {
            List<ScheduleEntry> all = store.listAll();
            entry = all.stream()
                    .filter(e -> e.getName() != null && e.getName().contains(idOrName))
                    .findFirst().orElse(null);
        }

        if (entry == null) {
            return "❌ 未找到任务：" + idOrName;
        }

        if (enabled) {
            store.toggle(entry.getId(), true);
            if (poller != null) poller.scheduleNow(entry);
        } else {
            store.toggle(entry.getId(), false);
            if (poller != null) poller.cancelTask(entry.getId());
        }

        return "✅ 任务 **" + entry.getName() + "** 已" + (enabled ? "启用" : "禁用");
    }
}
