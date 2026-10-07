// GENERATED from design/performance.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record PerfDef(String id, String value, String note) {
    public static final PerfDef SIM_TICK_HZ = new PerfDef("sim_tick_hz", "20", "RTS sim + physics step at Minecraft's 20 TPS; physics substeps below");
    public static final PerfDef PHYSICS_SUBSTEPS_NEAR = new PerfDef("physics_substeps_near", "2", "PBD substeps per tick for near-LOD ragdolls");
    public static final PerfDef LOD_NEAR_DISTANCE = new PerfDef("lod_near_distance", "40", "blocks from camera: full active ragdoll");
    public static final PerfDef LOD_MID_DISTANCE = new PerfDef("lod_mid_distance", "96", "reduced ragdoll (5 particles), 1 substep, every other tick");
    public static final PerfDef LOD_FAR_DISTANCE = new PerfDef("lod_far_distance", "200", "no ragdoll: point mass on flow field, animation only");
    public static final PerfDef ICON_ZOOM_DISTANCE = new PerfDef("icon_zoom_distance", "120", "camera height past which units draw as icons; mesh rendering off");
    public static final PerfDef MAX_UNITS_PER_PLAYER = new PerfDef("max_units_per_player", "300", "hard cap; supply cap normally binds first");
    public static final PerfDef PHYSICS_BUDGET_MS = new PerfDef("physics_budget_ms", "4.0", "per tick; adaptive LOD pushes distances in if exceeded");
    public static final PerfDef RENDER_BUDGET_MS = new PerfDef("render_budget_ms", "6.0", "per frame for unit meshes; instanced by unit type");
    public static final PerfDef FPS_CAP_DEFAULT = new PerfDef("fps_cap_default", "60", "frame limiter on by default to keep heat down");
    public static final PerfDef FPS_CAP_UNFOCUSED = new PerfDef("fps_cap_unfocused", "15", "when the window is in the background");
    public static final PerfDef SLEEP_VELOCITY = new PerfDef("sleep_velocity", "0.02", "ragdolls at rest stop simulating until touched");
    public static final PerfDef PATHFINDING = new PerfDef("pathfinding", "flow_field", "one flow field per destination group, cached, not A* per unit");
    public static final PerfDef SPATIAL_HASH_CELL = new PerfDef("spatial_hash_cell", "4", "blocks; neighbour queries for combat and collisions");
    public static final PerfDef THREADS = new PerfDef("threads", "2", "sim on one worker thread, render on main; no busy-waiting");
    public static final List<PerfDef> ALL = List.of(SIM_TICK_HZ, PHYSICS_SUBSTEPS_NEAR, LOD_NEAR_DISTANCE, LOD_MID_DISTANCE, LOD_FAR_DISTANCE, ICON_ZOOM_DISTANCE, MAX_UNITS_PER_PLAYER, PHYSICS_BUDGET_MS, RENDER_BUDGET_MS, FPS_CAP_DEFAULT, FPS_CAP_UNFOCUSED, SLEEP_VELOCITY, PATHFINDING, SPATIAL_HASH_CELL, THREADS);
    private static final Map<String, PerfDef> BY_ID = new LinkedHashMap<>();
    static { for (PerfDef x : ALL) BY_ID.put(x.id(), x); }
    public static PerfDef byId(String id) { PerfDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown performance id " + id); return x; }
}
