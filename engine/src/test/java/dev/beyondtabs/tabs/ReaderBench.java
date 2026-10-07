package dev.beyondtabs.tabs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Reads every object of the classes the mod needs from a real TABS asset file and checks each layout fits exactly. */
public final class ReaderBench {
    public static void main(String[] a) throws Exception {
        Path assets = Path.of(a[0]); Path trees = Path.of(a[1]);
        @SuppressWarnings("unchecked") Map<String, Object> j = (Map<String, Object>) Json.parse(Files.readString(trees));
        @SuppressWarnings("unchecked") Map<String, Object> cls = (Map<String, Object>) j.get("classes");
        Map<Integer, TypeNode> nodes = new HashMap<>();
        for (var e : cls.entrySet()) nodes.put(Integer.parseInt(e.getKey()), TypeNode.fromJson(e.getValue()));
        long t0 = System.nanoTime();
        try (UnityFile f = new UnityFile(assets)) {
            System.out.printf("%s: format %d, Unity %s, %d objects, externals %s%n", assets.getFileName(), f.format, f.unityVersion, f.objects.size(), f.externals);
            Map<Integer, int[]> stats = new HashMap<>();   // classId -> ok, fail
            String firstFail = null;
            for (UnityFile.Obj o : f.objects.values()) {
                TypeNode n = nodes.get(o.classId());
                if (n == null || o.classId() == 114) continue;
                int[] s = stats.computeIfAbsent(o.classId(), k -> new int[2]);
                try { TreeReader.read(f.slice(o), n); s[0]++; }
                catch (RuntimeException ex) { s[1]++; if (firstFail == null) firstFail = n.type + " #" + o.pathId() + ": " + ex; }
            }
            stats.forEach((k, v) -> System.out.printf("  %-20s read %6d  failed %d%n", nodes.get(k).type, v[0], v[1]));
            if (firstFail != null) System.out.println("  first failure: " + firstFail);
            System.out.printf("  %.0f ms%n", (System.nanoTime() - t0) / 1e6);
            if (firstFail != null) System.exit(1);
        }
    }
}
