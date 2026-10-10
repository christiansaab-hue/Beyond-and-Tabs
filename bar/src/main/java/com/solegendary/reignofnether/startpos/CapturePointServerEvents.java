package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Capturable neutral buildings (lovish, Oct 10: "buildings that can be captured that can provide helpful benefits
 * or units or an advantage"). The battlefield stamps a few small ruins between the bases; a player takes one by
 * keeping units inside {@link #RADIUS} blocks with no enemy units there, over {@link #CAPTURE_SECONDS} seconds.
 * An enemy standing alone on an owned site first drains it back to neutral, then takes it. Kinds:
 * <ul>
 *   <li><b>Ancient Mine</b> - +{@link #MINE_METAL} metal/s to the owner.</li>
 *   <li><b>Ley Shrine</b> - +{@link #SHRINE_ENERGY} energy/s to the owner.</li>
 *   <li><b>Mercenary Camp</b> - a hired wolf joins the owner every {@link #CAMP_INTERVAL_SECONDS} s, at most
 *       {@link #CAMP_MAX} alive at once.</li>
 * </ul>
 * Each site is a vanilla marker entity (invisible, saved with the world) carrying its kind, owner and progress;
 * the ruin itself is ordinary blocks, and its crown wool takes the owner's colour. Once a second, cheap.
 */
public class CapturePointServerEvents {

    public static final String TAG = "bt_capture_point";
    static final String KEY_KIND = "bt_cp_kind", KEY_OWNER = "bt_cp_owner", KEY_PROGRESS = "bt_cp_progress",
        KEY_CLAIMANT = "bt_cp_claimant", KEY_CAMP_TIMER = "bt_cp_camp";

    public static final double RADIUS = 6;
    public static final int CAPTURE_SECONDS = 20;
    public static final float MINE_METAL = 3f;
    public static final float SHRINE_ENERGY = 30f;
    public static final int CAMP_INTERVAL_SECONDS = 60;
    public static final int CAMP_MAX = 3;

    public enum Kind { MINE, SHRINE, CAMP }

    private static final List<Entity> points = new ArrayList<>();
    private static final Map<String, List<Entity>> hired = new HashMap<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // UnitGrid query results, reused

    public static List<Entity> getPoints() { return points; }
    public static String ownerOf(Entity p) { return p.getPersistentData().getString(KEY_OWNER); }
    public static float progressOf(Entity p) { return p.getPersistentData().getFloat(KEY_PROGRESS); }
    public static Kind kindOf(Entity p) {
        int k = p.getPersistentData().getInt(KEY_KIND);
        return Kind.values()[Math.max(0, Math.min(Kind.values().length - 1, k))];
    }

    // ------------------------------------------------------------------------------------------ placement

    /** Builds a ruin of the given kind on the ground at (x, z) and registers its marker. */
    public static Entity place(ServerLevel level, int x, int z, Kind kind) {
        int y = MetalPatches.solidTop(level, x, z);
        BlockPos base = new BlockPos(x, y, z);
        Block stone = switch (kind) { case MINE -> Blocks.COBBLED_DEEPSLATE; case SHRINE -> Blocks.MOSSY_STONE_BRICKS; case CAMP -> Blocks.SPRUCE_PLANKS; };
        Block accent = switch (kind) { case MINE -> Blocks.RAW_IRON_BLOCK; case SHRINE -> Blocks.AMETHYST_BLOCK; case CAMP -> Blocks.HAY_BLOCK; };
        // a 5x5 worn plinth, four short pillars, a centre column with the kind's accent and a wool crown
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(base.offset(dx, 0, dz), (Math.abs(dx) == 2 && Math.abs(dz) == 2 ? Blocks.CRACKED_STONE_BRICKS : stone).defaultBlockState(), 3);
                for (int up = 1; up <= 4; up++)
                    if (!level.getBlockState(base.offset(dx, up, dz)).isAir())
                        level.setBlock(base.offset(dx, up, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        for (int[] c : new int[][] { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 } }) {
            level.setBlock(base.offset(c[0], 1, c[1]), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(base.offset(c[0], 2, c[1]), Blocks.LANTERN.defaultBlockState(), 3);
        }
        level.setBlock(base.offset(0, 1, 0), stone.defaultBlockState(), 3);
        level.setBlock(base.offset(0, 2, 0), accent.defaultBlockState(), 3);
        level.setBlock(base.offset(0, 3, 0), Blocks.WHITE_WOOL.defaultBlockState(), 3);

        Entity marker = EntityType.MARKER.create(level);
        if (marker == null)
            return null;
        marker.moveTo(x + 0.5, y + 1, z + 0.5);
        marker.addTag(TAG);
        marker.getPersistentData().putInt(KEY_KIND, kind.ordinal());
        marker.getPersistentData().putString(KEY_OWNER, "");
        level.addFreshEntity(marker);   // onJoin registers it
        return marker;
    }

    /** Called by the battlefield: a handful of sites on a ring between the bases and the middle. */
    public static void stampFor(ServerLevel level, List<BlockPos> bases, float cx, float cz, float ringR) {
        int n = Math.max(3, bases.size() + 2);
        float r = Math.max(30, ringR * 0.7f);
        double offset = Math.PI / Math.max(1, bases.size()) / 2;   // between the lanes, not on them
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + offset;
            Kind kind = Kind.values()[i % Kind.values().length];
            place(level, (int) (cx + Math.cos(a) * r), (int) (cz + Math.sin(a) * r), kind);
        }
        ReignOfNether.LOGGER.info("[CapturePoints] placed {} neutral sites at radius {}", n, (int) r);
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (!evt.getLevel().isClientSide() && evt.getEntity().getTags().contains(TAG) && !points.contains(evt.getEntity()))
            points.add(evt.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        points.clear();
        hired.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        ServerLevel level = evt.getServer().overworld();
        if (level.getGameTime() % 20 != 0)
            return;
        // clients can't see the marker entities, so they get the site list (for the strategic-view flags) every
        // 5 s - also when empty, so a client never keeps flags from a previous map - and right after a change of owner
        if (ownersDirty || level.getGameTime() % SYNC_TICKS == 0) {
            ownersDirty = false;
            points.removeIf(Entity::isRemoved);
            CapturePointsClientboundPacket.sendAll(points);
        }
        if (points.isEmpty())
            return;
        tick(level, 1f);
    }

    private static final int SYNC_TICKS = 100;
    private static boolean ownersDirty = false;

    /** One step of capture + benefits covering {@code seconds}. Public for the game test. */
    public static void tick(ServerLevel level, float seconds) {
        points.removeIf(Entity::isRemoved);
        for (Entity p : points)
            if (p.level() == level)
                tickPoint(level, p, seconds);
    }

    static void tickPoint(ServerLevel level, Entity p, float seconds) {
        // who is standing on it? one "side" = a player and their allies
        String side = null;
        boolean contested = false;
        double r2 = RADIUS * RADIUS;
        // only the units in the grid cells round the site, not every unit on the map per site
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitGrid.near(level, p.getX(), p.getZ(), RADIUS, scratch)) {
            if (!(le instanceof Unit u) || !le.isAlive() || le.distanceToSqr(p) > r2)
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty())
                continue;
            if (side == null)
                side = o;
            else if (!side.equals(o) && !AlliancesServerEvents.isAllied(side, o))
                contested = true;
        }
        String owner = ownerOf(p);
        float step = seconds / CAPTURE_SECONDS;
        if (side != null && !contested) {
            boolean friendly = !owner.isEmpty() && (owner.equals(side) || AlliancesServerEvents.isAllied(owner, side));
            if (!friendly) {
                float prog = progressOf(p);
                if (!owner.isEmpty()) {   // drain the enemy's hold first
                    prog -= step;
                    if (prog <= 0) {
                        setOwner(level, p, "");
                        prog = 0;
                    }
                } else {
                    String claimant = p.getPersistentData().getString(KEY_CLAIMANT);
                    if (!side.equals(claimant)) {   // a new claimant starts over
                        p.getPersistentData().putString(KEY_CLAIMANT, side);
                        prog = 0;
                    }
                    prog += step;
                    if (prog >= 1) {
                        setOwner(level, p, side);
                        prog = 1;
                    }
                }
                p.getPersistentData().putFloat(KEY_PROGRESS, prog);
                level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 2, p.getZ(), 6, 0.6, 0.6, 0.6, 0.2);
            } else if (progressOf(p) < 1) {   // owner standing on a dented hold restores it
                p.getPersistentData().putFloat(KEY_PROGRESS, Math.min(1, progressOf(p) + step));
            }
        }
        owner = ownerOf(p);
        if (!owner.isEmpty())
            reward(level, p, owner, seconds);
    }

    static void setOwner(ServerLevel level, Entity p, String owner) {
        p.getPersistentData().putString(KEY_OWNER, owner);
        ownersDirty = true;   // resync clients on the next second
        BlockPos crown = BlockPos.containing(p.getX(), p.getY() + 2, p.getZ());
        level.setBlock(crown, woolFor(owner), 3);
        level.playSound(null, crown, owner.isEmpty() ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE,
            SoundSource.NEUTRAL, 2f, 1f);
        if (!owner.isEmpty())
            PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.capture_taken", false, owner,
                net.minecraft.network.chat.Component.translatable("capture.reignofnether." + kindOf(p).name().toLowerCase()));
    }

    static BlockState woolFor(String owner) {
        if (owner.isEmpty())
            return Blocks.WHITE_WOOL.defaultBlockState();
        for (RTSPlayer rp : PlayerServerEvents.rtsPlayers)
            if (rp.name.equals(owner)) {
                PlayerPalette.Entry e = PlayerPalette.byMapColorId(rp.startPosColorId);
                DyeColor dye = e == null ? DyeColor.RED : DyeColor.byName(e.name(), DyeColor.RED);
                Block wool = switch (dye) {
                    case ORANGE -> Blocks.ORANGE_WOOL; case MAGENTA -> Blocks.MAGENTA_WOOL; case LIGHT_BLUE -> Blocks.LIGHT_BLUE_WOOL;
                    case YELLOW -> Blocks.YELLOW_WOOL; case LIME -> Blocks.LIME_WOOL; case PINK -> Blocks.PINK_WOOL;
                    case GRAY -> Blocks.GRAY_WOOL; case LIGHT_GRAY -> Blocks.LIGHT_GRAY_WOOL; case CYAN -> Blocks.CYAN_WOOL;
                    case PURPLE -> Blocks.PURPLE_WOOL; case BLUE -> Blocks.BLUE_WOOL; case BROWN -> Blocks.BROWN_WOOL;
                    case GREEN -> Blocks.GREEN_WOOL; case BLACK -> Blocks.BLACK_WOOL; case WHITE -> Blocks.WHITE_WOOL;
                    default -> Blocks.RED_WOOL;
                };
                return wool.defaultBlockState();
            }
        return Blocks.RED_WOOL.defaultBlockState();
    }

    static void reward(ServerLevel level, Entity p, String owner, float seconds) {
        switch (kindOf(p)) {
            case MINE -> EconomyServerEvents.addReclaimedMetal(owner, MINE_METAL * seconds);
            case SHRINE -> {
                var eco = EconomyServerEvents.getEconomy(owner);
                for (Resources res : ResourcesServerEvents.resourcesList)
                    if (res.ownerName.equals(owner) && res.getEnergy() < eco.energyStorage)
                        res.addEnergy(Math.min(SHRINE_ENERGY * seconds, eco.energyStorage - res.getEnergy()));
            }
            case CAMP -> {
                int t = p.getPersistentData().getInt(KEY_CAMP_TIMER) + (int) seconds;
                if (t >= CAMP_INTERVAL_SECONDS) {
                    t = 0;
                    List<Entity> mine = hired.computeIfAbsent(owner, k -> new ArrayList<>());
                    mine.removeIf(e -> e.isRemoved() || !e.isAlive());
                    if (mine.size() < CAMP_MAX) {
                        Entity wolf = UnitServerEvents.spawnMob(EntityRegistrar.WOLF_UNIT.get(), level,
                            BlockPos.containing(p.getX(), p.getY() - 1, p.getZ() + 3), owner);
                        if (wolf != null)
                            mine.add(wolf);
                    }
                }
                p.getPersistentData().putInt(KEY_CAMP_TIMER, t);
            }
        }
    }
}
