package com.dingdong.agent.plugin;

import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.agent.tool.ToolSchema;

import java.util.List;

/**
 * Agent 插件接口。
 */
public interface AgentPlugin {
    String pluginId();
    void onLoad(PluginContext ctx);
    void onUnload();
    List<ToolSchema> getTools();
    String getSystemPromptExtension();
    default boolean onMessage(ChannelMessageEvent event) { return false; }
}
