package dev.beyondtabs.tabs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Derives script layouts from the game's own assemblies and reads every MonoBehaviour of an asset file with them,
 * checking that each object is consumed exactly. Args: dataDir typeTrees.json assetFileName [className-to-dump]
 */
public final class MonoBench {
    public static void main(String[] a) throws Exception {
        Path data = Path.of(a[0]);
        @SuppressWarnings("unchecked") Map<String, Object> j = (Map<String, Object>) Json.parse(Files.readString(Path.of(a[1])));
        @SuppressWarnings("unchecked") Map<String, Object> cls = (Map<String, Object>) j.get("classes");
        TypeNode monoScript = TypeNode.fromJson(cls.get("115"));
        long t0 = System.nanoTime();
        MonoLayouts ml = new MonoLayouts();
        for (String dll : new String[]{"Assembly-CSharp.dll", "Sirenix.Serialization.dll"}) ml.add(new DotNetMeta(data.resolve("Managed").resolve(dll)));
        System.out.printf("assemblies parsed in %.0f ms%n", (System.nanoTime() - t0) / 1e6);
        Map<String, Map<Long, String>> scripts = new HashMap<>();
        try (UnityFile f = new UnityFile(data.resolve(a[2]))) {
            Map<String, int[]> stats = new TreeMap<>();
            String firstFail = null; int dumped = 0;
            for (UnityFile.Obj o : f.objects.values()) {
                if (o.classId() != 114) continue;
                long[] st = f.scriptTypes.get(scriptIndex(f, o));
                String file = f.external((int) st[0]);
                Map<Long, String> names = scripts.computeIfAbsent(file, k -> loadScripts(data.resolve(k), monoScript));
                String cn = names.get(st[1]);
                TypeNode n = cn == null ? null : ml.script(cn);
                int[] s = stats.computeIfAbsent(cn == null ? "?" : cn, k -> new int[3]);
                if (n == null) { s[2]++; continue; }
                try {
                    Map<String, Object> v = TreeReader.read(f.slice(o), n); s[0]++;
                    if (a.length > 3 && cn.equals(a[3]) && dumped++ < 2) System.out.println(cn + " #" + o.pathId() + " " + v);
                } catch (RuntimeException ex) { s[1]++; if (firstFail == null || cn.equals("UnitBlueprint")) firstFail = cn + " #" + o.pathId() + ": " + ex; }
            }
            int ok = 0, bad = 0, unk = 0;
            for (var e : stats.entrySet()) { ok += e.getValue()[0]; bad += e.getValue()[1]; unk += e.getValue()[2]; if (e.getValue()[1] > 0 || e.getValue()[0] > 50) System.out.printf("  %-40s ok %5d  failed %4d  nolayout %d%n", e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]); }
            System.out.printf("total ok %d failed %d no-layout %d  (%.0f ms)%n", ok, bad, unk, (System.nanoTime() - t0) / 1e6);
            if (firstFail != null) System.out.println("first failure: " + firstFail);
        }
    }

    static int scriptIndex(UnityFile f, UnityFile.Obj o) { return f.scriptIndexOf(o); }

    static Map<Long, String> loadScripts(Path p, TypeNode monoScript) {
        Map<Long, String> m = new HashMap<>();
        try (UnityFile f = new UnityFile(p)) {
            for (UnityFile.Obj o : f.objects.values()) if (o.classId() == 115) {
                Map<String, Object> v = TreeReader.read(f.slice(o), monoScript);
                m.put(o.pathId(), (String) v.get("m_ClassName"));
            }
        } catch (Exception e) { throw new RuntimeException(p + ": " + e, e); }
        return m;
    }
}
