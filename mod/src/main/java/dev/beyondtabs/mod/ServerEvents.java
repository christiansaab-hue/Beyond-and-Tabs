package dev.beyondtabs.mod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.beyondtabs.engine.Order;
import dev.beyondtabs.engine.Unit;
import dev.beyondtabs.engine.World;
import dev.beyondtabs.engine.gen.UnitDef;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        w.tick();
        for (ServerPlayer p : level.players()) Network.sendSnapshot(p, m);
    }

    @SubscribeEvent public void onBreak(BlockEvent.BreakEvent e) { invalidate(e.getLevel()); }
    @SubscribeEvent public void onPlace(BlockEvent.EntityPlaceEvent e) { invalidate(e.getLevel()); }
    private void invalidate(Object level) { if (level instanceof ServerLevel l && MATCHES.containsKey(l)) MATCHES.get(l).terrain.invalidate(); }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("bt").requires(s -> s.hasPermission(2))
            .then(Commands.literal("battle").then(Commands.argument("perSide", IntegerArgumentType.integer(1, 400)).executes(this::battle)))
            .then(Commands.literal("clear").executes(c -> { MATCHES.remove(c.getSource().getLevel()); say(c, "Match cleared."); return 1; }))
            .then(Commands.literal("shove").executes(this::shove))
            .then(Commands.literal("stats").executes(this::stats)));
    }

    static Match match(ServerLevel l) { return MATCHES.computeIfAbsent(l, Match::new); }

    private int battle(CommandContext<CommandSourceStack> c) {
        int n = IntegerArgumentType.getInteger(c, "perSide");
        ServerLevel level = c.getSource().getLevel(); Match m = match(level); World w = m.world;
        if (w.teams.isEmpty()) { w.addTeam("ancient_world"); w.addTeam("kingdoms"); }
        Vec3 p = c.getSource().getPosition();
        float yaw = (float) Math.toRadians(-c.getSource().getRotation().y);   // player facing, sim yaw convention
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw), rx = fz, rz = -fx;
        float cx = (float) p.x + fx * 30, cz = (float) p.z + fz * 30;          // battle centre 30 blocks ahead
        UnitDef[] aw = {UnitDef.AW_CLUBBER, UnitDef.AW_CLUBBER, UnitDef.AW_PROTECTOR, UnitDef.AW_SPEAR_THROWER, UnitDef.AW_BERSERKER, UnitDef.AW_STONER};
        UnitDef[] kd = {UnitDef.KD_SQUIRE, UnitDef.KD_SQUIRE, UnitDef.KD_ARCHER, UnitDef.KD_FENCER, UnitDef.KD_HEALER, UnitDef.KD_KNIGHT};
        int cols = (int) Math.ceil(Math.sqrt(n));
        for (int i = 0; i < n; i++) {
            float side = (i % cols - cols / 2f) * 1.6f, depth = 12 + (i / cols) * 1.6f;
            float ax = cx + rx * side - fx * depth, az = cz + rz * side - fz * depth;
            float bx = cx + rx * side + fx * depth, bz = cz + rz * side + fz * depth;
            Unit a = w.spawn(0, aw[i % aw.length], ax, az, yaw);
            w.order(a, Order.attackMove(bx, bz), false);
            Unit b = w.spawn(1, kd[i % kd.length], bx, bz, yaw + (float) Math.PI);
            w.order(b, Order.attackMove(ax, az), false);
        }
        say(c, "Spawned " + n + " Ancient World vs " + n + " Kingdoms units 30 blocks ahead.");
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
        say(c, String.format("units %d (alive %d / %d) | tick %.2f ms, physics %.2f ms | ragdolls near %d mid %d asleep %d | near LOD %.0f blocks",
                w.units.size(), w.aliveCount(0), w.aliveCount(1), w.lastTickMs, w.lastPhysicsMs, w.activeNear, w.activeMid, w.sleepingBodies, w.lodNear()));
        return 1;
    }

    static void say(CommandContext<CommandSourceStack> c, String s) { c.getSource().sendSuccess(() -> Component.literal(s), false); }
}
