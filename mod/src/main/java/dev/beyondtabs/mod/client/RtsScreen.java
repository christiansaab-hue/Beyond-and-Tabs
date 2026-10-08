package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.beyondtabs.engine.Order;
import dev.beyondtabs.engine.gen.BuildingDef;
import dev.beyondtabs.engine.gen.TechDef;
import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.mod.Network;
import dev.beyondtabs.mod.RtsAction;
import dev.beyondtabs.mod.Snapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The RTS view's input and HUD. Stays open while the RTS camera is active (the game keeps running behind it).
 * BAR-style: left-drag box select, right-click orders, A for attack-move, Shift to queue, S stop, Ctrl+1-9 groups,
 * B build menu, U upgrade, R factory repeat, wheel zoom, WASD/edges pan, Q/E rotate, V or Esc to leave.
 */
public final class RtsScreen extends Screen {
    static final Set<Integer> selected = new LinkedHashSet<>();
    static int selectedBuilding = -1;
    static final Map<Integer, Set<Integer>> groups = new HashMap<>();
    static BuildingDef placing; static boolean attackMode, buildMenu;
    static float[] ghost;            // x,z under the cursor while placing
    static boolean ghostValid;

    double dragX = -1, dragY; boolean dragging; long lastClick; int lastGroupKey = -1; long lastGroupAt;
    /** Right-drag formation line: world start point while the right button is held. */
    static float[] lineStart, lineEnd; static boolean rightDown;
    /** Wall drag: world start of a line of 1x1 buildings. */
    static float[] wallStart;
    static boolean help; static int idleCycle;
    static final Map<Integer, float[]> bookmarks = new HashMap<>();
    final List<Btn> buttons = new ArrayList<>();
    record Btn(int x, int y, int w, int h, String label, String tip, Runnable left, Runnable right, boolean on) { }

    public RtsScreen() { super(Component.literal("RTS")); }

    @Override public boolean isPauseScreen() { return false; }

    static void pruneSelection(Snapshot s) {
        Set<Integer> alive = new java.util.HashSet<>();
        for (Snapshot.U u : s.units) if (u.alive && u.team == s.myTeam) alive.add(u.id);
        selected.retainAll(alive);
        if (selectedBuilding >= 0 && s.buildings.stream().noneMatch(b -> b.id == selectedBuilding)) selectedBuilding = -1;
    }

    // ---------------------------------------------------------------- input
    @Override public void tick() {
        Minecraft mc = Minecraft.getInstance(); long w = mc.getWindow().getWindow();
        float dt = .05f, right = 0, fwd = 0;
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_UP)) fwd += 1;
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_DOWN)) fwd -= 1;
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT)) right += 1;
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT)) right -= 1;
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_Q)) RtsCamera.yaw -= 90 * dt;
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_E)) RtsCamera.yaw += 90 * dt;
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_PAGE_UP)) RtsCamera.tiltBy(60 * dt);
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_PAGE_DOWN)) RtsCamera.tiltBy(-60 * dt);
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_END)) RtsCamera.resetView();
        // screen-edge panning (BAR style)
        double mx = mc.mouseHandler.xpos() * width / Math.max(1, mc.getWindow().getScreenWidth());
        double my = mc.mouseHandler.ypos() * height / Math.max(1, mc.getWindow().getScreenHeight());
        if (mx <= 2) right -= 1;
        if (mx >= width - 3) right += 1;
        if (my <= 2) fwd += 1;
        if (my >= height - 3) fwd -= 1;
        if (right != 0 || fwd != 0) RtsCamera.pan(right, fwd, dt);
    }

    /** Middle-drag pans the map. */
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && minimapDrag && mini != null) {
            RtsCamera.focusX = mini[3] + (float) Math.max(0, Math.min(1, (mx - mini[0]) / mini[2])) * mini[5];
            RtsCamera.focusZ = mini[4] + (float) Math.max(0, Math.min(1, (my - mini[1]) / mini[2])) * mini[5];
            return true;
        }
        if (button == 2 && hasAltDown()) {   // BAR-style: Alt + middle-drag turns and tilts the view
            RtsCamera.yaw += (float) dx * .35f; RtsCamera.tiltBy((float) dy * .35f); return true;
        }
        if (button == 2) { RtsCamera.pan((float) -dx * .05f, (float) dy * .05f, .5f); return true; }
        if (button == 1 && rightDown && lineStart != null) { float[] g = Proj.now().toGround(mx, my); if (g != null) lineEnd = new float[]{g[0], g[2]}; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    /** Wheel zooms; Alt+wheel tilts toward the horizon (Shift+Alt+wheel turns), like BAR. */
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (hasAltDown() && hasShiftDown()) { RtsCamera.yaw += (float) delta * 15; return true; }
        if (hasAltDown()) { RtsCamera.tiltBy((float) delta * 6); return true; }
        RtsCamera.zoom(delta); return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (Btn b : buttons) if (mx >= b.x && my >= b.y && mx < b.x + b.w && my < b.y + b.h) {
            if (button == 0 && b.left != null) b.left.run(); if (button == 1 && b.right != null) b.right.run();
            return true;
        }
        Snapshot s = ClientMatch.cur;
        if (mini != null && mx >= mini[0] && my >= mini[1] && mx < mini[0] + mini[2] && my < mini[1] + mini[2]) {
            float wx = mini[3] + (float) (mx - mini[0]) / mini[2] * mini[5], wz = mini[4] + (float) (my - mini[1]) / mini[2] * mini[5];
            if (button == 0) { RtsCamera.focusX = wx; RtsCamera.focusZ = wz; minimapDrag = true; }
            else if (button == 1 && !selected.isEmpty()) {
                RtsAction a = new RtsAction(); a.kind = RtsAction.Kind.ORDER; a.queue = hasShiftDown(); a.ids = ids();
                a.orderType = (attackMode ? Order.Type.ATTACK_MOVE : Order.Type.MOVE).ordinal(); a.x = wx; a.z = wz; Network.send(a); attackMode = false;
            }
            return true;
        }
        if (button == 0) {
            if (placing != null) {
                if (placing.footprint().equals("1x1") && ghost != null) { wallStart = ghost.clone(); return true; }   // drag a line of walls
                placeBuilding(); return true;
            }
            dragX = mx; dragY = my; dragging = true; return true;
        }
        if (button == 1) {
            if (placing != null) { placing = null; return true; }
            if (s == null) return true;
            Proj p = Proj.now(); float[] g = p.toGround(mx, my);
            boolean queue = hasShiftDown();
            if (!selected.isEmpty()) {
                Snapshot.U enemy = unitAt(s, p, mx, my, false);
                Snapshot.B eb = enemy == null && g != null ? buildingAt(s, g[0], g[2], false) : null;
                Snapshot.B ob = enemy == null && eb == null && g != null ? buildingAt(s, g[0], g[2], true) : null;
                if (ob != null && !ob.upgrading && (ob.progress < 1 || ob.hp < .999f)) eb = null; else ob = null;
                RtsAction a = new RtsAction(); a.kind = RtsAction.Kind.ORDER; a.queue = queue; a.ids = ids();
                if (ob != null) { a.orderType = Order.Type.BUILD.ordinal(); a.targetBuilding = ob.id; }   // finish or repair our own building
                else if (enemy != null) { a.orderType = Order.Type.ATTACK.ordinal(); a.targetUnit = enemy.id; }
                else if (eb != null) { a.orderType = Order.Type.ATTACK.ordinal(); a.targetBuilding = eb.id; }
                else if (g != null) { rightDown = true; lineStart = new float[]{g[0], g[2]}; lineEnd = null; return true; }   // sent on release (click or line)
                else return true;
                Network.send(a); attackMode = false; patrolNext = false;
                if (g != null) MatchRenderer.pingAt(g[0], g[1], g[2], a.orderType == Order.Type.ATTACK_MOVE.ordinal() || a.orderType == Order.Type.ATTACK.ordinal());
            } else if (selectedBuilding >= 0 && g != null) {
                RtsAction a = new RtsAction(); a.kind = RtsAction.Kind.RALLY; a.targetBuilding = selectedBuilding; a.x = g[0]; a.z = g[2];
                Network.send(a); MatchRenderer.pingAt(g[0], g[1], g[2], false);
            }
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1 && rightDown) {
            rightDown = false;
            if (lineStart != null && !selected.isEmpty()) {
                RtsAction a = new RtsAction(); a.kind = RtsAction.Kind.ORDER; a.queue = hasShiftDown(); a.ids = ids();
                a.orderType = (patrolNext ? Order.Type.PATROL : attackMode ? Order.Type.ATTACK_MOVE : Order.Type.MOVE).ordinal();
                a.x = lineStart[0]; a.z = lineStart[1];
                if (lineEnd != null && Math.hypot(lineEnd[0] - lineStart[0], lineEnd[1] - lineStart[1]) > 2) { a.x2 = lineEnd[0]; a.z2 = lineEnd[1]; }
                Network.send(a);
                float px = lineEnd != null ? (lineStart[0] + lineEnd[0]) / 2 : lineStart[0], pz = lineEnd != null ? (lineStart[1] + lineEnd[1]) / 2 : lineStart[1];
                MatchRenderer.pingAt(px, RtsCamera.ground(px, pz), pz, a.orderType == Order.Type.ATTACK_MOVE.ordinal());
                attackMode = false; patrolNext = false;
            }
            lineStart = lineEnd = null;
            return true;
        }
        if (button == 0 && wallStart != null) {
            float[] end = ghost != null ? ghost : wallStart;
            float dx = end[0] - wallStart[0], dz = end[1] - wallStart[1];
            int n = (int) Math.max(Math.abs(dx), Math.abs(dz));
            Snapshot s = ClientMatch.cur;
            for (int k = 0; k <= n; k++) {
                float x = wallStart[0] + (n == 0 ? 0 : Math.round(dx * k / (float) n)), z = wallStart[1] + (n == 0 ? 0 : Math.round(dz * k / (float) n));
                if (s != null && !validPlacement(s, placing, x, z)) continue;
                RtsAction a = new RtsAction(); a.kind = RtsAction.Kind.BUILD; a.defIndex = BuildingDef.ALL.indexOf(placing);
                a.x = x; a.z = z; a.ids = ids(); a.queue = k > 0 || hasShiftDown();
                Network.send(a);
            }
            wallStart = null;
            if (!hasShiftDown()) placing = null;
            return true;
        }
        if (button == 0) minimapDrag = false;
        if (button != 0 || !dragging) return false;
        dragging = false;
        Snapshot s = ClientMatch.cur; if (s == null) return true;
        Proj p = Proj.now(); boolean add = hasShiftDown();
        if (Math.abs(mx - dragX) < 4 && Math.abs(my - dragY) < 4) {   // click
            Snapshot.U u = unitAt(s, p, mx, my, true);
            long now = System.currentTimeMillis(); boolean dbl = now - lastClick < 300; lastClick = now;
            if (u != null) {
                if (!add) { selected.clear(); selectedBuilding = -1; }
                if (dbl) for (Snapshot.U o : s.units) { if (o.alive && o.team == s.myTeam && o.def == u.def && onScreen(p, o)) selected.add(o.id); }
                else if (add && selected.contains(u.id)) selected.remove(u.id); else selected.add(u.id);
            } else {
                float[] g = p.toGround(mx, my);
                Snapshot.B b = g == null ? null : buildingAt(s, g[0], g[2], true);
                if (!add) selected.clear();
                selectedBuilding = b == null ? -1 : b.id;
            }
        } else {   // box
            double x0 = Math.min(dragX, mx), x1 = Math.max(dragX, mx), y0 = Math.min(dragY, my), y1 = Math.max(dragY, my);
            if (!add) { selected.clear(); }
            selectedBuilding = -1;
            for (Snapshot.U u : s.units) {
                if (!u.alive || u.team != s.myTeam) continue;
                float[] q = screenOf(p, u);
                if (q != null && q[0] >= x0 && q[0] <= x1 && q[1] >= y0 && q[1] <= y1) selected.add(u.id);
            }
        }
        buildMenu = canBuild(s);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        Snapshot s = ClientMatch.cur;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_V) {
            if (placing != null && key == GLFW.GLFW_KEY_ESCAPE) { placing = null; return true; }
            RtsClient.toggle(); return true;
        }
        if (key == GLFW.GLFW_KEY_A && hasControlDown()) { selectArmy(s); return true; }
        if (key == GLFW.GLFW_KEY_A) { attackMode = true; return true; }
        if (key == GLFW.GLFW_KEY_I && s != null) { selectIdleBuilder(s); return true; }
        if (key == GLFW.GLFW_KEY_SPACE && s != null && s.alertAge >= 0) { RtsCamera.focusX = s.alertX; RtsCamera.focusZ = s.alertZ; return true; }
        if (key == GLFW.GLFW_KEY_HOME && s != null) { centerOnCommander(s); return true; }
        if (key == GLFW.GLFW_KEY_H || key == GLFW.GLFW_KEY_F1) { help = !help; return true; }
        if (key == GLFW.GLFW_KEY_PAUSE || key == GLFW.GLFW_KEY_F9) { simple(RtsAction.Kind.PAUSE, 1); return true; }
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) { simple(RtsAction.Kind.SPEED, 2); return true; }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) { simple(RtsAction.Kind.SPEED, 1); return true; }
        if (key >= GLFW.GLFW_KEY_F5 && key <= GLFW.GLFW_KEY_F8) {   // camera bookmarks: Ctrl to store
            int b = key - GLFW.GLFW_KEY_F5;
            if (hasControlDown()) bookmarks.put(b, new float[]{RtsCamera.focusX, RtsCamera.focusZ, RtsCamera.targetDist, RtsCamera.yaw});
            else { float[] v = bookmarks.get(b); if (v != null) { RtsCamera.focusX = v[0]; RtsCamera.focusZ = v[1]; RtsCamera.targetDist = v[2]; RtsCamera.yaw = v[3]; } }
            return true;
        }
        if (key == GLFW.GLFW_KEY_S) { order(Order.Type.STOP); return true; }
        if (key == GLFW.GLFW_KEY_P) { attackMode = false; patrolNext = true; return true; }
        if (key == GLFW.GLFW_KEY_B) { buildMenu = s != null && canBuild(s); return true; }
        if (key == GLFW.GLFW_KEY_U && selectedBuilding >= 0) { act(RtsAction.Kind.UPGRADE, -1, 1); return true; }
        if (key == GLFW.GLFW_KEY_R && selectedBuilding >= 0) { act(RtsAction.Kind.REPEAT, -1, 1); return true; }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int g = key - GLFW.GLFW_KEY_0;
            if (hasControlDown()) { groups.put(g, new LinkedHashSet<>(selected)); return true; }
            Set<Integer> grp = groups.get(g);
            if (grp != null) {
                selected.clear(); selected.addAll(grp); selectedBuilding = -1;
                long now = System.currentTimeMillis();
                if (lastGroupKey == g && now - lastGroupAt < 400 && s != null) centerOnSelection(s);
                lastGroupKey = g; lastGroupAt = now;
                if (s != null) buildMenu = canBuild(s);
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
    static boolean patrolNext;

    @Override public void onClose() { if (RtsCamera.active) RtsClient.toggle(); else super.onClose(); }

    // ---------------------------------------------------------------- actions
    static int[] ids() { return selected.stream().mapToInt(Integer::intValue).toArray(); }

    static void order(Order.Type t) {
        if (selected.isEmpty()) return;
        RtsAction a = new RtsAction(); a.kind = RtsAction.Kind.ORDER; a.orderType = t.ordinal(); a.ids = ids(); a.queue = hasShiftDown();
        Network.send(a);
    }

    static void act(RtsAction.Kind k, int defIndex, int count) {
        RtsAction a = new RtsAction(); a.kind = k; a.targetBuilding = selectedBuilding; a.defIndex = defIndex; a.count = count;
        Network.send(a);
    }

    static void simple(RtsAction.Kind k, int count) { RtsAction a = new RtsAction(); a.kind = k; a.count = count; Network.send(a); }

    /** Ctrl+A: every fighting unit (not builders or the commander). */
    void selectArmy(Snapshot s) {
        if (s == null) return;
        selected.clear(); selectedBuilding = -1;
        for (Snapshot.U u : s.units) {
            if (!u.alive || u.team != s.myTeam) continue;
            String r = UnitDef.ALL.get(u.def).role();
            if (!r.equals("builder") && !r.equals("commander")) selected.add(u.id);
        }
        buildMenu = false;
    }

    /** I: cycle through builders with nothing to do and look at them. */
    void selectIdleBuilder(Snapshot s) {
        List<Snapshot.U> idle = new ArrayList<>();
        for (Snapshot.U u : s.units) if (u.alive && u.team == s.myTeam && u.orders == 0 && UnitDef.ALL.get(u.def).role().equals("builder")) idle.add(u);
        if (idle.isEmpty()) { Minecraft.getInstance().gui.setOverlayMessage(Component.literal("No idle builders"), false); return; }
        Snapshot.U u = idle.get(Math.floorMod(idleCycle++, idle.size()));
        selected.clear(); selectedBuilding = -1; selected.add(u.id);
        RtsCamera.focusX = u.x; RtsCamera.focusZ = u.z; buildMenu = true;
    }

    void centerOnCommander(Snapshot s) {
        for (Snapshot.U u : s.units) if (u.alive && u.team == s.myTeam && UnitDef.ALL.get(u.def).role().equals("commander")) { RtsCamera.focusX = u.x; RtsCamera.focusZ = u.z; return; }
    }

    static int idleBuilders(Snapshot s) {
        int n = 0;
        for (Snapshot.U u : s.units) if (u.alive && u.team == s.myTeam && u.orders == 0 && UnitDef.ALL.get(u.def).role().equals("builder")) n++;
        return n;
    }

    void placeBuilding() {
        if (ghost == null || !ghostValid) return;
        RtsAction a = new RtsAction(); a.kind = RtsAction.Kind.BUILD; a.defIndex = BuildingDef.ALL.indexOf(placing);
        a.x = ghost[0]; a.z = ghost[1]; a.ids = ids(); a.queue = hasShiftDown();
        Network.send(a);
        if (!hasShiftDown()) placing = null;
    }

    void centerOnSelection(Snapshot s) {
        float sx = 0, sz = 0; int n = 0;
        for (Snapshot.U u : s.units) if (selected.contains(u.id)) { sx += u.x; sz += u.z; n++; }
        if (n > 0) { RtsCamera.focusX = sx / n; RtsCamera.focusZ = sz / n; }
    }

    static boolean canBuild(Snapshot s) {
        for (Snapshot.U u : s.units)
            if (selected.contains(u.id)) { String r = UnitDef.ALL.get(u.def).role(); if (r.equals("builder") || r.equals("commander")) return true; }
        return false;
    }

    // ---------------------------------------------------------------- picking helpers
    static float[] screenOf(Proj p, Snapshot.U u) {
        float[] xz = ClientMatch.pos(u);
        return p.toScreen(xz[0], RtsCamera.floor(xz[0], xz[1]) + 1, xz[1]);
    }
    static boolean onScreen(Proj p, Snapshot.U u) { float[] q = screenOf(p, u); return q != null && q[0] >= 0 && q[1] >= 0 && q[0] <= p.gw && q[1] <= p.gh; }

    static Snapshot.U unitAt(Snapshot s, Proj p, double mx, double my, boolean mine) {
        Snapshot.U best = null; double bd = 12 * 12;
        for (Snapshot.U u : s.units) {
            if (!u.alive || (mine ? u.team != s.myTeam : ClientMatch.ally(u.team, s.myTeam))) continue;   // mine, or a real enemy (not an ally)
            float[] q = screenOf(p, u); if (q == null) continue;
            double d = (q[0] - mx) * (q[0] - mx) + (q[1] - my) * (q[1] - my);
            if (d < bd) { bd = d; best = u; }
        }
        return best;
    }

    static Snapshot.B buildingAt(Snapshot s, float x, float z, boolean mine) {
        for (Snapshot.B b : s.buildings) {
            if (mine ? b.team != s.myTeam : ClientMatch.ally(b.team, s.myTeam)) continue;
            BuildingDef d = BuildingDef.ALL.get(b.def); String[] f = d.footprint().split("x");
            if (Math.abs(x - b.x) <= Integer.parseInt(f[0]) / 2f + .5f && Math.abs(z - b.z) <= Integer.parseInt(f[1]) / 2f + .5f) return b;
        }
        return null;
    }

    static boolean validPlacement(Snapshot s, BuildingDef d, float x, float z) {
        String[] f = d.footprint().split("x"); float hw = Integer.parseInt(f[0]) / 2f, hh = Integer.parseInt(f[1]) / 2f;
        for (Snapshot.B b : s.buildings) {
            BuildingDef od = BuildingDef.ALL.get(b.def); String[] g = od.footprint().split("x");
            float gap = od.footprint().equals("1x1") && d.footprint().equals("1x1") ? 0 : .5f;   // walls join up (same rule as the server)
            if (Math.abs(b.x - x) < Integer.parseInt(g[0]) / 2f + hw + gap && Math.abs(b.z - z) < Integer.parseInt(g[1]) / 2f + hh + gap) return false;
        }
        if (d.effect().equals("metal_per_s")) {
            for (int i = 0; i + 1 < ClientMatch.metalSpots.length; i += 2)
                if (Math.abs(ClientMatch.metalSpots[i] - x) < 3 && Math.abs(ClientMatch.metalSpots[i + 1] - z) < 3) return true;
            return false;
        }
        return true;
    }

    static final Map<String, String> DISPLAY = new HashMap<>();
    static {
        for (UnitDef d : UnitDef.ALL) DISPLAY.put(d.id(), d.name());
        for (dev.beyondtabs.engine.gen.RaceDef r : dev.beyondtabs.engine.gen.RaceDef.ALL) DISPLAY.put(r.id(), r.name());
    }

    /** Display name: the sheet's name for units and races, otherwise the id made readable. */
    static String name(String id) {
        String shown = DISPLAY.get(id);
        if (shown != null) return shown;
        String n = id.contains("_") ? id.substring(id.indexOf('_') + 1) : id;
        StringBuilder b = new StringBuilder();
        for (String w : n.split("_")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim().replace("T1", "").replace("T2", "II").replace("Davinci", "Da Vinci").trim();
    }

    // ---------------------------------------------------------------- HUD
    static final int BG = 0xC0101418, PANEL = 0xD0181E24, EDGE = 0xFF3A4652, TXT = 0xFFE8E2D4, DIM = 0xFF9AA4AE, METAL = 0xFFB8C4D0, ENERGY = 0xFFF2C94C;

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        buttons.clear();
        Snapshot s = ClientMatch.cur;
        Proj p = Proj.now();
        if (s == null) { g.drawCenteredString(font, "No match running — type /bt start in chat first (press V to leave)", width / 2, 20, TXT); return; }
        if (RtsCamera.strategic()) drawIcons(g, s, p);
        else drawSelectionMarkers(g, s, p);
        // placement ghost
        if (placing != null) {
            float[] gr = p.toGround(mx, my);
            if (gr != null) { ghost = new float[]{Math.round(gr[0]) + (sizeOdd(placing, 0) ? .5f : 0), Math.round(gr[2]) + (sizeOdd(placing, 1) ? .5f : 0)}; ghostValid = validPlacement(s, placing, ghost[0], ghost[1]); }
        } else ghost = null;
        // box
        if (dragging && (Math.abs(mx - dragX) > 3 || Math.abs(my - dragY) > 3)) {
            int x0 = (int) Math.min(dragX, mx), x1 = (int) Math.max(dragX, mx), y0 = (int) Math.min(dragY, my), y1 = (int) Math.max(dragY, my);
            g.fill(x0, y0, x1, y1, 0x2040FF60); outline(g, x0, y0, x1 - x0, y1 - y0, 0xFF60FF80);
        }
        drawFormationPreview(g, s, p);
        drawTopBar(g, s);
        drawBottomPanel(g, s, mx, my);
        drawAlert(g, s);
        if (!dragging && placing == null && my < height - 96) drawHover(g, s, p, mx, my);
        if (help) drawHelp(g);
        if (attackMode || patrolNext) g.drawCenteredString(font, (patrolNext ? "Patrol" : "Attack-move") + ": right-click a destination", width / 2, height - 118, 0xFFFF7060);
        if (s.winner >= 0) {
            boolean won = s.myTeam >= 0 && ClientMatch.ally(s.winner, s.myTeam);
            String msg = s.myTeam < 0 ? "BATTLE OVER" : won ? "VICTORY" : "DEFEAT";
            g.fill(width / 2 - 100, height / 2 - 30, width / 2 + 100, height / 2 + 30, BG); outline(g, width / 2 - 100, height / 2 - 30, 200, 60, EDGE);
            g.drawCenteredString(font, msg, width / 2, height / 2 - 20, s.myTeam < 0 ? TXT : won ? 0xFF7CFF7C : 0xFFFF6A6A);
            button(g, width / 2 - 60, height / 2, 120, 18, "Back to lobby", "End this battle and set up the next one",
                    () -> Network.send(new dev.beyondtabs.mod.LobbyAction(dev.beyondtabs.mod.LobbyAction.Op.RETURN)), null, false, true);
        }
        // tooltips
        for (Btn b : buttons) if (b.tip != null && mx >= b.x && my >= b.y && mx < b.x + b.w && my < b.y + b.h) tooltip(g, b.tip, mx, my);
    }

    static boolean sizeOdd(BuildingDef d, int axis) { return Integer.parseInt(d.footprint().split("x")[axis]) % 2 == 1; }

    void drawTopBar(GuiGraphics g, Snapshot s) {
        int w = 420, x = width / 2 - w / 2;
        g.fill(x, 0, x + w, 22, BG); g.hLine(x, x + w, 22, EDGE);
        bar(g, x + 8, 4, 150, "Metal", s.metal, s.metalMax, s.metalIncome, s.metalSpend, METAL);
        bar(g, x + 166, 4, 150, "Energy", s.energy, s.energyMax, s.energyIncome, s.energySpend, ENERGY);
        g.drawString(font, "Supply " + s.supplyUsed + "/" + s.supplyCap, x + 326, 4, s.supplyUsed >= s.supplyCap ? 0xFFFF6A6A : TXT);
        if (s.efficiency < .99f) g.drawString(font, String.format("Build %d%%", Math.round(s.efficiency * 100)), x + 326, 13, 0xFFFF9A4A);
        int idle = idleBuilders(s);
        if (idle > 0) {
            String t = idle + " idle builder" + (idle > 1 ? "s" : "") + " (I)";
            int bw = font.width(t) + 10;
            button(g, x + w + 6, 2, bw, 16, t, "Select the next builder with nothing to do", () -> selectIdleBuilder(s), null, false, true);
        }
        if (s.paused) g.drawCenteredString(font, "PAUSED  (Pause / F9 to resume)", width / 2, 28, 0xFFFFD27A);
        else if (s.speed != 1) g.drawCenteredString(font, "Speed " + (s.speed == .5f ? "0.5" : String.valueOf((int) s.speed)) + "x", width / 2, 28, 0xFFFFD27A);
    }

    void bar(GuiGraphics g, int x, int y, int w, String label, float v, float max, float inc, float spend, int color) {
        g.fill(x, y + 9, x + w, y + 14, 0xFF2A323A);
        g.fill(x, y + 9, x + (int) (w * Math.min(1, v / Math.max(1, max))), y + 14, color);
        g.drawString(font, String.format("%s %d/%d", label, Math.round(v), Math.round(max)), x, y - 1, TXT);
        String rate = String.format("+%.1f -%.1f", inc, spend);
        g.drawString(font, rate, x + w - font.width(rate), y - 1, inc >= spend ? 0xFF7CDC7C : 0xFFFF8A6A);
    }

    void drawBottomPanel(GuiGraphics g, Snapshot s, int mx, int my) {
        int h = 96, y = height - h;
        g.fill(0, y, width, height, PANEL); g.hLine(0, width, y, EDGE);
        // selection summary
        Map<Short, Integer> counts = new LinkedHashMap<>();
        for (Snapshot.U u : s.units) if (selected.contains(u.id)) counts.merge(u.def, 1, Integer::sum);
        int mm = h - 6; drawMinimap(g, s, 3, y + 3, mm);
        int x = mm + 10, ty = y + 6;
        if (!counts.isEmpty()) {
            g.drawString(font, selected.size() + " selected", x, ty, TXT); ty += 11;
            for (var e : counts.entrySet()) {
                if (ty > height - 12) break;
                UnitDef d = UnitDef.ALL.get(e.getKey()); final short def = e.getKey();
                String label = e.getValue() + "x " + name(d.id());
                button(g, x, ty, Math.max(120, font.width(label) + 10), 11, label, unitTip(d) + "\nLeft: select only these.  Right: drop these.",
                        () -> { Snapshot c = ClientMatch.cur; if (c != null) selected.removeIf(id -> c.units.stream().anyMatch(u -> u.id == id && u.def != def)); },
                        () -> { Snapshot c = ClientMatch.cur; if (c != null) selected.removeIf(id -> c.units.stream().anyMatch(u -> u.id == id && u.def == def)); },
                        false, true);
                ty += 12;
            }
        } else if (selectedBuilding >= 0) {
            Snapshot.B b = s.buildings.stream().filter(q -> q.id == selectedBuilding).findFirst().orElse(null);
            if (b != null) drawBuildingInfo(g, s, b, x, ty);
        } else {
            g.drawString(font, "Left-drag select, right-click order (right-drag = line formation). A attack-move, S stop, B build.", x, ty, DIM);
            g.drawString(font, "Ctrl+A army, I idle builder, Space last alert, Home commander, Ctrl+1-9 groups, H all hotkeys.", x, ty + 11, DIM);
        }
        int bx = mm + 200;
        if (buildMenu && !selected.isEmpty()) drawBuildMenu(g, s, bx, y + 6);
        if (selectedBuilding >= 0 && selected.isEmpty()) {
            Snapshot.B b = s.buildings.stream().filter(q -> q.id == selectedBuilding).findFirst().orElse(null);
            if (b != null && b.team == s.myTeam) drawBuildingActions(g, s, b, bx, y + 6);
        }
    }

    /** Minimap: [screenX, screenY, size, worldMinX, worldMinZ, worldSpan] of the last drawn minimap (north = up). */
    static float[] mini; static boolean minimapDrag;

    void drawMinimap(GuiGraphics g, Snapshot s, int x, int y, int size) {
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        float[] spots = ClientMatch.metalSpots;
        for (int i = 0; i + 1 < spots.length; i += 2) { minX = Math.min(minX, spots[i]); maxX = Math.max(maxX, spots[i]); minZ = Math.min(minZ, spots[i + 1]); maxZ = Math.max(maxZ, spots[i + 1]); }
        for (Snapshot.U u : s.units) if (u.alive) { minX = Math.min(minX, u.x); maxX = Math.max(maxX, u.x); minZ = Math.min(minZ, u.z); maxZ = Math.max(maxZ, u.z); }
        if (minX > maxX) return;
        float span = Math.max(maxX - minX, maxZ - minZ) + 40, cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        float x0 = cx - span / 2, z0 = cz - span / 2;
        mini = new float[]{x, y, size, x0, z0, span};
        g.fill(x, y, x + size, y + size, 0xFF22301E); outline(g, x, y, size, size, EDGE);
        java.util.function.BiFunction<Float, Float, int[]> at = (wx, wz) -> new int[]{x + (int) ((wx - x0) / span * size), y + (int) ((wz - z0) / span * size)};
        for (int i = 0; i + 1 < spots.length; i += 2) { int[] q = at.apply(spots[i], spots[i + 1]); g.fill(q[0] - 1, q[1] - 1, q[0] + 1, q[1] + 1, 0xFFB8C4D0); }
        for (Snapshot.B b : s.buildings) {
            int[] q = at.apply(b.x, b.z); int c = Look.teamArgb(b.team);
            g.fill(q[0] - 2, q[1] - 2, q[0] + 2, q[1] + 2, c);
        }
        for (Snapshot.U u : s.units) {
            if (!u.alive) continue;
            int[] q = at.apply(u.x, u.z); int c = selected.contains(u.id) ? 0xFFFFFFFF : Look.lighter(Look.teamArgb(u.team), .3f) | 0xFF000000;
            g.fill(q[0], q[1], q[0] + 1, q[1] + 1, c);
        }
        int[] f = at.apply(RtsCamera.focusX, RtsCamera.focusZ); int r = Math.max(3, (int) (RtsCamera.dist * .6f / span * size));
        outline(g, f[0] - r, f[1] - r, 2 * r, 2 * r, 0xFFFFFFFF);
        if (MatchRenderer.pingAt > 0 && System.currentTimeMillis() - MatchRenderer.pingAt < 1500) { int[] q = at.apply(MatchRenderer.pingX, MatchRenderer.pingZ); outline(g, q[0] - 3, q[1] - 3, 6, 6, 0xFF7CFF7C); }
    }

    void drawBuildingInfo(GuiGraphics g, Snapshot s, Snapshot.B b, int x, int y) {
        BuildingDef d = BuildingDef.ALL.get(b.def);
        g.drawString(font, name(d.id()) + "  (level " + b.level + "/" + d.levels() + ")", x, y, b.team == s.myTeam ? TXT : 0xFFFF8A8A);
        g.drawString(font, String.format("HP %d%%", Math.round(b.hp * 100)), x, y + 11, DIM);
        if (b.progress < 1) g.drawString(font, String.format("%s %d%%", b.upgrading ? "Upgrading" : "Building", Math.round(b.progress * 100)), x, y + 22, ENERGY);
        if (b.producing >= 0) g.drawString(font, String.format("Training %s %d%%", name(UnitDef.ALL.get(b.producing).id()), Math.round(b.produceFrac * 100)), x, y + 33, METAL);
        if (b.researching >= 0) g.drawString(font, String.format("Researching %s %d%%", name(TechDef.ALL.get(b.researching).id()), Math.round(b.researchFrac * 100)), x, y + 44, 0xFFB08CFF);
    }

    void drawBuildMenu(GuiGraphics g, Snapshot s, int x, int y) {
        g.drawString(font, "Build (click, then place; Shift to place several, right-click to cancel)", x, y, DIM);
        int cx = x, cy = y + 12;
        for (BuildingDef d : BuildingDef.ALL) {
            if (!d.race().equals(s.myRace)) continue;
            boolean ok = d.requiresTech().equals("none") || s.researched.contains(d.requiresTech());
            String label = name(d.id());
            int w = Math.max(70, font.width(label) + 10);
            if (cx + w > width - 8) { cx = x; cy += 22; }
            final BuildingDef fd = d;
            String tip = String.format("%s\n%d metal, %d energy, %s footprint, %d levels%s", name(d.id()), d.metal(), d.energy(), d.footprint(), d.levels(), ok ? "" : "\nNeeds " + name(d.requiresTech()));
            button(g, cx, cy, w, 18, label, tip, ok ? () -> { placing = fd; } : null, null, placing == d, ok);
            cx += w + 4;
        }
    }

    void drawBuildingActions(GuiGraphics g, Snapshot s, Snapshot.B b, int x, int y) {
        BuildingDef d = BuildingDef.ALL.get(b.def);
        int cx = x, cy = y;
        if (d.kind().equals("factory") && b.progress >= 1) {
            g.drawString(font, "Train (left +1, Shift +5, right-click remove)", cx, cy, DIM); cy += 12;
            for (UnitDef u : UnitDef.ALL) {
                if (!u.factory().equals(d.id())) continue;
                int idx = UnitDef.ALL.indexOf(u); int queued = 0; for (short q : b.queue) if (q == idx) queued++;
                boolean unlocked = u.tier() <= 1 || s.researched.stream().anyMatch(t -> t.endsWith("_tech_t" + u.tier()));
                String label = name(u.id()) + (queued > 0 ? " [" + queued + "]" : "");
                int w = Math.max(64, font.width(label) + 10);
                if (cx + w > width - 8) { cx = x; cy += 20; }
                button(g, cx, cy, w, 17, label, unitTip(u) + String.format("\n%d metal, %d energy, supply %d%s", u.metal(), u.energy(), u.supply(), unlocked ? "" : "\nNeeds tier " + u.tier() + " research"),
                        unlocked ? () -> act(RtsAction.Kind.ENQUEUE, idx, hasShiftDown() ? 5 : 1) : null,
                        () -> act(RtsAction.Kind.DEQUEUE, idx, 1), false, unlocked);
                cx += w + 3;
            }
            cy += 20; cx = x;
            button(g, cx, cy, 70, 17, b.repeat ? "Repeat: ON" : "Repeat: off", "R — loop the queue", () -> act(RtsAction.Kind.REPEAT, -1, 1), null, b.repeat, true);
            cx += 74;
        } else if (d.kind().equals("tech") && b.progress >= 1) {
            g.drawString(font, "Research", cx, cy, DIM); cy += 12;
            for (TechDef t : TechDef.ALL) {
                if (!t.researchedAt().equals(d.id())) continue;
                boolean done = s.researched.contains(t.id());
                boolean ok = !done && b.level >= t.minBuildingLevel() && (t.requires().equals("none") || s.researched.contains(t.requires()));
                int idx = TechDef.ALL.indexOf(t); String label = name(t.id()) + (done ? " ✓" : "");
                int w = Math.max(70, font.width(label) + 10);
                if (cx + w > width - 8) { cx = x; cy += 20; }
                button(g, cx, cy, w, 17, label, String.format("%d metal, %d energy, %ds — needs level %d", t.metal(), t.energy(), t.seconds(), t.minBuildingLevel()),
                        ok ? () -> act(RtsAction.Kind.RESEARCH, idx, 1) : null, null, done, ok);
                cx += w + 3;
            }
            cy += 20; cx = x;
        }
        if (b.level < d.levels() && b.progress >= 1)
            button(g, cx, cy, 90, 17, "Upgrade (U)", String.format("Level %d: %d metal, %d energy", b.level + 1, Math.round(d.metal() * .6), Math.round(d.energy() * .6)),
                    () -> act(RtsAction.Kind.UPGRADE, -1, 1), null, false, true);
    }

    static final String[] STYLE_NAMES = {"Aggressive", "Flanking", "Skirmishing", "Holding", "Kiting", "Focusing"};

    /** Name, role, health, weapon and ability of a unit type. */
    static String unitTip(UnitDef d) {
        var st = dev.beyondtabs.engine.UnitStats.fallback(d);
        var ab = dev.beyondtabs.engine.gen.AbilityDef.byId(d.ability());
        var w = st.weapon();
        StringBuilder b = new StringBuilder();
        b.append(name(d.id())).append("  (").append(d.role()).append(", tier ").append(d.tier()).append(")\n");
        b.append(String.format("HP %.0f  |  %s: %d dmg every %.1fs, range %.1f", st.hp(), name("x_" + w.id()), w.damage(), w.cooldown(), w.range()));
        if (!ab.id().equals("none")) b.append("\n").append(ab.name()).append(": ").append(ab.description());
        return b.toString();
    }

    void tooltip(GuiGraphics g, String tip, int mx, int my) {
        List<String> lines = new ArrayList<>();
        for (String l : tip.split("\n")) {   // wrap long lines
            while (font.width(l) > 300) { int cut = l.lastIndexOf(' ', Math.min(l.length() - 1, 70)); if (cut <= 0) break; lines.add(l.substring(0, cut)); l = l.substring(cut + 1); }
            lines.add(l);
        }
        int tw = 0; for (String l : lines) tw = Math.max(tw, font.width(l));
        int h = lines.size() * 10 + 6, x = Math.min(mx + 10, width - tw - 14), y = Math.max(2, my - h - 4);
        g.fill(x, y, x + tw + 10, y + h, 0xF0101418); outline(g, x, y, tw + 10, h, EDGE);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + 5, y + 4 + i * 10, i == 0 ? TXT : DIM);
    }

    /** Hovering a unit or building in the world shows what it is (and for your units, how it is fighting). */
    void drawHover(GuiGraphics g, Snapshot s, Proj p, int mx, int my) {
        Snapshot.U u = unitAt(s, p, mx, my, true); if (u == null) u = unitAt(s, p, mx, my, false);
        if (u != null) {
            UnitDef d = UnitDef.ALL.get(u.def);
            String tip = (u.team == s.myTeam ? "" : "Enemy ") + unitTip(d) + String.format("\nHealth %d%%", Math.round(u.hp * 100));
            if (u.style >= 0 && u.style < STYLE_NAMES.length) tip += "  |  fighting: " + STYLE_NAMES[u.style];
            if (u.knocked) tip += "  |  knocked down";
            tooltip(g, tip, mx, my);
            return;
        }
        float[] gr = p.toGround(mx, my);
        if (gr == null) return;
        Snapshot.B b = buildingAt(s, gr[0], gr[2], true); if (b == null) b = buildingAt(s, gr[0], gr[2], false);
        if (b != null) {
            BuildingDef d = BuildingDef.ALL.get(b.def);
            tooltip(g, (b.team == s.myTeam ? "" : "Enemy ") + name(d.id()) + String.format("  (level %d/%d)\nHealth %d%%%s", b.level, d.levels(), Math.round(b.hp * 100),
                    b.progress < 1 ? String.format("  |  %s %d%%", b.upgrading ? "upgrading" : "building", Math.round(b.progress * 100)) : ""), mx, my);
        }
    }

    /** While right-dragging: where the selected units will stand. */
    void drawFormationPreview(GuiGraphics g, Snapshot s, Proj p) {
        if (!rightDown || lineStart == null || lineEnd == null || selected.isEmpty()) return;
        float lx = lineEnd[0] - lineStart[0], lz = lineEnd[1] - lineStart[1], len = (float) Math.sqrt(lx * lx + lz * lz);
        if (len < 2) return;
        int n = selected.size();
        float cx = 0, cz = 0; int k = 0;
        for (Snapshot.U u : s.units) if (selected.contains(u.id)) { cx += u.x; cz += u.z; k++; }
        if (k > 0) { cx /= k; cz /= k; }
        float fx = lz / len, fz = -lx / len, mxw = (lineStart[0] + lineEnd[0]) / 2, mzw = (lineStart[1] + lineEnd[1]) / 2;
        if ((mxw - cx) * fx + (mzw - cz) * fz < 0) { fx = -fx; fz = -fz; }
        int cols = Math.max(1, Math.min(n, (int) (len / 1.4f) + 1));
        float colSp = cols > 1 ? Math.max(1.4f, len / (cols - 1)) : 1.4f, rx = fz, rz = -fx;
        int c = attackMode ? 0xFFFF7060 : 0xFF7CFF8C;
        for (int i = 0; i < n; i++) {
            int r = i / cols, j = i % cols, inRow = Math.min(cols, n - r * cols);
            float side = (j - (inRow - 1) / 2f) * colSp, back = -r * 1.4f;
            float wx = mxw + rx * side + fx * back, wz = mzw + rz * side + fz * back;
            float[] q = p.toScreen(wx, RtsCamera.ground(wx, wz) + .1f, wz);
            if (q != null) g.fill((int) q[0] - 2, (int) q[1] - 2, (int) q[0] + 2, (int) q[1] + 2, c);
        }
    }

    /** "Under attack" banner and a pulsing marker on the minimap; Space jumps there. */
    void drawAlert(GuiGraphics g, Snapshot s) {
        if (s.alertAge < 0 || s.alertAge > 5) return;
        String t = (s.alertBuilding ? "A building is under attack" : "Your units are under attack") + "  —  Space to look";
        int w = font.width(t) + 16, x = width / 2 - w / 2;
        int a = (int) (200 * Math.max(0, 1 - s.alertAge / 5f)) + 40;
        g.fill(x, 40, x + w, 54, (a << 24) | 0x5A1414); outline(g, x, 40, w, 14, 0xFFFF6A6A);
        g.drawString(font, t, x + 8, 43, 0xFFFFD0D0);
        if (mini != null) {
            int px = (int) (mini[0] + (s.alertX - mini[3]) / mini[5] * mini[2]), py = (int) (mini[1] + (s.alertZ - mini[4]) / mini[5] * mini[2]);
            int r = 3 + (int) ((System.currentTimeMillis() / 120) % 5);
            outline(g, px - r, py - r, 2 * r, 2 * r, 0xFFFF4040);
        }
    }

    void drawHelp(GuiGraphics g) {
        String[] lines = {
                "HOTKEYS  (H to close)",
                "Left-drag: box select   Double-click: all of that type on screen   Shift: add / queue",
                "Right-click: move / attack   Right-drag: line formation   A: attack-move   P: patrol   S: stop",
                "Ctrl+A: select army   I: next idle builder   Ctrl+1-9: set group   1-9: select group (twice: jump)",
                "B: build menu   (walls: drag to place a line)   U: upgrade building   R: factory repeat",
                "Space: jump to last alert   Home: commander   Ctrl+F5-F8: save camera   F5-F8: recall camera",
                "Arrows / screen edges / middle-drag: pan   Wheel: zoom (far = strategic icons)   Q / E: rotate",
                "Alt+wheel or PgUp/PgDn: tilt toward the horizon   Alt+middle-drag: turn and tilt   Shift+Alt+wheel: turn   End: reset tilt",
                "Pause or F9: pause (single player)   + / -: game speed   Alt: health bars for everyone",
                "V or Esc: leave the RTS view",
        };
        int w = 0; for (String l : lines) w = Math.max(w, font.width(l));
        int x = width / 2 - w / 2 - 10, y = height / 2 - lines.length * 6 - 10;
        g.fill(x, y, x + w + 20, y + lines.length * 12 + 14, 0xF0101418); outline(g, x, y, w + 20, lines.length * 12 + 14, EDGE);
        for (int i = 0; i < lines.length; i++) g.drawString(font, lines[i], x + 10, y + 8 + i * 12, i == 0 ? ENERGY : TXT);
    }

    void button(GuiGraphics g, int x, int y, int w, int h, String label, String tip, Runnable left, Runnable right, boolean on, boolean enabled) {
        g.fill(x, y, x + w, y + h, on ? 0xFF3E6A48 : enabled ? 0xFF2A3440 : 0xFF20262C);
        outline(g, x, y, w, h, on ? 0xFF7CDC7C : EDGE);
        g.drawString(font, label, x + 5, y + (h - 8) / 2, enabled ? TXT : 0xFF6A747E);
        buttons.add(new Btn(x, y, w, h, label, tip, left, right, on));
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int c) { g.hLine(x, x + w - 1, y, c); g.hLine(x, x + w - 1, y + h - 1, c); g.vLine(x, y, y + h - 1, c); g.vLine(x + w - 1, y, y + h - 1, c); }

    /** Close up: health bars over selected and damaged units (Alt: everyone). */
    void drawSelectionMarkers(GuiGraphics g, Snapshot s, Proj p) {
        boolean all = hasAltDown();
        for (Snapshot.U u : s.units) {
            if (!u.alive) continue;
            boolean sel = selected.contains(u.id);
            if (!sel && !all && u.hp > .995f) continue;
            float[] xz = ClientMatch.pos(u);
            float k = (float) UnitDef.ALL.get(Math.max(0, u.def)).scale();
            float[] q = p.toScreen(xz[0], RtsCamera.floor(xz[0], xz[1]) + 2.5f * k, xz[1]);
            if (q == null) continue;
            int x = (int) q[0], y = (int) q[1], hw = sel ? 9 : 7;
            int col = u.team == s.myTeam ? (u.hp > .5f ? 0xFF5FD86A : u.hp > .25f ? 0xFFE8C94A : 0xFFE8604A) : 0xFFE8604A;
            g.fill(x - hw - 1, y - 1, x + hw + 1, y + 3, 0xC0000000);
            g.fill(x - hw, y, x - hw + Math.round(2 * hw * u.hp), y + 2, col);
        }
    }

    /** Zoomed out (BAR strategic zoom): units become team-colored icons, buildings become outlined blocks. */
    void drawIcons(GuiGraphics g, Snapshot s, Proj p) {
        for (Snapshot.B b : s.buildings) {
            float[] q = p.toScreen(b.x, RtsCamera.ground(b.x, b.z), b.z);
            if (q == null) continue;
            int c = Look.teamArgb(b.team), r = 5;
            g.fill((int) q[0] - r, (int) q[1] - r, (int) q[0] + r, (int) q[1] + r, b.progress < 1 ? (c & 0x60FFFFFF) : c);
            outline(g, (int) q[0] - r, (int) q[1] - r, 2 * r, 2 * r, b.id == selectedBuilding ? 0xFFFFFFFF : 0xFF000000);
        }
        for (Snapshot.U u : s.units) {
            if (!u.alive) continue;
            float[] xz = ClientMatch.pos(u);
            float[] q = p.toScreen(xz[0], RtsCamera.floor(xz[0], xz[1]), xz[1]);
            if (q == null) continue;
            String role = UnitDef.ALL.get(u.def).role();
            int c = Look.lighter(Look.teamArgb(u.team), .2f) | 0xFF000000, r = role.equals("commander") ? 4 : role.equals("hero") || role.equals("siege") ? 3 : 2;
            int x = (int) q[0], y = (int) q[1];
            if (role.equals("ranged") || role.equals("siege")) { for (int k = 0; k <= r; k++) g.hLine(x - k, x + k, y - r + k, c); }   // triangle
            else g.fill(x - r, y - r, x + r, y + r, c);
            if (selected.contains(u.id)) outline(g, x - r - 1, y - r - 1, 2 * r + 3, 2 * r + 3, 0xFFFFFFFF);
        }
    }
}
