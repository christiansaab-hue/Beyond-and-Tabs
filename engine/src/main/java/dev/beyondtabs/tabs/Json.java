package dev.beyondtabs.tabs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON parser (objects, arrays, strings, numbers, booleans, null) so the engine stays dependency-free. */
public final class Json {
    private final String s; private int i;
    private Json(String s) { this.s = s; }
    public static Object parse(String s) { Json j = new Json(s); j.ws(); Object v = j.value(); return v; }
    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    private Object value() {
        char c = s.charAt(i);
        switch (c) {
            case '{': { i++; Map<String, Object> m = new LinkedHashMap<>(); ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) { ws(); String k = str(); ws(); i++; ws(); m.put(k, value()); ws(); if (s.charAt(i++) == '}') return m; } }
            case '[': { i++; List<Object> l = new ArrayList<>(); ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) { ws(); l.add(value()); ws(); if (s.charAt(i++) == ']') return l; } }
            case '"': return str();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: { int st = i; while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
                String n = s.substring(st, i);
                return n.contains(".") || n.contains("e") || n.contains("E") ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n); }
        }
    }
    private String str() {
        StringBuilder b = new StringBuilder(); i++;
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) { case 'n' -> b.append('\n'); case 't' -> b.append('\t'); case 'r' -> b.append('\r'); case 'b' -> b.append('\b'); case 'f' -> b.append('\f');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; } default -> b.append(e); }
            } else b.append(c);
        }
    }
}
