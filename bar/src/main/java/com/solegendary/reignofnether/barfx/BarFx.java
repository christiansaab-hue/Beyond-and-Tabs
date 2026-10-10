package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.registrars.PacketHandler;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server side of the BAR-style battle effects. Gameplay code calls the static helpers below whenever something
 * worth showing happens (a shot, an impact, an explosion, a death, a building part breaking); events are queued per
 * level and sent once per tick to each nearby player as one {@link BarFxClientboundPacket}. Everything visual is
 * done on the client in {@code BarFxClient}. Safe to call from anywhere: client-side calls are ignored.
 */
public final class BarFx {
    private BarFx() { }

    // ---- event types
    public static final byte SHOT = 0, IMPACT = 1, EXPLOSION = 2, DEATH = 3, BUILDING_PART = 4, COLLAPSE = 5, NANO = 6,
            SCORCH = 7;

    // ---- nanolathe beam tints (NANO kind): one per faction so a glance tells whose builders are at work
    // (N_NEUTRAL: factions without their own tint - they used to borrow Sunforged gold; see FactionTraits)
    public static final byte N_SUNFORGED = 0, N_GRAVEBOUND = 1, N_HORDE = 2, N_NEUTRAL = 3, N_VERDANT = 4;

    /** Minimum ticks between two nano beams from the same worker (a beam lives a little longer, so they overlap). */
    public static final int NANO_INTERVAL = 8;

    // ---- weapon / projectile kinds (shots and impacts)
    public static final byte K_ARROW = 0, K_FIREBALL = 1, K_BIG_FIREBALL = 2, K_MAGIC = 3, K_THROWN = 4, K_TNT = 5,
            K_ROCKET = 6, K_SONIC = 7, K_MELEE = 8, K_POTION = 9, K_HEAVY_MELEE = 10, K_OTHER = 11;

    // ---- impact "where" flags
    public static final byte AT_GROUND = 0, AT_UNIT = 1, AT_BUILDING = 2;

    // ---- death kinds
    public static final byte D_FLESH = 0, D_CONSTRUCT = 1, D_BONE = 2, D_SLIME = 3;

    // ---- death flags: low 2 bits = faction debris tint, next 2 bits = cost tier (0 = not an RTS unit)
    public static final byte F_NONE = 0, F_SUNFORGED = 1, F_GRAVEBOUND = 2, F_HORDE = 3;

    /** Metal cost from which a dying unit counts as T2 / T3 for its death blast (T1 below). */
    public static final int T2_METAL = 120, T3_METAL = 500;

    /** Cost tier 1-3 from a unit's metal cost (death blast size). */
    public static int tierOf(int metal) {
        return metal >= T3_METAL ? 3 : metal >= T2_METAL ? 2 : 1;
    }

    /** One effect event. Meaning of a/b/c depends on the type (see the emit helpers). */
    public static final class Event {
        public final byte type, kind, flags;
        public final float x, y, z, a, b, c;

        public Event(byte type, byte kind, byte flags, float x, float y, float z, float a, float b, float c) {
            this.type = type; this.kind = kind; this.flags = flags;
            this.x = x; this.y = y; this.z = z; this.a = a; this.b = b; this.c = c;
        }
    }

    static final int MAX_PER_TICK = 160;
    static final double SEND_RANGE = 200;
    private static final Map<ResourceKey<Level>, List<Event>> QUEUE = new HashMap<>();

    /** While > 0, explosions are not reported (a building collapse already reports one big effect). */
    static int muteExplosions = 0;

    public static void muteExplosions(boolean mute) {
        muteExplosions = Math.max(0, muteExplosions + (mute ? 1 : -1));
    }

    static void emit(Level level, Event e) {
        if (!(level instanceof ServerLevel sl) || e == null)
            return;
        if (!Float.isFinite(e.x) || !Float.isFinite(e.y) || !Float.isFinite(e.z))
            return;
        List<Event> q = QUEUE.computeIfAbsent(sl.dimension(), k -> new ArrayList<>());
        if (q.size() >= MAX_PER_TICK) {
            // when flooded, keep the big stuff and drop small shots/hits/nano beams
            if (isMinor(e.type))
                return;
            for (int i = 0; i < q.size(); i++) {
                if (isMinor(q.get(i).type)) {
                    q.set(i, e);
                    return;
                }
            }
            return;
        }
        q.add(e);
    }

    /** Cosmetic chatter that may be dropped first when a tick is flooded. */
    private static boolean isMinor(byte type) {
        return type == SHOT || type == IMPACT || type == NANO;
    }

    // ------------------------------------------------------------------ emit helpers

    /** A weapon fires from 'from' towards 'to'. */
    public static void shot(Level level, Vec3 from, Vec3 to, byte kind) {
        if (level == null || level.isClientSide() || from == null || to == null) return;
        emit(level, new Event(SHOT, kind, (byte) 0, (float) from.x, (float) from.y, (float) from.z,
                (float) to.x, (float) to.y, (float) to.z));
    }

    /** Something lands at pos travelling along dir (may be null). size ~1 for a normal hit. */
    public static void impact(Level level, Vec3 pos, Vec3 dir, byte kind, byte where, float size) {
        if (level == null || level.isClientSide() || pos == null) return;
        float dx = 0, dz = 0;
        if (dir != null) {
            double l = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (l > 1e-4) { dx = (float) (dir.x / l); dz = (float) (dir.z / l); }
        }
        emit(level, new Event(IMPACT, kind, where, (float) pos.x, (float) pos.y, (float) pos.z, dx, size, dz));
    }

    /** An explosion of the given radius. kind: K_FIREBALL / K_BIG_FIREBALL / K_TNT / K_MAGIC / K_OTHER. */
    public static void explosion(Level level, Vec3 pos, float radius, byte kind) {
        if (level == null || level.isClientSide() || pos == null || muteExplosions > 0) return;
        emit(level, new Event(EXPLOSION, kind, (byte) 0, (float) pos.x, (float) pos.y, (float) pos.z,
                Math.max(.5f, Math.min(radius, 16f)), 0, 0));
    }

    /** A unit / mob dies. width & height are its bounding box. */
    public static void death(Level level, Vec3 pos, byte kind, float width, float height) {
        death(level, pos, kind, width, height, F_NONE, 0);
    }

    /**
     * An RTS unit dies: faction (F_*) picks the debris tint, tier (1-3, from its metal cost) the size of the blast.
     * tier 0 = a plain mob (the old untinted puff).
     */
    public static void death(Level level, Vec3 pos, byte kind, float width, float height, byte faction, int tier) {
        if (level == null || level.isClientSide() || pos == null) return;
        byte flags = (byte) ((faction & 3) | ((Math.max(0, Math.min(3, tier)) & 3) << 2));
        emit(level, new Event(DEATH, kind, flags, (float) pos.x, (float) pos.y, (float) pos.z, width, height, 0));
    }

    /**
     * A heavy weapon lands (artillery shell, T3 ability): the client leaves a ring of dark smoke and a scorch mark
     * that linger ~2 s. radius = the blast's reach in blocks.
     */
    public static void heavyImpact(Level level, Vec3 pos, float radius) {
        if (level == null || level.isClientSide() || pos == null) return;
        emit(level, new Event(SCORCH, (byte) 0, (byte) 0, (float) pos.x, (float) pos.y, (float) pos.z,
                Math.max(.75f, Math.min(radius, 8f)), 0, 0));
    }

    /** One block of a building is knocked out (state = the block before it was removed, may be null). */
    public static void buildingPart(Level level, BlockPos bp, BlockState state) {
        if (level == null || level.isClientSide() || bp == null) return;
        int col = 0x8A7A68;
        try {
            if (state != null && !state.isAir()) {
                int c = state.getMapColor(level, bp).col;
                if (c != 0) col = c;
            }
        } catch (Exception ignored) { }
        emit(level, new Event(BUILDING_PART, (byte) 0, (byte) 0, bp.getX() + .5f, bp.getY() + .5f, bp.getZ() + .5f,
                (float) (col & 0xFFFFFF), 0, 0));
    }

    /** A whole building is destroyed: centre of its base, horizontal half size and height. */
    public static void collapse(Level level, Vec3 centre, float halfSize, float height) {
        if (level == null || level.isClientSide() || centre == null) return;
        emit(level, new Event(COLLAPSE, (byte) 0, (byte) 0, (float) centre.x, (float) centre.y, (float) centre.z,
                Math.max(1, halfSize), Math.max(1, height), 0));
    }

    /**
     * Last game time each worker (by entity id) sent a nano beam. This is the per-worker rate limit: with 100+ workers
     * building, a beam every {@link #NANO_INTERVAL} ticks each is ~12 tiny events a tick, well under MAX_PER_TICK.
     * Entity ids are unique for the whole server run, so one map serves every level.
     */
    private static final Int2LongOpenHashMap LAST_NANO = new Int2LongOpenHashMap();

    /**
     * A worker's nanolathe beam from its hands to (tx,ty,tz): it is building, repairing or reclaiming there.
     * Rate limited per worker to one beam every {@code interval} ticks; the client keeps it alive for
     * {@code lifeTicks}. Returns true if a beam was queued.
     */
    public static boolean nano(LivingEntity worker, double tx, double ty, double tz, int interval, int lifeTicks) {
        if (worker == null || !(worker.level() instanceof ServerLevel sl))
            return false;
        long now = sl.getGameTime();
        int id = worker.getId();
        long last = LAST_NANO.getOrDefault(id, Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now >= last && now - last < interval)   // (now < last: time was set back)
            return false;
        if (LAST_NANO.size() > 8192)   // dead workers are never removed one by one; a rare reset is cheaper
            LAST_NANO.clear();
        LAST_NANO.put(id, now);
        // hands: a bit in front of the body at chest height, so the beam leaves the worker rather than its feet
        float yaw = worker.yBodyRot * ((float) Math.PI / 180f);
        double reach = worker.getBbWidth() * .5 + .15;
        double hx = worker.getX() - Math.sin(yaw) * reach;
        double hy = worker.getY() + worker.getBbHeight() * .55;
        double hz = worker.getZ() + Math.cos(yaw) * reach;
        byte tint = N_SUNFORGED;   // non-unit workers keep the old golden beam
        try {
            if (worker instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                tint = com.solegendary.reignofnether.faction.FactionTraits.of(
                    com.solegendary.reignofnether.faction.Factions.getFaction(u)).nanoTint;
        } catch (Exception ignored) { }
        emit(sl, new Event(NANO, tint, (byte) Math.max(1, Math.min(lifeTicks, 100)),
                (float) hx, (float) hy, (float) hz, (float) tx, (float) ty, (float) tz));
        return true;
    }

    /** Game time of the worker's last nano beam, or -1 if it never sent one (used by the game test). */
    public static long lastNanoTime(Entity worker) {
        return worker == null ? -1 : LAST_NANO.getOrDefault(worker.getId(), -1L);
    }

    /** Classifies a projectile entity (works on both sides; never throws). */
    public static byte kindOf(net.minecraft.world.entity.Entity e) {
        if (e == null) return K_OTHER;
        try {
            if (e instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity) return K_ROCKET;
            if (e instanceof com.solegendary.reignofnether.entities.ThrowableTntProjectile) return K_TNT;
            if (e instanceof com.solegendary.reignofnether.entities.MoltenBombProjectile) return K_BIG_FIREBALL;
            if (e instanceof net.minecraft.world.entity.projectile.LargeFireball) return K_BIG_FIREBALL;
            if (e instanceof net.minecraft.world.entity.projectile.SmallFireball) return K_FIREBALL;
            if (e instanceof net.minecraft.world.entity.projectile.DragonFireball) return K_BIG_FIREBALL;
            if (e instanceof com.solegendary.reignofnether.entities.AbstractMagicProjectile) return K_MAGIC;
            if (e instanceof net.minecraft.world.entity.projectile.WitherSkull) return K_MAGIC;
            if (e instanceof net.minecraft.world.entity.projectile.ShulkerBullet) return K_MAGIC;
            if (e instanceof net.minecraft.world.entity.projectile.Fireball) return K_FIREBALL;
            if (e instanceof net.minecraft.world.entity.projectile.AbstractArrow) return K_ARROW;
            if (e instanceof net.minecraft.world.entity.projectile.ThrownPotion) return K_POTION;
            if (e instanceof net.minecraft.world.entity.projectile.ThrowableProjectile) return K_THROWN;
            if (e instanceof net.minecraft.world.entity.projectile.LlamaSpit) return K_THROWN;
        } catch (Throwable ignored) { }
        return K_OTHER;
    }

    // ------------------------------------------------------------------ sending

    /** Called once per server tick: sends each player the queued events near them. */
    static void flush(ServerLevel level) {
        List<Event> q = QUEUE.get(level.dimension());
        if (q == null || q.isEmpty())
            return;
        List<Event> events = new ArrayList<>(q);
        q.clear();
        for (ServerPlayer player : level.players()) {
            List<Event> mine = new ArrayList<>();
            double px = player.getX(), pz = player.getZ();
            for (Event e : events) {
                double dx = e.x - px, dz = e.z - pz;
                if (dx * dx + dz * dz <= SEND_RANGE * SEND_RANGE)
                    mine.add(e);
            }
            if (!mine.isEmpty()) {
                try {
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new BarFxClientboundPacket(mine));
                } catch (Exception ignored) { }
            }
        }
    }

    static void clearAll() {
        QUEUE.clear();
        LAST_NANO.clear();
        muteExplosions = 0;
    }
}
