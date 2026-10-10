package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Ironhide Horde (Bonewright): <b>Totem of the Pack</b>. The Bonewright plants a bone totem at its feet for
 * {@link #LIFETIME_TICKS} ticks (one per Bonewright - a new one replaces the old). Whenever a friendly unit dies
 * within {@link #RADIUS} blocks of it, a spectral wolf - RoN's tamed wolf unit, the same one capture camps hire -
 * leaps up where it fell and fights for {@link #WOLF_TICKS} ticks, at most {@link #MAX_WOLVES} alive per totem.
 * Rewards holding ground around the totem; it does nothing on its own. {@link #CD_SECONDS} s cooldown.
 * <p>
 * Spectral wolves are free: they leave no wreck (WreckServerEvents asks {@link #isSpectral}), never call up wolves
 * of their own, and need population room like the Bone Dragon's risen skeletons. The totem is two vanilla display
 * entities (bone pole + skull); a stale one left over from a save is discarded when it loads.
 * Registered as an event class: deaths spawn wolves, the server tick expires wolves and totems.
 */
public class TotemOfThePack extends Ability {

    public static final int CD_SECONDS = 60;
    public static final int LIFETIME_TICKS = 60 * 20;
    public static final float RADIUS = 10f;
    public static final int WOLF_TICKS = 15 * 20;
    public static final int MAX_WOLVES = 3;
    public static final String TOTEM_TAG = "bt_pack_totem";
    public static final String WOLF_TAG = "bt_spectral_wolf";
    static final String KEY_EXPIRE = "bt_spectral_expire";

    /** One planted totem. Public for the game test. */
    public static class Totem {
        public final ServerLevel level;
        public final Vec3 pos;
        public final String owner;
        final int casterId;
        final long expiresAt;
        final List<Entity> displays = new ArrayList<>(2);
        public final List<Entity> wolves = new ArrayList<>(MAX_WOLVES);

        Totem(ServerLevel level, Vec3 pos, String owner, int casterId, long expiresAt) {
            this.level = level;
            this.pos = pos;
            this.owner = owner;
            this.casterId = casterId;
            this.expiresAt = expiresAt;
        }
    }

    // one per Bonewright at most, keyed by its entity id; server thread only
    static final Map<Integer, Totem> TOTEMS = new HashMap<>();
    // every live spectral wolf, so the once-a-second expiry pass never scans the whole unit list
    static final List<Entity> WOLVES = new ArrayList<>();
    // leftovers from a save, discarded on the next server tick (see onJoin)
    static final List<Entity> STALE = new ArrayList<>();

    public TotemOfThePack() {
        // one click, one totem: a box-selected group of Bonewrights must not plant a forest
        super(UnitAction.TOTEM_OF_THE_PACK, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, true);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Totem of the Pack",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/bone.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.TOTEM_OF_THE_PACK),
            null,
            List.of(
                FormattedCharSequence.forward("Totem of the Pack  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Plants a bone totem for " + LIFETIME_TICKS / 20 + " s. When a friendly unit dies within "
                    + (int) RADIUS + " blocks, a spectral wolf rises for " + WOLF_TICKS / 20 + " s (max " + MAX_WOLVES + ").", Style.EMPTY),
                FormattedCharSequence.forward("Wolves need population room.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        plant(sl, self, unitUsing.getOwnerName(), self.position());
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    public static boolean isSpectral(Entity e) {
        return e != null && e.getTags().contains(WOLF_TAG);
    }

    /** Plants a totem for {@code caster} at {@code at}, replacing its previous one. Public for the game test. */
    public static Totem plant(ServerLevel sl, LivingEntity caster, String owner, Vec3 at) {
        Totem old = TOTEMS.remove(caster.getId());
        if (old != null)
            removeDisplays(old);
        Totem t = new Totem(sl, at, owner, caster.getId(), sl.getGameTime() + LIFETIME_TICKS);
        TOTEMS.put(caster.getId(), t);   // tracked before the displays join, so onJoin lets them live
        Entity pole = EntityType.BLOCK_DISPLAY.create(sl);
        if (pole != null) {
            CompoundTag tag = new CompoundTag();
            pole.saveWithoutId(tag);
            tag.put("block_state", NbtUtils.writeBlockState(Blocks.BONE_BLOCK.defaultBlockState()));
            tag.put("transformation", transform(-0.15f, 0f, -0.15f, 0.3f, 1.6f, 0.3f));
            pole.load(tag);
            pole.moveTo(at.x, at.y, at.z, 0, 0);
            pole.addTag(TOTEM_TAG);
            t.displays.add(pole);
            sl.addFreshEntity(pole);
        }
        Entity skull = EntityType.ITEM_DISPLAY.create(sl);
        if (skull != null) {
            CompoundTag tag = new CompoundTag();
            skull.saveWithoutId(tag);
            tag.put("item", new ItemStack(Items.SKELETON_SKULL).save(new CompoundTag()));
            tag.put("transformation", transform(0f, 0f, 0f, 0.7f, 0.7f, 0.7f));
            skull.load(tag);
            skull.moveTo(at.x, at.y + 1.85, at.z, caster.getYRot(), 0);
            skull.addTag(TOTEM_TAG);
            t.displays.add(skull);
            sl.addFreshEntity(skull);
        }
        sl.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 12, 0.3, 0.6, 0.3, 0.02);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.BONE_BLOCK_PLACE, SoundSource.NEUTRAL, 2f, 0.7f);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.WOLF_HOWL, SoundSource.NEUTRAL, 1.5f, 0.8f);
        return t;
    }

    static CompoundTag transform(float tx, float ty, float tz, float sx, float sy, float sz) {
        CompoundTag tf = new CompoundTag();
        tf.put("left_rotation", floats(0, 0, 0, 1));
        tf.put("right_rotation", floats(0, 0, 0, 1));
        tf.put("translation", floats(tx, ty, tz));
        tf.put("scale", floats(sx, sy, sz));
        return tf;
    }

    static ListTag floats(float... v) {
        ListTag list = new ListTag();
        for (float f : v)
            list.add(FloatTag.valueOf(f));
        return list;
    }

    /** Takes a totem down early (the game test cleans up after itself with this); its wolves keep their own timer. */
    public static void dispel(Totem t) {
        TOTEMS.values().remove(t);
        removeDisplays(t);
    }

    static void removeDisplays(Totem t) {
        for (Entity e : t.displays)
            e.discard();
        t.displays.clear();
    }

    /**
     * A unit died: if it was a friend of a live totem within range that has wolf room (and its owner has population
     * room), a spectral wolf rises where it fell. Returns the wolf, or null. Public for the game test.
     */
    public static Entity answerDeath(ServerLevel sl, LivingEntity victim) {
        if (TOTEMS.isEmpty() || !(victim instanceof Unit v) || isSpectral(victim))
            return null;
        String vo = v.getOwnerName();
        if (vo == null || vo.isEmpty())
            return null;
        long now = sl.getGameTime();
        double r2 = RADIUS * RADIUS;
        for (Totem t : TOTEMS.values()) {
            if (t.level != sl || now >= t.expiresAt || victim.distanceToSqr(t.pos) > r2)
                continue;
            if (!t.owner.equals(vo) && !AlliancesServerEvents.isAllied(t.owner, vo))
                continue;
            t.wolves.removeIf(w -> w.isRemoved() || !w.isAlive());
            if (t.wolves.size() >= MAX_WOLVES)
                continue;
            if (UnitServerEvents.getCurrentPopulation(t.owner) + 1 > BuildingServerEvents.getTotalPopulationSupply(t.owner))
                return null;
            Entity wolf = UnitServerEvents.spawnMob(EntityRegistrar.WOLF_UNIT.get(), sl, victim.blockPosition().below(), t.owner);
            if (wolf == null)
                return null;
            wolf.addTag(WOLF_TAG);
            wolf.getPersistentData().putLong(KEY_EXPIRE, now + WOLF_TICKS);
            t.wolves.add(wolf);
            WOLVES.add(wolf);
            sl.sendParticles(ParticleTypes.SOUL, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 12, 0.3, 0.4, 0.3, 0.03);
            sl.sendParticles(ParticleTypes.SOUL, t.pos.x, t.pos.y + 1.8, t.pos.z, 4, 0.1, 0.1, 0.1, 0.02);
            sl.playSound(null, wolf.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.NEUTRAL, 1.5f, 1.3f);
            return wolf;
        }
        return null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        if (TOTEMS.isEmpty() || !(evt.getEntity().level() instanceof ServerLevel sl))
            return;
        answerDeath(sl, evt.getEntity());
    }

    /**
     * A totem display or spectral wolf that (re)loads from disk - a save, or its chunk unloading - is a new entity
     * object this class doesn't track, so its timer is lost: queue it to be discarded on the next tick (discarding
     * inside the join event itself is unsafe for an entity still being loaded). A freshly spawned wolf is not caught
     * here: spawnMob adds it to the level before it gets its tag.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide())
            return;
        Entity e = evt.getEntity();
        if (e.getTags().contains(TOTEM_TAG)) {
            for (Totem t : TOTEMS.values())
                if (t.displays.contains(e))
                    return;
            STALE.add(e);
        } else if (isSpectral(e) && !WOLVES.contains(e)) {
            STALE.add(e);
        }
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
        if (TOTEMS.isEmpty() && WOLVES.isEmpty())
            return;
        long tick = evt.getServer().getTickCount();
        if (tick % 10 != 0)
            return;
        Iterator<Totem> it = TOTEMS.values().iterator();
        while (it.hasNext()) {
            Totem t = it.next();
            if (t.level.getServer() != evt.getServer())
                continue;
            if (t.level.getGameTime() >= t.expiresAt) {
                t.level.sendParticles(ParticleTypes.ASH, t.pos.x, t.pos.y + 1, t.pos.z, 15, 0.2, 0.6, 0.2, 0.02);
                removeDisplays(t);
                it.remove();
                continue;
            }
            // a wisp at the skull every half second: marks a live totem without flooding the client
            t.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, t.pos.x, t.pos.y + 2.1, t.pos.z, 1, 0.08, 0.05, 0.08, 0.0);
        }
        if (tick % 20 != 0)
            return;
        Iterator<Entity> wi = WOLVES.iterator();
        while (wi.hasNext()) {
            Entity w = wi.next();
            if (w.isRemoved()) {
                wi.remove();
                continue;
            }
            if (!(w.level() instanceof ServerLevel wl))
                continue;
            if (wl.getGameTime() >= w.getPersistentData().getLong(KEY_EXPIRE)) {
                wl.sendParticles(ParticleTypes.SOUL, w.getX(), w.getY() + 0.5, w.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
                w.discard();   // fades: no death event, so no wreck, no kill credit, no new wolf
                wi.remove();
            } else {
                wl.sendParticles(ParticleTypes.SOUL, w.getX(), w.getY() + 0.6, w.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        TOTEMS.clear();
        WOLVES.clear();
        STALE.clear();
    }
}
