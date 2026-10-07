package dev.beyondtabs.tabs;

import java.util.ArrayList;
import java.util.List;

/** One node of a Unity type tree: type name, field name, align-after flag, children. */
public final class TypeNode {
    public final String type, name; public final boolean align; public final List<TypeNode> children;
    public TypeNode(String type, String name, boolean align, List<TypeNode> children) { this.type = type; this.name = name; this.align = align; this.children = children; }

    /** From the compact JSON form [type, name, align, [children]]. */
    @SuppressWarnings("unchecked")
    public static TypeNode fromJson(Object o) {
        List<Object> l = (List<Object>) o; List<TypeNode> ch = new ArrayList<>();
        for (Object c : (List<Object>) l.get(3)) ch.add(fromJson(c));
        return new TypeNode((String) l.get(0), (String) l.get(1), ((Number) l.get(2)).intValue() != 0, ch);
    }
    public TypeNode child(String n) { for (TypeNode c : children) if (c.name.equals(n)) return c; return null; }
}
