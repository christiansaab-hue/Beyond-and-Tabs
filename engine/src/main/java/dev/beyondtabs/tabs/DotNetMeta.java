package dev.beyondtabs.tabs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal ECMA-335 metadata reader for a managed .dll on the player's PC: types, fields, field signatures and custom
 * attributes — just enough to work out how a game's scripts are serialized. Read-only.
 */
public final class DotNetMeta {
    public final String name;
    final ByteBuffer b;
    int strOff, blobOff;
    final int[] rows = new int[64];
    final int[] tableOff = new int[64], rowSize = new int[64];
    boolean bigStr, bigGuid, bigBlob;

    public final List<TypeDefRow> typeDefs = new ArrayList<>();
    public final List<TypeRefRow> typeRefs = new ArrayList<>();
    public final List<FieldRow> fields = new ArrayList<>();
    public final Map<String, TypeDefRow> byName = new HashMap<>();
    /** Attribute type names per field row (1-based field index). */
    public final Map<Integer, List<String>> fieldAttrs = new HashMap<>();

    public static final class TypeDefRow { public int index, flags, extendsTag, extendsIdx, fieldStart, fieldEnd; public String name, ns; }
    public static final class TypeRefRow { public int index; public String name, ns; }
    public static final class FieldRow { public int index, flags; public String name; public byte[] sig; }

    public DotNetMeta(Path dll) throws IOException {
        name = dll.getFileName().toString();
        b = ByteBuffer.wrap(Files.readAllBytes(dll)).order(ByteOrder.LITTLE_ENDIAN);
        int pe = b.getInt(0x3C);
        if (b.getInt(pe) != 0x00004550) throw new IOException("not a PE file: " + dll);
        int coff = pe + 4, nSections = b.getShort(coff + 2) & 0xffff, optSize = b.getShort(coff + 16) & 0xffff;
        int opt = coff + 20, magic = b.getShort(opt) & 0xffff;
        int dd = opt + (magic == 0x20b ? 112 : 96);
        int cliRva = b.getInt(dd + 14 * 8);
        int sec = opt + optSize;
        int[][] sections = new int[nSections][];
        for (int i = 0; i < nSections; i++) { int s = sec + i * 40; sections[i] = new int[]{b.getInt(s + 12), b.getInt(s + 8), b.getInt(s + 20)}; }
        int cli = rva(sections, cliRva);
        int meta = rva(sections, b.getInt(cli + 8));
        if (b.getInt(meta) != 0x424A5342) throw new IOException("no CLI metadata in " + dll);
        int verLen = b.getInt(meta + 12), p = meta + 16 + verLen;
        int nStreams = b.getShort(p + 2) & 0xffff; p += 4;
        int tablesOff = -1;
        for (int i = 0; i < nStreams; i++) {
            int off = b.getInt(p), size = b.getInt(p + 4); p += 8;
            int st = p; while (b.get(p) != 0) p++;
            String sn = new String(b.array(), st, p - st, StandardCharsets.US_ASCII);
            p = (p + 4) & ~3;
            switch (sn) { case "#~", "#-" -> tablesOff = meta + off; case "#Strings" -> strOff = meta + off; case "#Blob" -> blobOff = meta + off; default -> { } }
        }
        if (tablesOff < 0) throw new IOException("no metadata tables in " + dll);
        int heap = b.get(tablesOff + 6) & 0xff; bigStr = (heap & 1) != 0; bigGuid = (heap & 2) != 0; bigBlob = (heap & 4) != 0;
        long valid = b.getLong(tablesOff + 8);
        int q = tablesOff + 24;
        for (int t = 0; t < 64; t++) if ((valid >>> t & 1) != 0) { rows[t] = b.getInt(q); q += 4; }
        computeRowSizes();
        for (int t = 0; t < 64; t++) { tableOff[t] = q; q += rows[t] * rowSize[t]; }
        readTypeRefs(); readFields(); readTypeDefs(); readCustomAttributes();
    }

    static int rva(int[][] sections, int rva) {
        for (int[] s : sections) if (rva >= s[0] && rva < s[0] + Math.max(s[1], 1)) return rva - s[0] + s[2];
        throw new IllegalStateException("RVA outside sections: " + rva);
    }

    // ---------------------------------------------------------------- sizes
    int idx(int table) { return rows[table] < 65536 ? 2 : 4; }
    int coded(int bits, int... tables) { int max = 0; for (int t : tables) if (t >= 0) max = Math.max(max, rows[t]); return max < (1 << (16 - bits)) ? 2 : 4; }
    int str() { return bigStr ? 4 : 2; } int guid() { return bigGuid ? 4 : 2; } int blob() { return bigBlob ? 4 : 2; }

    static final int[] HAS_CA = {6, 4, 1, 2, 8, 9, 10, 0, 14, 23, 20, 17, 26, 27, 32, 35, 38, 39, 40, 42, 44, 43};

    void computeRowSizes() {
        int typeDefOrRef = coded(2, 2, 1, 27), resScope = coded(2, 0, 26, 35, 1), memberRefParent = coded(3, 2, 1, 26, 6, 27);
        int hasConst = coded(2, 4, 8, 23), hasCA = coded(5, HAS_CA), caType = coded(3, 6, 10), hasFM = coded(1, 4, 8), hasDS = coded(2, 2, 6, 32);
        int hasSem = coded(1, 20, 23), mdor = coded(1, 6, 10), memberFwd = coded(1, 4, 6), impl = coded(2, 38, 35, 39), tomd = coded(1, 2, 6);
        int[] s = rowSize;
        s[0x00] = 2 + str() + guid() * 3;
        s[0x01] = resScope + str() * 2;
        s[0x02] = 4 + str() * 2 + typeDefOrRef + idx(4) + idx(6);
        s[0x03] = idx(4);
        s[0x04] = 2 + str() + blob();
        s[0x05] = idx(6);
        s[0x06] = 4 + 2 + 2 + str() + blob() + idx(8);
        s[0x07] = idx(8);
        s[0x08] = 2 + 2 + str();
        s[0x09] = idx(2) + typeDefOrRef;
        s[0x0A] = memberRefParent + str() + blob();
        s[0x0B] = 2 + hasConst + blob();
        s[0x0C] = hasCA + caType + blob();
        s[0x0D] = hasFM + blob();
        s[0x0E] = 2 + hasDS + blob();
        s[0x0F] = 2 + 4 + idx(2);
        s[0x10] = 4 + idx(4);
        s[0x11] = blob();
        s[0x12] = idx(2) + idx(20);
        s[0x13] = idx(20);
        s[0x14] = 2 + str() + typeDefOrRef;
        s[0x15] = idx(2) + idx(23);
        s[0x16] = idx(23);
        s[0x17] = 2 + str() + blob();
        s[0x18] = 2 + idx(6) + hasSem;
        s[0x19] = idx(2) + mdor * 2;
        s[0x1A] = str();
        s[0x1B] = blob();
        s[0x1C] = 2 + memberFwd + str() + idx(26);
        s[0x1D] = 4 + idx(4);
        s[0x1E] = 8; s[0x1F] = 4;
        s[0x20] = 4 + 8 + 4 + blob() + str() * 2;
        s[0x21] = 4; s[0x22] = 12;
        s[0x23] = 8 + 4 + blob() + str() * 2 + blob();
        s[0x24] = 4 + idx(35); s[0x25] = 12 + idx(35);
        s[0x26] = 4 + str() + blob();
        s[0x27] = 8 + str() * 2 + impl;
        s[0x28] = 8 + str() + impl;
        s[0x29] = idx(2) * 2;
        s[0x2A] = 4 + tomd + str();
        s[0x2B] = mdor + blob();
        s[0x2C] = idx(42) + typeDefOrRef;
    }

    // ---------------------------------------------------------------- readers
    int u(int pos, int size) { return size == 2 ? b.getShort(pos) & 0xffff : b.getInt(pos); }
    String string(int index) {
        int p = strOff + index, st = p; while (b.get(p) != 0) p++;
        return new String(b.array(), st, p - st, StandardCharsets.UTF_8);
    }
    byte[] blobAt(int index) {
        int p = blobOff + index, first = b.get(p) & 0xff, len;
        if ((first & 0x80) == 0) { len = first; p += 1; }
        else if ((first & 0xC0) == 0x80) { len = ((first & 0x3f) << 8) | (b.get(p + 1) & 0xff); p += 2; }
        else { len = ((first & 0x1f) << 24) | ((b.get(p + 1) & 0xff) << 16) | ((b.get(p + 2) & 0xff) << 8) | (b.get(p + 3) & 0xff); p += 4; }
        byte[] out = new byte[len]; System.arraycopy(b.array(), p, out, 0, len); return out;
    }

    void readTypeRefs() {
        int resScope = coded(2, 0, 26, 35, 1);
        for (int i = 0; i < rows[0x01]; i++) {
            int p = tableOff[0x01] + i * rowSize[0x01] + resScope;
            TypeRefRow r = new TypeRefRow(); r.index = i + 1; r.name = string(u(p, str())); r.ns = string(u(p + str(), str()));
            typeRefs.add(r);
        }
    }

    void readFields() {
        for (int i = 0; i < rows[0x04]; i++) {
            int p = tableOff[0x04] + i * rowSize[0x04];
            FieldRow f = new FieldRow(); f.index = i + 1; f.flags = b.getShort(p) & 0xffff;
            f.name = string(u(p + 2, str())); f.sig = blobAt(u(p + 2 + str(), blob()));
            fields.add(f);
        }
    }

    void readTypeDefs() {
        int tdor = coded(2, 2, 1, 27);
        for (int i = 0; i < rows[0x02]; i++) {
            int p = tableOff[0x02] + i * rowSize[0x02];
            TypeDefRow t = new TypeDefRow(); t.index = i + 1; t.flags = b.getInt(p); p += 4;
            t.name = string(u(p, str())); p += str(); t.ns = string(u(p, str())); p += str();
            int ext = u(p, tdor); p += tdor; t.extendsTag = ext & 3; t.extendsIdx = ext >>> 2;
            t.fieldStart = u(p, idx(4));
            typeDefs.add(t);
        }
        for (int i = 0; i < typeDefs.size(); i++) typeDefs.get(i).fieldEnd = i + 1 < typeDefs.size() ? typeDefs.get(i + 1).fieldStart : rows[0x04] + 1;
        for (TypeDefRow t : typeDefs) byName.putIfAbsent(t.name, t);
    }

    void readCustomAttributes() {
        int hasCA = coded(5, HAS_CA), caType = coded(3, 6, 10), mrp = coded(3, 2, 1, 26, 6, 27);
        for (int i = 0; i < rows[0x0C]; i++) {
            int p = tableOff[0x0C] + i * rowSize[0x0C];
            int parent = u(p, hasCA), type = u(p + hasCA, caType);
            if ((parent & 31) != 1) continue;   // only attributes on fields matter here
            int field = parent >>> 5;
            String attr = null;
            if ((type & 7) == 3) {   // MemberRef -> its class
                int mr = type >>> 3, mp = tableOff[0x0A] + (mr - 1) * rowSize[0x0A], cls = u(mp, mrp);
                int tag = cls & 7, ci = cls >>> 3;
                if (tag == 1 && ci >= 1 && ci <= typeRefs.size()) attr = typeRefs.get(ci - 1).name;
                else if (tag == 0 && ci >= 1 && ci <= typeDefs.size()) attr = typeDefs.get(ci - 1).name;
            } else if ((type & 7) == 2) {   // MethodDef in this assembly: find the owning type
                int md = type >>> 3; attr = ownerOfMethod(md);
            }
            if (attr != null) fieldAttrs.computeIfAbsent(field, k -> new ArrayList<>()).add(attr);
        }
    }

    String ownerOfMethod(int md) {
        int methodListOff = 4 + str() * 2 + coded(2, 2, 1, 27) + idx(4);
        String owner = null;
        for (int i = 0; i < rows[0x02]; i++) {
            int start = u(tableOff[0x02] + i * rowSize[0x02] + methodListOff, idx(6));
            if (start <= md) owner = typeDefs.get(i).name; else break;
        }
        return owner;
    }

    /** Base type of a type: {namespace, name} or null. */
    public String[] baseOf(TypeDefRow t) {
        if (t.extendsIdx == 0) return null;
        if (t.extendsTag == 0) { TypeDefRow d = typeDefs.get(t.extendsIdx - 1); return new String[]{d.ns, d.name}; }
        if (t.extendsTag == 1) { TypeRefRow r = typeRefs.get(t.extendsIdx - 1); return new String[]{r.ns, r.name}; }
        return new String[]{"", "?"};
    }
}
