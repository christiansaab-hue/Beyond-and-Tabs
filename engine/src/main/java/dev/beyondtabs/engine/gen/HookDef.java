// GENERATED from design/hooks.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record HookDef(String id, String mcApi, String purpose, String side) {
    public static final HookDef RTS_DIMENSION = new HookDef("rts_dimension", "Forge DimensionType + ChunkGenerator", "Match world: flat-ish generated battle maps, no mobs, no weather damage", "server");
    public static final HookDef UNIT_ENTITY = new HookDef("unit_entity", "Entity (non-living mirror)", "One lightweight entity per unit for networking/visibility; sim owns state", "both");
    public static final HookDef UNIT_RENDERER = new HookDef("unit_renderer", "EntityRenderer + RenderType.entityCutoutNoCull", "Draw extracted TABS meshes skinned to ragdoll pose; icons when zoomed out", "client");
    public static final HookDef RTS_CAMERA = new HookDef("rts_camera", "ViewportEvent.ComputeCameraAngles + CameraType override", "Free top-down camera with strategic zoom", "client");
    public static final HookDef RTS_INPUT = new HookDef("rts_input", "InputEvent + KeyMapping", "Mouse picking, box select, orders, rebindable keys", "client");
    public static final HookDef HUD = new HookDef("hud", "RegisterGuiOverlaysEvent", "Resource bar, minimap, build menu, selection panel", "client");
    public static final HookDef BLOCK_COLLISION = new HookDef("block_collision", "Level.getBlockState via cached heightfield", "Physics ground + walls, refreshed when blocks change", "server");
    public static final HookDef STRUCTURES = new HookDef("structures", "StructureTemplate placement", "Buildings as block structures per race/level", "server");
    public static final HookDef TABS_ASSETS = new HookDef("tabs_assets", "Local resource pack + mod config dir", "Extracted TABS meshes/textures/sounds from the player's install", "client");
    public static final HookDef NETWORKING = new HookDef("networking", "SimpleChannel packets", "Orders client->server; batched unit snapshots server->client", "both");
    public static final HookDef TICK = new HookDef("tick", "TickEvent.ServerTickEvent / ClientTickEvent", "Step sim; interpolate render", "both");
    public static final HookDef FPS_LIMITER = new HookDef("fps_limiter", "Options.framerateLimit", "Apply performance fps caps during matches", "client");
    public static final List<HookDef> ALL = List.of(RTS_DIMENSION, UNIT_ENTITY, UNIT_RENDERER, RTS_CAMERA, RTS_INPUT, HUD, BLOCK_COLLISION, STRUCTURES, TABS_ASSETS, NETWORKING, TICK, FPS_LIMITER);
    private static final Map<String, HookDef> BY_ID = new LinkedHashMap<>();
    static { for (HookDef x : ALL) BY_ID.put(x.id(), x); }
    public static HookDef byId(String id) { HookDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown hooks id " + id); return x; }
}
