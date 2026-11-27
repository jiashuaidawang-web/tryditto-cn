package cn.aiditto.domain.reasoning.v4;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A small JSON reader and writer for the fixed reasoning object. It is not a general parser. */
final class ReasoningJson {
    private ReasoningJson() {}

    static Object parse(String text) {
        if (text == null) return null;
        return new Parser(text.trim()).parseValue();
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            out.append('"').append(escape(text)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                write(out, String.valueOf(entry.getKey()));
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(',');
                write(out, list.get(i));
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("unsupported json value");
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) { this.text = text; }

        private Object parseValue() {
            skip();
            if (index >= text.length()) throw new IllegalArgumentException("empty json");
            char c = text.charAt(index);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') { index += 4; return null; }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            index++;
            Map<String, Object> map = new LinkedHashMap<>();
            skip();
            if (peek('}')) { index++; return map; }
            while (index < text.length()) {
                skip();
                String key = parseString();
                skip();
                if (text.charAt(index) != ':') throw new IllegalArgumentException("expected colon");
                index++;
                map.put(key, parseValue());
                skip();
                if (peek('}')) { index++; return map; }
                if (text.charAt(index) != ',') throw new IllegalArgumentException("expected comma");
                index++;
            }
            throw new IllegalArgumentException("object");
        }

        private List<Object> parseArray() {
            index++;
            List<Object> list = new ArrayList<>();
            skip();
            if (peek(']')) { index++; return list; }
            while (index < text.length()) {
                list.add(parseValue());
                skip();
                if (peek(']')) { index++; return list; }
                if (text.charAt(index) != ',') throw new IllegalArgumentException("expected comma");
                index++;
                skip();
            }
            throw new IllegalArgumentException("array");
        }

        private String parseString() {
            if (text.charAt(index) != '"') throw new IllegalArgumentException("expected string");
            index++;
            StringBuilder out = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') return out.toString();
                if (c == '\\') {
                    char escaped = text.charAt(index++);
                    out.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'n' -> '\n';
                        default -> escaped;
                    });
                } else {
                    out.append(c);
                }
            }
            throw new IllegalArgumentException("string");
        }

        private Boolean parseBoolean() {
            if (text.startsWith("true", index)) { index += 4; return true; }
            if (text.startsWith("false", index)) { index += 5; return false; }
            throw new IllegalArgumentException("boolean");
        }

        private Double parseNumber() {
            int start = index;
            while (index < text.length() && "-0123456789.eE".indexOf(text.charAt(index)) >= 0) index++;
            return Double.valueOf(text.substring(start, index));
        }

        private void skip() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        }

        private boolean peek(char c) {
            return index < text.length() && text.charAt(index) == c;
        }
    }
}
