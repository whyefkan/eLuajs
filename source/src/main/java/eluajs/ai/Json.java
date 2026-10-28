package eluajs.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small, dependency-free JSON reader/writer used by the AI clients. */
public final class Json {
    private Json() {}

    public static String quote(String value) {
        if (value == null) return "null";
        StringBuilder b = new StringBuilder(value.length() + 16);
        b.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object value = p.value();
        p.ws();
        if (p.i < p.s.length()) throw new IllegalArgumentException("Unexpected JSON at " + p.i);
        return value;
    }

    public static Object at(Object node, Object... path) {
        Object cur = node;
        for (Object part : path) {
            if (cur == null) return null;
            if (part instanceof Integer index && cur instanceof List<?> list) {
                cur = index >= 0 && index < list.size() ? list.get(index) : null;
            } else if (part instanceof String key && cur instanceof Map<?, ?> map) {
                cur = map.get(key);
            } else {
                return null;
            }
        }
        return cur;
    }

    public static String stringAt(Object node, Object... path) {
        Object value = at(node, path);
        return value == null ? null : String.valueOf(value);
    }

    private static final class Parser {
        private final String s;
        private int i;
        Parser(String s) { this.s = s == null ? "" : s; }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        Object value() {
            ws();
            if (i >= s.length()) return null;
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

        private Object literal(String word, Object value) {
            if (!s.startsWith(word, i)) throw new IllegalArgumentException("Invalid JSON literal at " + i);
            i += word.length();
            return value;
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return map; }
            while (true) {
                ws();
                String key = string();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') throw new IllegalArgumentException("Expected ':' at " + i);
                i++;
                map.put(key, value());
                ws();
                if (i >= s.length()) throw new IllegalArgumentException("Unterminated object");
                char c = s.charAt(i++);
                if (c == '}') return map;
                if (c != ',') throw new IllegalArgumentException("Expected ',' at " + (i - 1));
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++;
            ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return list; }
            while (true) {
                list.add(value());
                ws();
                if (i >= s.length()) throw new IllegalArgumentException("Unterminated array");
                char c = s.charAt(i++);
                if (c == ']') return list;
                if (c != ',') throw new IllegalArgumentException("Expected ',' at " + (i - 1));
            }
        }

        private String string() {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw new IllegalArgumentException("Expected string at " + i);
            i++;
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case '"' -> b.append('"');
                    case '\\' -> b.append('\\');
                    case '/' -> b.append('/');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw new IllegalArgumentException("Bad unicode escape");
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad escape \\" + e);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Object number() {
            int start = i;
            while (i < s.length() && "-+0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalArgumentException("Unexpected character at " + i);
            String raw = s.substring(start, i);
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) return Double.parseDouble(raw);
            try { return Long.parseLong(raw); } catch (NumberFormatException e) { return Double.parseDouble(raw); }
        }
    }
}
