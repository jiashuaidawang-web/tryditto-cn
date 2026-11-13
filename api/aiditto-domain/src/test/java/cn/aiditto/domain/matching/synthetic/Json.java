package cn.aiditto.domain.matching.synthetic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Just enough JSON for the fixture: maps keep insertion order, short containers print on one line. */
final class Json {
    private static final int INLINE_WIDTH = 100;

    private Json() {}

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.append('\n').toString();
    }

    private static void write(StringBuilder sb, Object v, int indent) {
        String compact = compact(v);
        if (compact.length() + indent <= INLINE_WIDTH || !(v instanceof Map<?, ?> || v instanceof List<?>)) {
            sb.append(compact);
            return;
        }
        String pad = "  ".repeat(indent + 1);
        if (v instanceof Map<?, ?> m) {
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sb.append(pad);
                string(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                write(sb, e.getValue(), indent + 1);
                sb.append(++i < m.size() ? ",\n" : "\n");
            }
            sb.append("  ".repeat(indent)).append('}');
        } else {
            List<?> l = (List<?>) v;
            sb.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                sb.append(pad);
                write(sb, l.get(i), indent + 1);
                sb.append(i + 1 < l.size() ? ",\n" : "\n");
            }
            sb.append("  ".repeat(indent)).append(']');
        }
    }

    private static String compact(Object v) {
        StringBuilder sb = new StringBuilder();
        if (v == null) sb.append("null");
        else if (v instanceof String s) string(sb, s);
        else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) sb.append(v);
        else if (v instanceof Double d) sb.append(d);
        else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (i++ > 0) sb.append(", ");
                string(sb, String.valueOf(e.getKey()));
                sb.append(": ").append(compact(e.getValue()));
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(compact(l.get(i)));
            }
            sb.append(']');
        } else throw new IllegalArgumentException("not JSON: " + v.getClass());
        return sb.toString();
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    static Object parse(String s) {
        Parser p = new Parser(s);
        Object v = p.value();
        p.ws();
        if (p.i != s.length()) throw p.error("trailing content");
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object value() {
            ws();
            if (i >= s.length()) throw error("unexpected end");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                String k = string();
                ws();
                expect(':');
                m.put(k, value());
                ws();
                if (peek() == ',') i++;
                else {
                    expect('}');
                    return m;
                }
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (peek() == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(value());
                ws();
                if (peek() == ',') i++;
                else {
                    expect(']');
                    return l;
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw error("unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error("bad escape");
                }
            }
        }

        private Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String n = s.substring(start, i);
            if (n.isEmpty()) throw error("unexpected character");
            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
            long l = Long.parseLong(n);
            return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Object) (int) l : (Object) l;
        }

        private Object literal(String word, Object v) {
            if (!s.startsWith(word, i)) throw error("expected " + word);
            i += word.length();
            return v;
        }

        private char peek() {
            if (i >= s.length()) throw error("unexpected end");
            return s.charAt(i);
        }

        private void expect(char c) {
            if (peek() != c) throw error("expected '" + c + "'");
            i++;
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at offset " + i);
        }
    }
}
