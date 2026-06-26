package com.dingdong.agent.agent;

import com.dingdong.agent.session.SessionKey;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多人格管理器。
 * 管理所有已注册的人格配置，并追踪每个用户当前激活的人格。
 * <p>
 * 人格隔离维度：按 userId 区分。每个用户独立设置人格，所有群/私聊通用。
 * 例如：用户A设为傲娇，用户B设为学者，其他人使用默认人格。
 */
@Slf4j
public class PersonaManager {

    /** 所有已注册的人格，key = persona.id */
    private final Map<String, PersonaDefinition> personas = new ConcurrentHashMap<>();

    /** 用户当前激活的人格 ID。key 格式："u:{userId}"，每个用户独立追踪 */
    private final Map<String, String> activePersonaMap = new ConcurrentHashMap<>();

    /** 默认人格 ID（启动时注册的第一个，或显式指定） */
    private String defaultPersonaId;

    /**
     * 注册一个人格。第一个注册的人格自动成为默认人格。
     */
    public void register(PersonaDefinition persona) {
        personas.put(persona.getId(), persona);
        if (defaultPersonaId == null) {
            defaultPersonaId = persona.getId();
        }
        log.info("Registered persona: id={}, name={}", persona.getId(), persona.getName());
    }

    /**
     * 设置默认人格 ID。
     */
    public void setDefaultPersonaId(String id) {
        this.defaultPersonaId = id;
    }

    /**
     * 获取所有已注册的人格。
     */
    public List<PersonaDefinition> listPersonas() {
        return new ArrayList<>(personas.values());
    }

    /**
     * 根据 ID 获取人格配置。
     * @return 人格配置，不存在时返回 null
     */
    public PersonaDefinition getPersona(String id) {
        return personas.get(id);
    }

    /**
     * 获取指定用户当前激活的人格。
     * 如果该用户没有显式设置过人格，返回默认人格。
     */
    public PersonaDefinition getActivePersona(SessionKey key) {
        String mapKey = buildMapKey(key);
        String personaId = activePersonaMap.get(mapKey);
        if (personaId == null) {
            personaId = defaultPersonaId;
        }
        if (personaId == null) {
            return null; // 没有注册任何人格
        }
        return personas.getOrDefault(personaId, personas.get(defaultPersonaId));
    }

    /**
     * 切换指定用户的人格。
     * @param key    会话键（从中提取 userId）
     * @param personaId 目标人格 ID
     * @return true 切换成功，false 目标人格不存在
     */
    public boolean switchPersona(SessionKey key, String personaId) {
        if (!personas.containsKey(personaId)) {
            return false;
        }
        String mapKey = buildMapKey(key);
        activePersonaMap.put(mapKey, personaId);
        log.info("Persona switched to '{}' for user {}", personaId, key.userId());
        return true;
    }

    /**
     * 重置指定会话的人格为默认。
     */
    public void resetPersona(SessionKey key) {
        String mapKey = buildMapKey(key);
        activePersonaMap.remove(mapKey);
    }

    /**
     * 获取当前激活人格的系统提示词。
     * 如果没有配置任何人格，返回 null（由 NapCatAgent 使用默认 prompt）。
     */
    public String getActiveSystemPrompt(SessionKey key) {
        PersonaDefinition persona = getActivePersona(key);
        return persona != null ? persona.getSystemPrompt() : null;
    }

    /**
     * 根据关键词模糊搜索人格（支持按 id 或 name 匹配）。
     */
    public PersonaDefinition findPersonaByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String lower = keyword.toLowerCase().trim();

        // 精确匹配 id
        PersonaDefinition exact = personas.get(lower);
        if (exact != null) return exact;

        // 模糊匹配 id 或 name
        for (PersonaDefinition p : personas.values()) {
            if (p.getId().toLowerCase().contains(lower)
                    || (p.getName() != null && p.getName().toLowerCase().contains(lower))) {
                return p;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return personas.isEmpty();
    }

    public int size() {
        return personas.size();
    }

    private String buildMapKey(SessionKey key) {
        // 始终按 userId 维度追踪人格，每个用户独立，所有群/私聊通用
        return "u:" + key.userId();
    }

    /**
     * 人格定义。独立于配置层，供 agent 内部使用。
     */
    public static class PersonaDefinition {
        private final String id;
        private final String name;
        private final String description;
        private final String systemPrompt;
        /** 关联的 TTS 声线配置名称（对应 tts.voice-profiles 中的 key） */
        private final String voiceProfile;

        public PersonaDefinition(String id, String name, String description, String systemPrompt) {
            this(id, name, description, systemPrompt, null);
        }

        public PersonaDefinition(String id, String name, String description, String systemPrompt, String voiceProfile) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.systemPrompt = systemPrompt;
            this.voiceProfile = voiceProfile;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getSystemPrompt() { return systemPrompt; }
        public String getVoiceProfile() { return voiceProfile; }
    }
}
