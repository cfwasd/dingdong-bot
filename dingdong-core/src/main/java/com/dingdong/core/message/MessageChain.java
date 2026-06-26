package com.dingdong.core.message;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Data
@JsonSerialize(using = MessageChainSerializer.class)
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

    /**
     * 生成适合传给 LLM Agent 的文本描述，保留图片、表情、@等富文本信息。
     * 纯文本消息与 toPlainText() 结果一致；含富文本时会插入语义化标记。
     */
    public String toAgentPrompt() {
        if (segments.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MessageSegment seg : segments) {
            if (seg instanceof TextSegment text) {
                sb.append(text.getText());
            } else if (seg instanceof AtSegment at) {
                if (at.isAtAll()) sb.append("[@全体成员]");
                else sb.append("[@").append(at.getQq()).append("]");
            } else if (seg instanceof ImageSegment img) {
                String url = firstNonBlank(img.getUrl(), img.getFile());
                if (url != null) {
                    sb.append("[图片:").append(url).append("]");
                } else {
                    sb.append("[图片]");
                }
            } else if (seg instanceof FaceSegment face) {
                sb.append("[表情:").append(face.getData().getOrDefault("id", "?")).append("]");
            } else if (seg instanceof RecordSegment record) {
                String url = record.getFile();
                sb.append(url != null ? "[语音:" + url + "]" : "[语音]");
            } else if (seg instanceof VideoSegment video) {
                String url = video.getFile();
                sb.append(url != null ? "[视频:" + url + "]" : "[视频]");
            } else if (seg instanceof FileSegment file) {
                String name = file.getName();
                String url = file.getFile();
                if (name != null && url != null) sb.append("[文件:").append(name).append(" ").append(url).append("]");
                else if (name != null) sb.append("[文件:").append(name).append("]");
                else if (url != null) sb.append("[文件:").append(url).append("]");
                else sb.append("[文件]");
            } else if (seg instanceof ReplySegment) {
                sb.append("[回复消息]");
            } else if (seg instanceof MarkdownSegment md) {
                String content = md.getContent();
                sb.append(content != null ? "[Markdown消息:" + content + "]" : "[Markdown消息]");
            } else if (seg instanceof JsonSegment json) {
                String data = json.getJsonData();
                sb.append(data != null ? "[卡片消息:" + data + "]" : "[卡片消息]");
            } else if (seg instanceof XmlSegment xml) {
                String data = xml.getXmlData();
                sb.append(data != null ? "[XML消息:" + data + "]" : "[XML消息]");
            } else if (seg instanceof ForwardSegment) {
                sb.append("[合并转发消息]");
            } else if (seg instanceof NodeSegment) {
                sb.append("[转发节点]");
            } else if (seg instanceof UnknownSegment unknown) {
                sb.append("[未知消息:").append(unknown.getType()).append("]");
            } else {
                sb.append("[").append(seg.getType()).append("]");
            }
        }
        return sb.toString().trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
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

        private static final Pattern CQ_CODE_PATTERN = Pattern.compile("\\[CQ:([^,\\]]+)(?:,([^\\]]*))?\\]");

        @Override
        public MessageChain deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node.isArray()) {
                return deserializeArray(node);
            } else if (node.isTextual()) {
                return deserializeString(node.asText());
            } else if (node.isObject()) {
                // 兼容非标准单对象格式
                MessageChain chain = new MessageChain();
                MessageSegment seg = deserializeSegment(node);
                if (seg != null) chain.add(seg);
                return chain;
            }
            return new MessageChain();
        }

        private MessageChain deserializeArray(JsonNode node) {
            MessageChain chain = new MessageChain();
            for (JsonNode segNode : node) {
                MessageSegment seg = deserializeSegment(segNode);
                if (seg != null) chain.add(seg);
            }
            return chain;
        }

        private MessageSegment deserializeSegment(JsonNode node) {
            if (node == null || !node.isObject()) return null;
            String type = node.path("type").asText("");
            JsonNode dataNode = node.path("data");
            MessageSegment seg = createSegment(type);
            if (seg == null) {
                seg = new UnknownSegment(type);
            }
            MessageSegment finalSeg = seg;
            finalSeg.setType(type);
            if (dataNode.isObject()) {
                dataNode.fields().forEachRemaining(entry -> {
                    finalSeg.setDataValue(entry.getKey(), convertJsonNode(entry.getValue()));
                });
            }
            return finalSeg;
        }

        private Object convertJsonNode(JsonNode node) {
            if (node == null || node.isNull()) return null;
            if (node.isTextual()) return node.asText();
            if (node.isInt()) return node.asInt();
            if (node.isLong()) return node.asLong();
            if (node.isBoolean()) return node.asBoolean();
            if (node.isDouble()) return node.asDouble();
            if (node.isArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonNode item : node) list.add(convertJsonNode(item));
                return list;
            }
            if (node.isObject()) {
                Map<String, Object> map = new LinkedHashMap<>();
                node.fields().forEachRemaining(e -> map.put(e.getKey(), convertJsonNode(e.getValue())));
                return map;
            }
            return node.toString();
        }

        private MessageSegment createSegment(String type) {
            if (type == null || type.isEmpty()) return null;
            return switch (type) {
                case "text" -> new TextSegment();
                case "at" -> new AtSegment();
                case "image" -> new ImageSegment();
                case "face" -> new FaceSegment();
                case "reply" -> new ReplySegment();
                case "record" -> new RecordSegment();
                case "video" -> new VideoSegment();
                case "file" -> new FileSegment();
                case "markdown" -> new MarkdownSegment();
                case "json" -> new JsonSegment();
                case "xml" -> new XmlSegment();
                case "node" -> new NodeSegment();
                case "forward" -> new ForwardSegment();
                default -> null;
            };
        }

        private MessageChain deserializeString(String text) {
            MessageChain chain = new MessageChain();
            if (text == null || text.isEmpty()) return chain;

            Matcher matcher = CQ_CODE_PATTERN.matcher(text);
            int lastEnd = 0;

            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    chain.text(text.substring(lastEnd, matcher.start()));
                }
                String type = matcher.group(1);
                String paramStr = matcher.group(2);
                Map<String, String> params = new LinkedHashMap<>();
                if (paramStr != null && !paramStr.isEmpty()) {
                    for (String pair : paramStr.split(",")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) {
                            params.put(kv[0], kv[1]);
                        }
                    }
                }
                MessageSegment seg = createSegmentFromCq(type, params);
                if (seg != null) chain.add(seg);
                lastEnd = matcher.end();
            }

            if (lastEnd < text.length()) {
                chain.text(text.substring(lastEnd));
            }
            return chain;
        }

        private MessageSegment createSegmentFromCq(String type, Map<String, String> params) {
            return switch (type) {
                case "text" -> {
                    TextSegment seg = new TextSegment();
                    seg.setDataValue("text", params.getOrDefault("text", ""));
                    yield seg;
                }
                case "at" -> {
                    AtSegment seg = new AtSegment();
                    seg.setDataValue("qq", params.getOrDefault("qq", "0"));
                    yield seg;
                }
                case "image" -> {
                    ImageSegment seg = new ImageSegment();
                    seg.setDataValue("file", params.getOrDefault("file", ""));
                    seg.setDataValue("url", params.get("url"));
                    yield seg;
                }
                case "face" -> {
                    FaceSegment seg = new FaceSegment();
                    seg.setDataValue("id", params.getOrDefault("id", "0"));
                    yield seg;
                }
                case "reply" -> {
                    ReplySegment seg = new ReplySegment();
                    seg.setDataValue("id", params.getOrDefault("id", "0"));
                    yield seg;
                }
                case "record" -> {
                    RecordSegment seg = new RecordSegment();
                    seg.setDataValue("file", params.getOrDefault("file", ""));
                    yield seg;
                }
                case "video" -> {
                    VideoSegment seg = new VideoSegment();
                    seg.setDataValue("file", params.getOrDefault("file", ""));
                    yield seg;
                }
                case "file" -> {
                    FileSegment seg = new FileSegment();
                    seg.setDataValue("file", params.getOrDefault("file", ""));
                    seg.setDataValue("name", params.get("name"));
                    yield seg;
                }
                case "json" -> {
                    JsonSegment seg = new JsonSegment();
                    seg.setDataValue("data", params.getOrDefault("data", ""));
                    yield seg;
                }
                case "xml" -> {
                    XmlSegment seg = new XmlSegment();
                    seg.setDataValue("data", params.getOrDefault("data", ""));
                    yield seg;
                }
                case "markdown" -> {
                    MarkdownSegment seg = new MarkdownSegment();
                    seg.setDataValue("content", params.getOrDefault("content", ""));
                    yield seg;
                }
                case "node" -> {
                    NodeSegment seg = new NodeSegment();
                    seg.setDataValue("user_id", params.getOrDefault("user_id", "0"));
                    seg.setDataValue("nickname", params.getOrDefault("nickname", ""));
                    seg.setDataValue("id", params.getOrDefault("id", ""));
                    yield seg;
                }
                case "forward" -> {
                    ForwardSegment seg = new ForwardSegment();
                    seg.setDataValue("id", params.getOrDefault("id", ""));
                    yield seg;
                }
                default -> {
                    UnknownSegment seg = new UnknownSegment(type);
                    params.forEach(seg::setDataValue);
                    yield seg;
                }
            };
        }
    }
}
