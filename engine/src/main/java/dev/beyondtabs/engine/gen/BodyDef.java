// GENERATED from design/bodies.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record BodyDef(String id, int particles, int constraints, int hpBase, double mass, double moveSpeed, double radius) {
    public static final BodyDef HUMANOID = new BodyDef("humanoid", 11, 14, 200, 1.0, 3.2, 0.35);
    public static final BodyDef LARGE = new BodyDef("large", 11, 14, 1400, 4.0, 2.8, 0.6);
    public static final BodyDef QUADRUPED_LARGE = new BodyDef("quadruped_large", 14, 20, 6000, 20.0, 2.6, 1.4);
    public static final BodyDef MOUNTED = new BodyDef("mounted", 16, 22, 900, 6.0, 6.5, 0.8);
    public static final BodyDef VEHICLE = new BodyDef("vehicle", 6, 9, 400, 3.0, 2.8, 0.6);
    public static final BodyDef VEHICLE_LARGE = new BodyDef("vehicle_large", 10, 16, 4000, 15.0, 2.2, 1.4);
    public static final BodyDef SIEGE = new BodyDef("siege", 6, 9, 600, 6.0, 1.6, 0.9);
    public static final BodyDef FLYER = new BodyDef("flyer", 8, 10, 250, 1.0, 3.5, 0.5);
    public static final BodyDef FLYER_LARGE = new BodyDef("flyer_large", 14, 20, 5000, 12.0, 4.5, 1.6);
    public static final List<BodyDef> ALL = List.of(HUMANOID, LARGE, QUADRUPED_LARGE, MOUNTED, VEHICLE, VEHICLE_LARGE, SIEGE, FLYER, FLYER_LARGE);
    private static final Map<String, BodyDef> BY_ID = new LinkedHashMap<>();
    static { for (BodyDef x : ALL) BY_ID.put(x.id(), x); }
    public static BodyDef byId(String id) { BodyDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown bodies id " + id); return x; }
}
