package com.napcat.core.message;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Data
@JsonDeserialize(using = MessageChain.MessageChainDeserializer.class)
public class MessageChain implements List<MessageSegment> {

    private final List<MessageSegment> segments = new ArrayList<>();

    public MessageChain() {}

    public MessageChain(MessageSegment segment) {
        this.segments.add(segment);
    }

    public static MessageChain of(MessageSegment segment) {
        return new MessageChain(segment);
    }

    public static MessageChain ofText(String text) {
        return new MessageChain(new TextSegment(text));
    }

    public static MessageChain ofAt(long qq) {
        return new MessageChain(new AtSegment(qq));
    }

    public static MessageChain ofAtAll() {
        AtSegment seg = new AtSegment();
        seg.setType("at");
        seg.setDataValue("qq", "all");
        return new MessageChain(seg);
    }

    public static MessageChain ofFace(int id) {
        return new MessageChain(new FaceSegment(id));
    }

    public static MessageChain ofImage(String file) {
        return new MessageChain(new ImageSegment(file));
    }

    public static MessageChain ofRecord(String file) {
        return new MessageChain(new RecordSegment(file));
    }

    public static MessageChain ofVideo(String file) {
        return new MessageChain(new VideoSegment(file));
    }

    public static MessageChain ofFile(String file, String name) {
        return new MessageChain(new FileSegment(file, name));
    }

    public static MessageChain ofReply(int messageId) {
        return new MessageChain(new ReplySegment(messageId));
    }

    public static MessageChain ofMarkdown(String content) {
        return new MessageChain(new MarkdownSegment(content));
    }

    public static MessageChain ofJson(String data) {
        return new MessageChain(new JsonSegment(data));
    }

    public static MessageChain ofXml(String data) {
        return new MessageChain(new XmlSegment(data));
    }

    public static MessageChain ofForward(List<NodeSegment> nodes) {
        return new MessageChain(new ForwardSegment(nodes));
    }

    public MessageChain text(String text) {
        this.segments.add(new TextSegment(text));
        return this;
    }

    public MessageChain at(long qq) {
        this.segments.add(new AtSegment(qq));
        return this;
    }

    public MessageChain atAll() {
        AtSegment seg = new AtSegment();
        seg.setType("at");
        seg.setDataValue("qq", "all");
        this.segments.add(seg);
        return this;
    }

    public MessageChain face(int id) {
        this.segments.add(new FaceSegment(id));
        return this;
    }

    public MessageChain image(String file) {
        this.segments.add(new ImageSegment(file));
        return this;
    }

    public MessageChain record(String file) {
        this.segments.add(new RecordSegment(file));
        return this;
    }

    public MessageChain video(String file) {
        this.segments.add(new VideoSegment(file));
        return this;
    }

    public MessageChain file(String file, String name) {
        this.segments.add(new FileSegment(file, name));
        return this;
    }

    public MessageChain reply(int messageId) {
        this.segments.add(new ReplySegment(messageId));
        return this;
    }

    public MessageChain markdown(String content) {
        this.segments.add(new MarkdownSegment(content));
        return this;
    }

    public MessageChain json(String data) {
        this.segments.add(new JsonSegment(data));
        return this;
    }

    public MessageChain xml(String data) {
        this.segments.add(new XmlSegment(data));
        return this;
    }

    public MessageChain forward(List<NodeSegment> nodes) {
        this.segments.add(new ForwardSegment(nodes));
        return this;
    }

    public String toPlainText() {
        return segments.stream()
                .filter(s -> s instanceof TextSegment)
                .map(s -> ((TextSegment) s).getText())
                .collect(Collectors.joining());
    }

    public boolean containsImage() {
        return segments.stream().anyMatch(s -> s instanceof ImageSegment);
    }

    public boolean isAt(long qq) {
        return segments.stream()
                .filter(s -> s instanceof AtSegment)
                .map(s -> ((AtSegment) s).getQq())
                .anyMatch(q -> q == qq);
    }

    public boolean isAtAll() {
        return segments.stream()
                .filter(s -> s instanceof AtSegment)
                .anyMatch(s -> ((AtSegment) s).isAtAll());
    }

    public List<String> getImages() {
        return segments.stream()
                .filter(s -> s instanceof ImageSegment)
                .map(s -> {
                    String url = ((ImageSegment) s).getUrl();
                    return url != null ? url : ((ImageSegment) s).getFile();
                })
                .collect(Collectors.toList());
    }

    public List<Long> getAts() {
        return segments.stream()
                .filter(s -> s instanceof AtSegment)
                .map(s -> ((AtSegment) s).getQq())
                .filter(q -> q > 0)
                .collect(Collectors.toList());
    }

    @Override
    public int size() {
        return segments.size();
    }

    @Override
    public boolean isEmpty() {
        return segments.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return segments.contains(o);
    }

    @Override
    public Iterator<MessageSegment> iterator() {
        return segments.iterator();
    }

    @Override
    public Object[] toArray() {
        return segments.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return segments.toArray(a);
    }

    @Override
    public boolean add(MessageSegment messageSegment) {
        return segments.add(messageSegment);
    }

    @Override
    public boolean remove(Object o) {
        return segments.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return segments.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends MessageSegment> c) {
        return segments.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends MessageSegment> c) {
        return segments.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return segments.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return segments.retainAll(c);
    }

    @Override
    public void clear() {
        segments.clear();
    }

    @Override
    public MessageSegment get(int index) {
        return segments.get(index);
    }

    @Override
    public MessageSegment set(int index, MessageSegment element) {
        return segments.set(index, element);
    }

    @Override
    public void add(int index, MessageSegment element) {
        segments.add(index, element);
    }

    @Override
    public MessageSegment remove(int index) {
        return segments.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return segments.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return segments.lastIndexOf(o);
    }

    @Override
    public ListIterator<MessageSegment> listIterator() {
        return segments.listIterator();
    }

    @Override
    public ListIterator<MessageSegment> listIterator(int index) {
        return segments.listIterator(index);
    }

    @Override
    public List<MessageSegment> subList(int fromIndex, int toIndex) {
        return segments.subList(fromIndex, toIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageChain)) return false;
        MessageChain that = (MessageChain) o;
        return Objects.equals(segments, that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segments);
    }

    public static class MessageChainDeserializer extends JsonDeserializer<MessageChain> {
        @Override
        public MessageChain deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            // 支持 array 格式和 string 格式（CQ 码）
            if (p.currentToken().isStructStart()) {
                MessageChain chain = new MessageChain();
                while (p.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                    // 读取 type 和 data
                    String type = null;
                    Map<String, Object> data = new HashMap<>();
                    while (p.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                        String field = p.getCurrentName();
                        p.nextToken();
                        if ("type".equals(field)) {
                            type = p.getValueAsString();
                        } else if ("data".equals(field)) {
                            while (p.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                                String dataKey = p.getCurrentName();
                                p.nextToken();
                                data.put(dataKey, p.readValueAs(Object.class));
                            }
                        }
                    }
                    MessageSegment seg = createSegment(type, data);
                    if (seg != null) chain.add(seg);
                }
                return chain;
            } else {
                // String 格式，简单解析 CQ 码（简化版）
                String text = p.getValueAsString();
                return parseCqCode(text);
            }
        }

        private MessageSegment createSegment(String type, Map<String, Object> data) {
            MessageSegment seg;
            switch (type) {
                case "text": seg = new TextSegment(); break;
                case "at": seg = new AtSegment(); break;
                case "image": seg = new ImageSegment(); break;
                case "face": seg = new FaceSegment(); break;
                case "reply": seg = new ReplySegment(); break;
                case "record": seg = new RecordSegment(); break;
                case "video": seg = new VideoSegment(); break;
                case "file": seg = new FileSegment(); break;
                case "markdown": seg = new MarkdownSegment(); break;
                case "json": seg = new JsonSegment(); break;
                case "xml": seg = new XmlSegment(); break;
                case "node": seg = new NodeSegment(); break;
                case "forward": seg = new ForwardSegment(); break;
                default: seg = new MessageSegment() {}; break;
            }
            seg.setType(type);
            if (data != null) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    seg.setDataValue(entry.getKey(), entry.getValue());
                }
            }
            return seg;
        }

        private MessageChain parseCqCode(String text) {
            MessageChain chain = new MessageChain();
            // 简化版 CQ 码解析，仅支持 text 和 at
            // 实际使用时建议使用 array 格式
            if (text == null || text.isEmpty()) {
                return chain;
            }
            chain.text(text);
            return chain;
        }
    }
}
