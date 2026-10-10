package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Verdant Court (Elder Druid): <b>Awaken Thicket</b> (design/verdant_court_plan.md, slice 4). Aim a spot within
 * {@link #RANGE} blocks: the thicket there wakes as a Sentinel Treant (the T1 tank, same body and stats) that fights
 * for its owner for {@link #LIFETIME_TICKS} ticks, then sinks back into the ground. {@link #CD_SECONDS} s cooldown,
 * one treant per click (oneClickOneUse), so a box of Druids doesn't raise a grove at once.
 * <p>
 * An awakened treant is free and temporary like the Bonewright's spectral wolves: it leaves no wreck
 * (WreckServerEvents asks {@link #isAwakened}), fades without a death event, and a leftover from a save is discarded
 * when it loads (its timer lives here, not on disk). It does not need population room - it is a 30 s spell, and a
 * full army must still be able to cast it - but it counts toward population while it stands.
 * Registered as an event class: the server tick expires the treants.
 */
public class AwakenThicket extends Ability {

    public static final int CD_SECONDS = 45;
    public static final int LIFETIME_TICKS = 30 * 20;
    public static final int RANGE = 12;
    public static final String TAG = "bt_awakened_treant";
    static final String KEY_EXPIRE = "bt_awakened_expire";

    // every live awakened treant, so the expiry pass never scans the unit list; server thread only
    static final List<Entity> TREANTS = new ArrayList<>();
    // leftovers from a save, discarded on the next server tick (see onJoin)
    static final List<Entity> STALE = new ArrayList<>();

    public AwakenThicket() {
        super(UnitAction.AWAKEN_THICKET, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, true);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Awaken Thicket",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_side.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.AWAKEN_THICKET,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.AWAKEN_THICKET),
            null,
            List.of(
                FormattedCharSequence.forward("Awaken Thicket  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Wakes a treant at a spot within " + RANGE + " blocks. It fights for you for "
                    + LIFETIME_TICKS / 20 + " s, then sinks back into the ground.", Style.EMPTY),
                FormattedCharSequence.forward("The forest remembers who tends it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (awaken(sl, unitUsing.getOwnerName(), targetBp) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    public static boolean isAwakened(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    /**
     * Wakes a treant for {@code owner} on the ground at {@code at}'s column and returns it (null if it could not be
     * spawned). Public for the game test.
     */
    public static LivingEntity awaken(ServerLevel sl, String owner, BlockPos at) {
        // stand it on the surface of the clicked column (spawnMob puts the mob on top of the block it is given)
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 1;
        BlockPos ground = new BlockPos(at.getX(), Math.max(y, sl.getMinBuildHeight()), at.getZ());
        Entity e = UnitServerEvents.spawnMob(EntityRegistrar.SENTINEL_TREANT_UNIT.get(), sl, ground, owner);
        if (!(e instanceof LivingEntity treant))
            return null;
        treant.addTag(TAG);
        treant.getPersistentData().putLong(KEY_EXPIRE, sl.getGameTime() + LIFETIME_TICKS);
        TREANTS.add(treant);
        BlockParticleOption leaves = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AZALEA_LEAVES.defaultBlockState());
        BlockParticleOption soil = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
        sl.sendParticles(soil, treant.getX(), treant.getY() + 0.2, treant.getZ(), 30, 0.8, 0.2, 0.8, 0.1);
        sl.sendParticles(leaves, treant.getX(), treant.getY() + 1.6, treant.getZ(), 30, 0.7, 0.9, 0.7, 0.1);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, treant.getX(), treant.getY() + 1.2, treant.getZ(), 10, 0.6, 0.8, 0.6, 0.0);
        sl.playSound(null, treant.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 2f, 0.6f);
        sl.playSound(null, treant.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 2f, 0.5f);
        return treant;
    }

    /** Puts a treant back to sleep now (fades: no death event, so no wreck and no kill credit). */
    public static void dismiss(Entity treant) {
        if (treant.level() instanceof ServerLevel sl) {
            BlockParticleOption leaves = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AZALEA_LEAVES.defaultBlockState());
            sl.sendParticles(leaves, treant.getX(), treant.getY() + 1.2, treant.getZ(), 20, 0.6, 0.8, 0.6, 0.05);
            sl.playSound(null, treant.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 1.5f, 0.7f);
        }
        treant.discard();
        TREANTS.remove(treant);
    }

    /**
     * An awakened treant that (re)loads from disk - a save, or its chunk unloading - is a new entity object this class
     * doesn't track, so its timer is lost: queue it to be discarded on the next tick (discarding inside the join event
     * itself is unsafe for an entity still being loaded). A fresh one is not caught: spawnMob adds it to the level
     * before it gets its tag.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide())
            return;
        Entity e = evt.getEntity();
        if (isAwakened(e) && !TREANTS.contains(e))
            STALE.add(e);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (!STALE.isEmpty()) {
            for (Entity e : STALE)
                e.discard();
            STALE.clear();
        }
        if (TREANTS.isEmpty() || evt.getServer().getTickCount() % 20 != 0)
            return;
        Iterator<Entity> it = TREANTS.iterator();
        while (it.hasNext()) {
            Entity t = it.next();
            if (t.isRemoved()) {
                it.remove();
                continue;
            }
            if (!(t.level() instanceof ServerLevel tl) || tl.getServer() != evt.getServer())
                continue;
            if (tl.getGameTime() >= t.getPersistentData().getLong(KEY_EXPIRE)) {
                BlockParticleOption leaves = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AZALEA_LEAVES.defaultBlockState());
                tl.sendParticles(leaves, t.getX(), t.getY() + 1.2, t.getZ(), 20, 0.6, 0.8, 0.6, 0.05);
                tl.playSound(null, t.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 1.5f, 0.7f);
                t.discard();
                it.remove();
            } else {
                // a falling leaf a second marks it as a summon without flooding the client
                tl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, t.getX(), t.getY() + 2.4, t.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        TREANTS.clear();
        STALE.clear();
    }
}
