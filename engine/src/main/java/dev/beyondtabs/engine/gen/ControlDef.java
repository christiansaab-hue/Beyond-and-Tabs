// GENERATED from design/controls.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record ControlDef(String id, String binding, String effect) {
    public static final ControlDef CAMERA_PAN = new ControlDef("camera_pan", "WASD / screen edge / middle-drag", "Move RTS camera");
    public static final ControlDef STRATEGIC_ZOOM = new ControlDef("strategic_zoom", "Mouse wheel", "Zoom from 4 to 400 blocks; icons replace units past icon_zoom_distance");
    public static final ControlDef BOX_SELECT = new ControlDef("box_select", "Left drag", "Select units");
    public static final ControlDef DOUBLE_CLICK_SELECT = new ControlDef("double_click_select", "Double left click", "Select all on-screen units of that type");
    public static final ControlDef MOVE = new ControlDef("move", "Right click", "Move order");
    public static final ControlDef ATTACK_MOVE = new ControlDef("attack_move", "A + right click", "Fight while moving");
    public static final ControlDef SHIFT_QUEUE = new ControlDef("shift_queue", "Shift + any order", "Append to command queue");
    public static final ControlDef FORMATION_DRAG = new ControlDef("formation_drag", "Right drag", "Spread units along a dragged line");
    public static final ControlDef AREA_ATTACK = new ControlDef("area_attack", "Ctrl + A + drag circle", "Attack everything in area");
    public static final ControlDef AREA_RECLAIM = new ControlDef("area_reclaim", "E + drag circle", "Builders reclaim wrecks in area");
    public static final ControlDef PATROL = new ControlDef("patrol", "P + right click", "Patrol between points");
    public static final ControlDef GUARD = new ControlDef("guard", "G + right click", "Guard a unit or building");
    public static final ControlDef STOP = new ControlDef("stop", "S", "Clear orders");
    public static final ControlDef CONTROL_GROUP_SET = new ControlDef("control_group_set", "Ctrl + 1-9", "Save selection to group");
    public static final ControlDef CONTROL_GROUP_SELECT = new ControlDef("control_group_select", "1-9 (twice to centre)", "Recall group");
    public static final ControlDef FACTORY_REPEAT = new ControlDef("factory_repeat", "Factory panel: Repeat", "Loop the build queue");
    public static final ControlDef RALLY_POINT = new ControlDef("rally_point", "Right click with factory selected", "Units walk here when built");
    public static final ControlDef BUILD_MENU = new ControlDef("build_menu", "B / factory panel", "Place buildings, shift for rows");
    public static final ControlDef UPGRADE_BUILDING = new ControlDef("upgrade_building", "U with building selected", "Upgrade to next level");
    public static final ControlDef PAUSE_SPEED = new ControlDef("pause_speed", "Pause / +/- (solo only)", "Pause or game speed");
    public static final ControlDef MINIMAP = new ControlDef("minimap", "Bottom-left", "Click to jump; shows icons and pings");
    public static final ControlDef RESOURCE_BAR = new ControlDef("resource_bar", "Top", "Metal/energy income, drain, storage");
    public static final List<ControlDef> ALL = List.of(CAMERA_PAN, STRATEGIC_ZOOM, BOX_SELECT, DOUBLE_CLICK_SELECT, MOVE, ATTACK_MOVE, SHIFT_QUEUE, FORMATION_DRAG, AREA_ATTACK, AREA_RECLAIM, PATROL, GUARD, STOP, CONTROL_GROUP_SET, CONTROL_GROUP_SELECT, FACTORY_REPEAT, RALLY_POINT, BUILD_MENU, UPGRADE_BUILDING, PAUSE_SPEED, MINIMAP, RESOURCE_BAR);
    private static final Map<String, ControlDef> BY_ID = new LinkedHashMap<>();
    static { for (ControlDef x : ALL) BY_ID.put(x.id(), x); }
    public static ControlDef byId(String id) { ControlDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown controls id " + id); return x; }
}
