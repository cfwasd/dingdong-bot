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
            Map<String, Object> args = mapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
            Parameter[] params = tm.getMethod().getParameters();
            Object[] argsArray = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];
                ToolParam tp = param.getAnnotation(ToolParam.class);
                if (tp != null) {
                    Object value = args.get(tp.description());
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
