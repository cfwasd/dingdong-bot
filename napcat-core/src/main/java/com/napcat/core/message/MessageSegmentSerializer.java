package com.napcat.core.message;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class MessageSegmentSerializer extends JsonSerializer<MessageSegment> {

    @Override
    public void serialize(MessageSegment value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", value.getType());
        gen.writeObjectFieldStart("data");
        for (Map.Entry<String, Object> entry : value.getData().entrySet()) {
            Object fieldValue = entry.getValue();
            if (fieldValue instanceof MessageChain mc) {
                // MessageChain 序列为消息段数组
                gen.writeFieldName(entry.getKey());
                gen.writeStartArray();
                for (MessageSegment seg : mc) {
                    serializers.defaultSerializeValue(seg, gen);
                }
                gen.writeEndArray();
            } else if (fieldValue instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof MessageSegment) {
                // List<MessageSegment>（如 ForwardSegment 的 nodes）直接序列化为数组
                gen.writeFieldName(entry.getKey());
                gen.writeStartArray();
                for (Object item : list) {
                    serializers.defaultSerializeValue(item, gen);
                }
                gen.writeEndArray();
            } else {
                gen.writeObjectField(entry.getKey(), fieldValue);
            }
        }
        gen.writeEndObject();
        gen.writeEndObject();
    }
}
