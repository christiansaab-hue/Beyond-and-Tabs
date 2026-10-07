package dev.beyondtabs.mod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.beyondtabs.engine.Building;
import dev.beyondtabs.engine.Order;
import dev.beyondtabs.engine.Team;
import dev.beyondtabs.engine.Unit;
import dev.beyondtabs.engine.World;
import dev.beyondtabs.engine.gen.BuildingDef;
import dev.beyondtabs.engine.gen.RaceDef;
import dev.beyondtabs.engine.gen.TechDef;
import dev.beyondtabs.engine.gen.UnitDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ServerEvents {
    public static final Map<ServerLevel, Match> MATCHES = new HashMap<>();

    @SubscribeEvent
    public void onTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
        Match m = MATCHES.get(level);
        if (m == null) return;
        World w = m.world;
        w.cameras.clear();
        for (ServerPlayer p : level.players()) { Vec3 v = p.getEyePosition(); w.cameras.add(new float[]{(float) v.x, (float) v.y, (float) v.z}); }
        if (level.getGameTime() % 40 == 0) m.terrain.invalidate();
        int before = w.winner;
        w.tick();
        if (level.getGameTime() % 5 == 0) m.structures.update();
        waves(m);
        if (before < 0 && w.winner >= 0)
            for (ServerPlayer p : level.players()) p.sendSystemMessage(Component.literal(
                    m.teamOf(p.getUUID()) == w.winner ? "Victory! The enemy commander has fallen." : "Defeat. Your commander has fallen."));
        boolean spots = level.getGameTime() % 20 == 0;
        for (ServerPlayer p : level.players()) Network.sendSnapshot(p, m, spots);
    }

    /** Stand-in opponent until the full AI lands: enemy factories repeat their queue and idle troops attack in waves. */
    static void waves(Match m) {
        World w = m.world;
        if (w.winner >= 0 || w.tick % (20 * 40) != 0) return;
        for (Team t : w.teams) {
            if (m.playerTeams.containsValue(t.id) || t.defeated) continue;
            Unit target = null;
            for (Unit u : w.units) if (u.alive && u.team != t.id && "commander".equals(u.def.role())) { target = u; break; }
            if (target == null) continue;
            int sent = 0;
            for (Unit u : w.units)
                if (u.alive && u.team == t.id && u.orders.isEmpty() && !"commander".equals(u.def.role()) && !"builder".equals(u.def.role())) {
                    w.order(u, Order.attackMove(target.x, target.z), false); sent++;
                }
            if (sent >= 4) for (ServerPlayer p : m.level.players()) p.sendSystemMessage(Component.literal("An enemy wave of " + sent + " units is marching on your base!"));
        }
    }

    @SubscribeEvent public void onBreak(BlockEvent.BreakEvent e) { invalidate(e.getLevel()); }
    @SubscribeEvent public void onPlace(BlockEvent.EntityPlaceEvent e) { invalidate(e.getLevel()); }
    private void invalidate(Object level) { if (level instanceof ServerLevel l && MATCHES.containsKey(l)) MATCHES.get(l).terrain.invalidate(); }

    // ---------------------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("bt").requires(s -> s.hasPermission(2))
            .then(Commands.literal("start").executes(c -> start(c, "ancient_world", "kingdoms"))
                .then(Commands.argument("race", StringArgumentType.word()).executes(c -> start(c, StringArgumentType.getString(c, "race"), "kingdoms"))
                    .then(Commands.argument("enemy", StringArgumentType.word()).executes(c -> start(c, StringArgumentType.getString(c, "race"), StringArgumentType.getString(c, "enemy"))))))
            .then(Commands.literal("battle").then(Commands.argument("perSide", IntegerArgumentType.integer(1, 400)).executes(this::battle)))
            .then(Commands.literal("clear").executes(c -> { MATCHES.remove(c.getSource().getLevel()); say(c, "Match cleared."); return 1; }))
            .then(Commands.literal("shove").executes(this::shove))
            .then(Commands.literal("showcase").executes(this::showcase))
            .then(Commands.literal("stats").executes(this::stats)));
    }

    static Match match(ServerLevel l) { return MATCHES.computeIfAbsent(l, Match::new); }

    private int start(CommandContext<CommandSourceStack> c, String race, String enemy) {
        for (String r : List.of(race, enemy))
            if (RaceDef.ALL.stream().noneMatch(d -> d.id().equals(r) && d.firstPlayable())) {
                say(c, "Playable races right now: ancient_world, kingdoms"); return 0;
            }
        ServerLevel level = c.getSource().getLevel();
        ServerPlayer player = c.getSource().getPlayer();
        Match m = new Match(level); MATCHES.put(level, m);
        World w = m.world;
        w.addTeam(race); w.addTeam(enemy);
        if (player != null) m.playerTeams.put(player.getUUID(), 0);
        Vec3 p = c.getSource().getPosition();
        float yaw = (float) Math.toRadians(-c.getSource().getRotation().y), fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float ax = (float) p.x, az = (float) p.z, bx = ax + fx * 90, bz = az + fz * 90;
        for (float[] base : new float[][]{{ax, az}, {bx, bz}})
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + .3; float r = 13 + (i % 2) * 6;
                w.metalSpots.add(new float[]{base[0] + (float) Math.cos(a) * r, base[1] + (float) Math.sin(a) * r});
            }
        for (int i = 0; i < 4; i++) w.metalSpots.add(new float[]{(ax + bx) / 2 + fz * (i - 1.5f) * 14, (az + bz) / 2 - fx * (i - 1.5f) * 14});
        UnitDef myCmd = commander(race), theirCmd = commander(enemy);
        w.spawn(0, myCmd, ax + fx * 4, az + fz * 4, yaw);
        w.spawn(1, theirCmd, bx - fx * 4, bz - fz * 4, yaw + (float) Math.PI);
        // the stand-in opponent starts with a small working base so there is something to fight
        String pre = enemy.equals("kingdoms") ? "kd" : "aw";
        float rx = fz, rz = -fx;   // "right" of the player's facing
        Building barracks = prebuild(w, 1, pre + "_barracks", bx - fx * 16, bz - fz * 16);
        if (barracks != null) {
            barracks.repeat = true; barracks.rallyX = bx - fx * 24; barracks.rallyZ = bz - fz * 24;
            for (UnitDef d : UnitDef.ALL) if (d.factory().equals(barracks.def.id()) && !"builder".equals(d.role()) && !"support".equals(d.role())) barracks.queue.add(d);
        }
        prebuild(w, 1, pre + "_watchtower", bx - fx * 11 + rx * 8, bz - fz * 11 + rz * 8);
        prebuild(w, 1, pre + "_watchtower", bx - fx * 11 - rx * 8, bz - fz * 11 - rz * 8);
        int placed = 0;
        for (float[] sp : w.metalSpots) if (placed < 3 && Math.abs(sp[0] - bx) + Math.abs(sp[1] - bz) < 30 && prebuild(w, 1, pre + "_metal_extractor", sp[0], sp[1]) != null) placed++;
        prebuild(w, 1, pre + "_energy_gen", bx + rx * 9, bz + rz * 9);
        prebuild(w, 1, pre + "_energy_gen", bx - rx * 9, bz - rz * 9);
        prebuild(w, 1, pre + "_tech_center", bx + fx * 9, bz + fz * 9);
        say(c, "Match started: you are " + race + " (commander next to you), the enemy " + enemy + " base is 90 blocks ahead. Press V for the RTS view.");
        return 1;
    }

    /** Places a finished building at (x,z), or at the nearest free spot around it (footprints never overlap). */
    static Building prebuild(World w, int team, String defId, float x, float z) {
        BuildingDef def = BuildingDef.byId(defId);
        for (int r = 0; r <= 12; r += 2)
            for (int k = 0; k < (r == 0 ? 1 : 8); k++) {
                double a = k * Math.PI / 4;
                Building b = w.startConstruction(team, def, x + (float) Math.cos(a) * r, z + (float) Math.sin(a) * r);
                if (b != null) { b.progress = 1; b.hp = b.maxHp(); return b; }
            }
        return null;
    }

    static UnitDef commander(String race) {
        for (UnitDef d : UnitDef.ALL) if (d.race().equals(race) && "commander".equals(d.role())) return d;
        throw new IllegalArgumentException("no commander for " + race);
    }

    private int battle(CommandContext<CommandSourceStack> c) {
        int n = IntegerArgumentType.getInteger(c, "perSide");
        ServerLevel level = c.getSource().getLevel(); Match m = match(level); World w = m.world;
        if (w.teams.isEmpty()) { w.addTeam("ancient_world"); w.addTeam("kingdoms"); }
        if (c.getSource().getPlayer() != null) m.playerTeams.putIfAbsent(c.getSource().getPlayer().getUUID(), 0);
        Vec3 p = c.getSource().getPosition();
        float yaw = (float) Math.toRadians(-c.getSource().getRotation().y);
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw), rx = fz, rz = -fx;
        float cx = (float) p.x + fx * 30, cz = (float) p.z + fz * 30;
        UnitDef[] aw = {UnitDef.AW_CLUBBER, UnitDef.AW_CLUBBER, UnitDef.AW_PROTECTOR, UnitDef.AW_SPEAR_THROWER, UnitDef.AW_BERSERKER, UnitDef.AW_STONER};
        UnitDef[] kd = {UnitDef.KD_SQUIRE, UnitDef.KD_SQUIRE, UnitDef.KD_ARCHER, UnitDef.KD_FENCER, UnitDef.KD_HEALER, UnitDef.KD_KNIGHT};
        int cols = (int) Math.ceil(Math.sqrt(n));
        for (int i = 0; i < n; i++) {
            float side = (i % cols - cols / 2f) * 1.6f, depth = 12 + (i / cols) * 1.6f;
            float ax = cx + rx * side - fx * depth, az = cz + rz * side - fz * depth;
            float bx = cx + rx * side + fx * depth, bz = cz + rz * side + fz * depth;
            w.order(w.spawn(0, aw[i % aw.length], ax, az, yaw), Order.attackMove(bx, bz), false);
            w.order(w.spawn(1, kd[i % kd.length], bx, bz, yaw + (float) Math.PI), Order.attackMove(ax, az), false);
        }
        say(c, "Spawned " + n + " Ancient World vs " + n + " Kingdoms units 30 blocks ahead.");
        return 1;
    }

    /** Builds every building of the playable races at every level in rows ahead of the player (architecture preview). */
    private int showcase(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        Match m = new Match(level); MATCHES.put(level, m); World w = m.world;
        w.addTeam("ancient_world"); w.addTeam("kingdoms");
        if (c.getSource().getPlayer() != null) m.playerTeams.put(c.getSource().getPlayer().getUUID(), 0);
        Vec3 p = c.getSource().getPosition();
        float yaw = (float) Math.toRadians(-c.getSource().getRotation().y), fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw), rx = fz, rz = -fx;
        float ahead = 14;
        for (String race : List.of("ancient_world", "kingdoms")) {
            float along = -40, rowDepth = 0;
            for (BuildingDef d : BuildingDef.ALL) {
                if (!d.race().equals(race)) continue;
                String[] f = d.footprint().split("x"); int fw = Integer.parseInt(f[0]), fd = Integer.parseInt(f[1]);
                for (int lv = 1; lv <= d.levels(); lv++) {
                    if (along + fw > 60) { along = -40; ahead += rowDepth + 4; rowDepth = 0; }
                    float cx = (float) p.x + fx * (ahead + fd / 2f) + rx * (along + fw / 2f), cz = (float) p.z + fz * (ahead + fd / 2f) + rz * (along + fw / 2f);
                    Building b = w.place(race.equals("ancient_world") ? 0 : 1, d, Math.round(cx) + (fw % 2 == 1 ? .5f : 0), Math.round(cz) + (fd % 2 == 1 ? .5f : 0), true);
                    b.level = lv; b.hp = b.maxHp();
                    along += fw + 3; rowDepth = Math.max(rowDepth, fd);
                }
            }
            ahead += rowDepth + 8;
        }
        say(c, "Showcase: every Ancient World and Kingdoms building at every level, in rows ahead of you.");
        return 1;
    }

    private int shove(CommandContext<CommandSourceStack> c) {
        Match m = MATCHES.get(c.getSource().getLevel());
        if (m == null) return 0;
        Vec3 p = c.getSource().getPosition();
        for (Unit u : m.world.units) if (u.alive) m.world.damage(u, 0, 30f, (float) p.x, (float) p.z, 1);
        say(c, "Shoved every unit away from you."); return 1;
    }

    private int stats(CommandContext<CommandSourceStack> c) {
        Match m = MATCHES.get(c.getSource().getLevel());
        if (m == null) { say(c, "No match running."); return 0; }
        World w = m.world;
        say(c, String.format("units %d, buildings %d | tick %.2f ms, physics %.2f ms | ragdolls near %d mid %d asleep %d | near LOD %.0f blocks",
                w.units.size(), w.buildings.size(), w.lastTickMs, w.lastPhysicsMs, w.activeNear, w.activeMid, w.sleepingBodies, w.lodNear()));
        return 1;
    }

    static void say(CommandContext<CommandSourceStack> c, String s) { c.getSource().sendSuccess(() -> Component.literal(s), false); }

    // ---------------------------------------------------------------------------------------------------------------
    /** Applies a player's RTS command after checking they own everything it touches. */
    static void handle(ServerPlayer player, RtsAction a) {
        Match m = MATCHES.get(player.serverLevel());
        if (a.kind == RtsAction.Kind.MODE) { setMode(player, m, a.on); return; }
        if (m == null) return;
        int team = m.teamOf(player.getUUID());
        if (team < 0) return;
        World w = m.world;
        switch (a.kind) {
            case ORDER -> {
                Order.Type type = Order.Type.values()[Math.max(0, Math.min(Order.Type.values().length - 1, a.orderType))];
                List<Unit> mine = owned(w, team, a.ids);
                int cols = (int) Math.ceil(Math.sqrt(mine.size())), i = 0;
                for (Unit u : mine) {
                    Order o;
                    switch (type) {
                        case ATTACK -> {
                            Unit t = unit(w, a.targetUnit); Building b = building(w, a.targetBuilding);
                            if (t != null && t.alive && t.team != team) o = Order.attack(t);
                            else if (b != null && b.alive && b.team != team) o = Order.attackBuilding(b);
                            else continue;
                        }
                        case GUARD -> { Unit t = unit(w, a.targetUnit); if (t == null || !t.alive) continue; o = Order.guard(t); }
                        case STOP -> o = new Order(Order.Type.STOP, u.x, u.z, 0, null, null);
                        case AREA_ATTACK -> o = Order.areaAttack(a.x, a.z, Math.max(3, a.radius));
                        default -> {   // spread groups into a square formation around the clicked point
                            float ox = (i % cols - (cols - 1) / 2f) * 1.6f, oz = (i / cols - (cols - 1) / 2f) * 1.6f;
                            o = new Order(type, a.x + ox, a.z + oz, 0, null, null);
                        }
                    }
                    w.order(u, o, a.queue); i++;
                }
            }
            case BUILD -> {
                if (a.defIndex < 0 || a.defIndex >= BuildingDef.ALL.size()) return;
                Building b = w.startConstruction(team, BuildingDef.ALL.get(a.defIndex), a.x, a.z);
                if (b == null) { player.displayClientMessage(Component.literal("Can't build there."), true); return; }
                for (Unit u : owned(w, team, a.ids))
                    if ("builder".equals(u.def.role()) || "commander".equals(u.def.role())) w.order(u, Order.build(b), a.queue);
            }
            case ENQUEUE, DEQUEUE, REPEAT, RALLY, UPGRADE, RESEARCH -> {
                Building b = building(w, a.targetBuilding);
                if (b == null || b.team != team || !b.alive) return;
                switch (a.kind) {
                    case ENQUEUE -> { if (a.defIndex >= 0 && a.defIndex < UnitDef.ALL.size()) for (int k = 0; k < a.count; k++) w.enqueue(b, UnitDef.ALL.get(a.defIndex)); }
                    case DEQUEUE -> { if (a.defIndex >= 0 && a.defIndex < UnitDef.ALL.size()) { var arr = new ArrayList<>(b.queue); int idx = arr.lastIndexOf(UnitDef.ALL.get(a.defIndex)); if (idx >= 0) { arr.remove(idx); b.queue.clear(); b.queue.addAll(arr); } } }
                    case REPEAT -> b.repeat = !b.repeat;
                    case RALLY -> { b.rallyX = a.x; b.rallyZ = a.z; }
                    case UPGRADE -> w.upgrade(b);
                    case RESEARCH -> { if (a.defIndex >= 0 && a.defIndex < TechDef.ALL.size()) w.research(b, TechDef.ALL.get(a.defIndex)); }
                    default -> { }
                }
            }
            default -> { }
        }
    }

    static void setMode(ServerPlayer p, Match m, boolean on) {
        Map<java.util.UUID, GameType> prev = m != null ? m.previousMode : FALLBACK_MODES;
        if (on) { prev.putIfAbsent(p.getUUID(), p.gameMode.getGameModeForPlayer()); p.setGameMode(GameType.SPECTATOR); }
        else { GameType g = prev.remove(p.getUUID()); if (g != null) p.setGameMode(g); }
    }
    static final Map<java.util.UUID, GameType> FALLBACK_MODES = new HashMap<>();

    static List<Unit> owned(World w, int team, int[] ids) {
        List<Unit> out = new ArrayList<>();
        java.util.Set<Integer> want = new java.util.HashSet<>(); for (int id : ids) want.add(id);
        for (Unit u : w.units) if (u.alive && u.team == team && want.contains(u.id)) out.add(u);
        return out;
    }
    static Unit unit(World w, int id) { for (Unit u : w.units) if (u.id == id) return u; return null; }
    static Building building(World w, int id) { for (Building b : w.buildings) if (b.id == id) return b; return null; }
}
