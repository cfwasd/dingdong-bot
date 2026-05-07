package com.napcat.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ToolRegistry {

    private final Map<String, ToolMethod> tools = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public void register(Object bean) {
        Class<?> clazz = bean.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            Tool toolAnn = method.getAnnotation(Tool.class);
            if (toolAnn == null) continue;

            method.setAccessible(true);
            ToolMethod tm = new ToolMethod(toolAnn.name(), toolAnn.description(), bean, method);
            tools.put(toolAnn.name(), tm);
            log.debug("Registered tool: {}", toolAnn.name());
        }
    }

    public List<ToolSchema> getSchemas() {
        List<ToolSchema> schemas = new ArrayList<>();
        for (ToolMethod tm : tools.values()) {
            schemas.add(buildSchema(tm));
        }
        return schemas;
    }

    public Object invoke(String name, String argumentsJson) {
        ToolMethod tm = tools.get(name);
        if (tm == null) {
            throw new IllegalArgumentException("Tool not found: " + name);
        }
        try {
            // 验证并清理 JSON 字符串
            if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
                log.warn("Empty arguments for tool: {}", name);
                return tm.getMethod().invoke(tm.getBean(), new Object[tm.getMethod().getParameterCount()]);
            }

            // 尝试解析 JSON，增加容错处理
            Map<String, Object> args;
            try {
                args = mapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
            } catch (Exception jsonEx) {
                log.error("Invalid JSON arguments for tool '{}': {}\nError: {}",
                        name, argumentsJson, jsonEx.getMessage());

                // 尝试修复常见的 JSON 格式问题
                String fixedJson = tryFixJson(argumentsJson);
                if (fixedJson != null) {
                    try {
                        args = mapper.readValue(fixedJson, new TypeReference<Map<String, Object>>() {});
                        log.info("Successfully fixed JSON for tool '{}': {}", name, fixedJson);
                    } catch (Exception fixEx) {
                        log.error("Failed to fix JSON for tool '{}': {}", name, fixEx.getMessage());
                        return "Error: Invalid tool arguments format. Expected valid JSON but got: " + argumentsJson;
                    }
                } else {
                    // 尝试从错误 JSON 中提取值并智能映射到参数
                    args = extractAndMapParameters(name, argumentsJson, tm);
                    if (args == null) {
                        return "Error: Invalid tool arguments format. Expected valid JSON but got: " + argumentsJson;
                    }
                    log.info("Extracted and mapped parameters for tool '{}': {}", name, args);
                }
            }

            Parameter[] params = tm.getMethod().getParameters();
            Object[] argsArray = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];
                ToolParam tp = param.getAnnotation(ToolParam.class);
                if (tp != null) {
                    // 优先使用 description 作为 key，如果找不到则尝试其他可能的 key
                    Object value = args.get(tp.description());

                    // 如果通过 description 没找到，尝试使用参数名
                    if (value == null && param.isNamePresent()) {
                        value = args.get(param.getName());
                    }

                    // 如果还是没找到，尝试模糊匹配（去除空格、标点等）
                    if (value == null) {
                        value = findValueByFuzzyMatch(args, tp.description());
                    }

                    argsArray[i] = convertType(value, param.getType());
                } else {
                    argsArray[i] = null;
                }
            }

            return tm.getMethod().invoke(tm.getBean(), argsArray);
        } catch (Exception e) {
            log.error("Tool invocation error: {}", name, e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 从错误的 JSON 中提取值并智能映射到工具参数
     */
    private Map<String, Object> extractAndMapParameters(String toolName, String invalidJson, ToolMethod tm) {
        try {
            // 提取所有 URL
            java.util.regex.Matcher urlMatcher = java.util.regex.Pattern.compile("(https?://[^\\s\"'}\\]]+)").matcher(invalidJson);
            
            // 获取方法的参数信息
            Parameter[] params = tm.getMethod().getParameters();
            if (params.length == 1) {
                // 只有一个参数，直接使用提取到的第一个值
                if (urlMatcher.find()) {
                    String url = urlMatcher.group(1);
                    Map<String, Object> mappedArgs = new HashMap<>();
                    ToolParam tp = params[0].getAnnotation(ToolParam.class);
                    if (tp != null) {
                        mappedArgs.put(tp.description(), url);
                        if (params[0].isNamePresent()) {
                            mappedArgs.put(params[0].getName(), url);
                        }
                    }
                    return mappedArgs;
                }
            }
            
            // 多个参数的情况，尝试提取键值对
            Map<String, Object> result = new HashMap<>();
            java.util.regex.Matcher kvMatcher = java.util.regex.Pattern.compile("\"([^\"]+)\":\\s*\"([^\"]+)\"").matcher(invalidJson);
            while (kvMatcher.find()) {
                result.put(kvMatcher.group(1), kvMatcher.group(2));
            }
            
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.debug("Failed to extract parameters from invalid JSON: {}", invalidJson);
            return null;
        }
    }

    /**
     * 尝试修复常见的 JSON 格式问题
     */
    private String tryFixJson(String invalidJson) {
        if (invalidJson == null || invalidJson.trim().isEmpty()) {
            return null;
        }

        String json = invalidJson.trim();

        // 如果已经是有效 JSON，直接返回
        try {
            mapper.readTree(json);
            return json;
        } catch (Exception e) {
            // 继续尝试修复
        }

        // 修复1: 键名缺少引号的情况，如 {name: "value"} -> {"name": "value"}
        json = json.replaceAll("(?<=\\{|,)\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:", " \"$1\":");

        // 修复2: 键名包含冒号和多余文本的情况
        // 匹配模式: "xxx: "yyy" 或 "xxx: yyy" -> 提取第一个有意义的词作为键名
        json = json.replaceAll("\"([^\"]{0,50}?):\\s*\"([^\"]+)\"", "\"$1\": \"$2\"");
        
        // 修复3: 处理中文字符后跟冒号但在引号内的情况
        // 例如: "要获取内容的完整: "url"" -> "url": "url"
        // 提取 URL 或其他值作为实际的键值对
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"[^\"]*?(https?://[^\"}]+)\"").matcher(json);
        if (matcher.find()) {
            String url = matcher.group(1);
            // 尝试推断参数名（使用工具定义中的第一个参数）
            json = "{\"url\": \"" + url + "\"}";
        }

        // 确保最外层有大括号
        if (!json.startsWith("{")) {
            json = "{" + json;
        }
        if (!json.endsWith("}")) {
            json = json + "}";
        }

        // 再次尝试验证
        try {
            mapper.readTree(json);
            return json;
        } catch (Exception e) {
            log.debug("Could not fix JSON: {}", invalidJson);
            return null;
        }
    }

    /**
     * 模糊匹配查找值，处理键名不完全匹配的情况
     */
    private Object findValueByFuzzyMatch(Map<String, Object> args, String targetKey) {
        if (args == null || targetKey == null) {
            return null;
        }

        // 标准化目标键名
        String normalizedTarget = normalizeKey(targetKey);

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String normalizedKey = normalizeKey(entry.getKey());

            // 完全匹配
            if (normalizedTarget.equals(normalizedKey)) {
                return entry.getValue();
            }

            // 包含关系匹配
            if (normalizedTarget.contains(normalizedKey) || normalizedKey.contains(normalizedTarget)) {
                return entry.getValue();
            }

            // 移除常见描述词后匹配
            String simplifiedTarget = removeCommonWords(normalizedTarget);
            String simplifiedKey = removeCommonWords(normalizedKey);
            if (simplifiedTarget.equals(simplifiedKey) ||
                    simplifiedTarget.contains(simplifiedKey) ||
                    simplifiedKey.contains(simplifiedTarget)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 标准化键名：转小写，去除特殊字符
     */
    private String normalizeKey(String key) {
        if (key == null) return "";
        return key.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }

    /**
     * 移除常见的描述性词汇
     */
    private String removeCommonWords(String text) {
        if (text == null) return "";

        String[] commonWords = {"指定", "要", "的", "如", "例如", "默认", "可选", "必填",
                "required", "optional", "default", "such as", "for example"};

        String result = text;
        for (String word : commonWords) {
            result = result.replace(word, "").replaceAll("\\s+", " ").trim();
        }

        return result;
    }


    private ToolSchema buildSchema(ToolMethod tm) {
        ToolSchema schema = new ToolSchema();
        schema.setName(tm.getName());
        schema.setDescription(tm.getDescription());

        Map<String, ToolSchema.ParameterSchema> params = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : tm.getMethod().getParameters()) {
            ToolParam tp = param.getAnnotation(ToolParam.class);
            if (tp == null) continue;

            ToolSchema.ParameterSchema ps = new ToolSchema.ParameterSchema();
            ps.setType(tp.type());
            ps.setDescription(tp.description());
            if (tp.enums().length > 0) {
                ps.setEnums(Arrays.asList(tp.enums()));
            }
            params.put(tp.description(), ps);
            if (tp.required()) {
                required.add(tp.description());
            }
        }

        schema.setParameters(params);
        schema.setRequired(required);
        return schema;
    }

    private Object convertType(Object value, Class<?> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return value;
        if (type == String.class) return value.toString();
        if (type == int.class || type == Integer.class) {
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        }
        if (type == long.class || type == Long.class) {
            if (value instanceof Number) return ((Number) value).longValue();
            return Long.parseLong(value.toString());
        }
        if (type == double.class || type == Double.class) {
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(value.toString());
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        return value;
    }
}
