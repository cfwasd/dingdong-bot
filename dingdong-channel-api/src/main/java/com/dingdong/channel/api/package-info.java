/**
 * 叮咚 (DingDong) 统一渠道抽象层。
 * <p>
 * 定义渠道无关的事件模型 ({@link com.dingdong.channel.api.ChannelEvent})、
 * 消息发送接口 ({@link com.dingdong.channel.api.MessageSender})、
 * 通道适配器 ({@link com.dingdong.channel.api.BotChannel}) 和
 * 渠道隔离注解 ({@link com.dingdong.channel.api.annotation.ChannelRestrict})。
 * </p>
 * <p>
 * 所有渠道实现（OneBot11 / QQ官方）和 AI Agent 只依赖此模块，
 * 不互相依赖。
 * </p>
 */
package com.dingdong.channel.api;
