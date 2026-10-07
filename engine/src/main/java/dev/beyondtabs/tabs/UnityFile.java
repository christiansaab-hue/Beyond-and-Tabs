package dev.beyondtabs.tabs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a Unity serialized file (.assets, format 17-22, as used by Unity 2018-2021) from the player's own game install.
 * Memory-maps the file; objects are decoded on demand with a {@link TreeReader}. Read-only: nothing is ever written back.
 */
public final class UnityFile implements AutoCloseable {
    public final Path path; public final int format; public final String unityVersion; public final boolean typeTrees;
    final ByteBuffer buf; final long dataOffset; final FileChannel channel;
    public final List<String> externals = new ArrayList<>();
    public final Map<Long, Obj> objects = new LinkedHashMap<>();
    final List<Integer> typeClassIds = new ArrayList<>();
    final List<Integer> typeScriptIndex = new ArrayList<>();
    public final List<long[]> scriptTypes = new ArrayList<>();   // [fileIndex, pathId]

    public record Obj(long pathId, long offset, int size, int classId, int typeIndex) { }

    public UnityFile(Path path) throws IOException {
        this.path = path;
        channel = new RandomAccessFile(path.toFile(), "r").getChannel();
        buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        buf.order(ByteOrder.BIG_ENDIAN);
        int metaSize = buf.getInt(0); long fileSize = buf.getInt(4) & 0xffffffffL; format = buf.getInt(8); long dataOff = buf.getInt(12) & 0xffffffffL;
        if (format < 17 || format > 22) throw new IOException("Unsupported Unity serialized format " + format + " in " + path);
        int pos = 16;
        boolean big = buf.get(pos) != 0; pos += 4;
        if (format >= 22) { metaSize = buf.getInt(pos); fileSize = buf.getLong(pos + 4); dataOff = buf.getLong(pos + 12); pos += 28; }
        dataOffset = dataOff;
        buf.order(big ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        Cursor c = new Cursor(buf, pos);
        unityVersion = c.cstr(); c.i32(); // target platform
        typeTrees = c.u8() != 0;
        if (typeTrees) throw new IOException("Files with embedded type trees are not supported yet: " + path);
        int types = c.i32();
        for (int t = 0; t < types; t++) {
            int classId = c.i32(); c.u8(); int scriptIdx = c.i16();
            if (classId == 114 || classId < 0) c.skip(16);
            c.skip(16);   // old type hash
            typeClassIds.add(classId); typeScriptIndex.add(scriptIdx);
        }
        int count = c.i32();
        for (int o = 0; o < count; o++) {
            c.align4(); long pid = c.i64();
            long start = format >= 22 ? c.i64() : (c.i32() & 0xffffffffL);
            int size = c.i32(); int typeIdx = c.i32();
            objects.put(pid, new Obj(pid, dataOffset + start, size, typeClassIds.get(typeIdx), typeIdx));
        }
        int scripts = c.i32();
        for (int s = 0; s < scripts; s++) { int fi = c.i32(); c.align4(); long id = c.i64(); scriptTypes.add(new long[]{fi, id}); }
        int ext = c.i32();
        for (int e = 0; e < ext; e++) { c.cstr(); c.skip(16); c.i32(); externals.add(c.cstr()); }
    }

    /** Index into {@link #scriptTypes} of a MonoBehaviour object's script (-1 if none). */
    public int scriptIndexOf(Obj o) { return typeScriptIndex.get(o.typeIndex()); }

    /** External file name for a PPtr fileID (0 = this file). */
    public String external(int fileId) { return fileId == 0 ? path.getFileName().toString() : externals.get(fileId - 1); }

    public ByteBuffer slice(Obj o) {
        ByteBuffer b = buf.duplicate().order(buf.order());
        b.position((int) o.offset()); b.limit((int) (o.offset() + o.size()));
        return b.slice().order(buf.order());
    }

    @Override public void close() throws IOException { channel.close(); }

    static final class Cursor {
        final ByteBuffer b; int p;
        Cursor(ByteBuffer b, int p) { this.b = b; this.p = p; }
        int u8() { return b.get(p++) & 255; }
        int i16() { short v = b.getShort(p); p += 2; return v; }
        int i32() { int v = b.getInt(p); p += 4; return v; }
        long i64() { long v = b.getLong(p); p += 8; return v; }
        void skip(int n) { p += n; }
        void align4() { p = (p + 3) & ~3; }
        String cstr() { int st = p; while (b.get(p) != 0) p++; byte[] a = new byte[p - st]; b.get(st, a); p++; return new String(a, java.nio.charset.StandardCharsets.UTF_8); }
    }
}
