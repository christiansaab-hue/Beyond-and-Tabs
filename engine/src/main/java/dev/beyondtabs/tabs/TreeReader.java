package dev.beyondtabs.tabs;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes an object's bytes by walking its type tree. Structs become maps, arrays lists (byte and float arrays become
 * byte[]/float[] for speed), PPtrs become long[]{fileID, pathID}. Throws if the data does not match the layout exactly.
 */
public final class TreeReader {
    final ByteBuffer b;
    public TreeReader(ByteBuffer b) { this.b = b; }

    public static Map<String, Object> read(ByteBuffer data, TypeNode root) {
        TreeReader r = new TreeReader(data);
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) r.node(root);
        if (r.b.remaining() != 0) throw new IllegalStateException(root.type + ": " + r.b.remaining() + " bytes left after reading (layout mismatch)");
        return m;
    }

    void align() { int p = b.position(); b.position((p + 3) & ~3); }

    Object node(TypeNode n) {
        Object v = value(n);
        if (n.align) align();
        return v;
    }

    Object value(TypeNode n) {
        switch (n.type) {
            case "bool": return b.get() != 0;
            case "SInt8": return (int) b.get();
            case "UInt8": case "char": return b.get() & 255;
            case "SInt16": case "short": return (int) b.getShort();
            case "UInt16": case "unsigned short": return b.getShort() & 0xffff;
            case "int": case "SInt32": case "Type*": return b.getInt();
            case "unsigned int": case "UInt32": return b.getInt() & 0xffffffffL;
            case "SInt64": case "long long": case "UInt64": case "unsigned long long": case "FileSize": return b.getLong();
            case "float": return b.getFloat();
            case "double": return b.getDouble();
            case "string": { int len = b.getInt(); byte[] a = new byte[len]; b.get(a); align(); return new String(a, java.nio.charset.StandardCharsets.UTF_8); }
            case "TypelessData": { int len = b.getInt(); byte[] a = new byte[len]; b.get(a); return a; }
            default:
                if (n.type.startsWith("Skip")) { b.position(b.position() + Integer.parseInt(n.type.substring(4))); return null; }
                if (n.type.startsWith("PPtr<")) return new long[]{b.getInt(), b.getLong()};
                if (n.children.size() == 1 && n.children.get(0).type.equals("Array")) return array(n.children.get(0));
                if (n.type.equals("Array")) return array(n);
                Map<String, Object> m = new LinkedHashMap<>();
                for (TypeNode c : n.children) m.put(c.name, node(c));
                return m;
        }
    }

    Object array(TypeNode arr) {
        int size = b.getInt();
        if (size < 0 || size > 50_000_000) throw new IllegalStateException("bad array size " + size);
        TypeNode e = arr.children.get(1);
        Object out;
        if ((e.type.equals("UInt8") || e.type.equals("char") || e.type.equals("SInt8")) && e.children.isEmpty()) { byte[] a = new byte[size]; b.get(a); out = a; }
        else if (e.type.equals("float") && e.children.isEmpty()) { float[] a = new float[size]; for (int i = 0; i < size; i++) a[i] = b.getFloat(); out = a; }
        else { List<Object> l = new ArrayList<>(size); for (int i = 0; i < size; i++) l.add(node(e)); out = l; }
        if (arr.align) align();
        return out;
    }
}
