package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.resources.ResourcesClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow private static double viewScale;
    @Shadow private AABB bb;
    @Shadow public abstract EntityType<?> getType();

    @Inject(
        method = "shouldRenderAtSqrDistance(D)Z",
        at = @At("HEAD"),
        cancellable=true
    )
    private void shouldRenderAtSqrDistance(
            double pDistance, CallbackInfoReturnable<Boolean> cir
    ) {
        if (!OrthoviewClientEvents.isEnabled())
            return;

        // Vanilla culls mobs further than ~64 * bbSize blocks from the camera (about 68 blocks for a skeleton).
        // The orthoview camera floats 30+ blocks above the ground (higher still when lifted over tall terrain
        // such as Nether-base pillars) and sits ~24 blocks behind the player, so units near the middle/top of
        // the screen crossed that limit and their models vanished while their selection outlines and rings
        // (drawn separately) remained: the "empty white boxes" bug. Distance doesn't change on-screen size in an
        // orthographic projection, so skip the distance cull entirely; frustum culling still drops anything
        // off-screen and the server's tracking range bounds what exists clientside at all.
        if (this.getType() != EntityType.ITEM) {
            cir.setReturnValue(true);
            return;
        }

        double d0 = this.bb.getSize();
        if (Double.isNaN(d0)) {
            d0 = 1.0D;
        }
        // make item entities render at 4x normal distance
        d0 *= 64.0D * viewScale * 4;
        cir.setReturnValue(pDistance < d0 * d0);
    }

    // use this mixin if you want a mob to avoid damage and not even register a damage animation
    @Inject(
            method = "isInvulnerableTo",
            at = @At("HEAD"),
            cancellable=true
    )
    private void isInvulnerableTo(DamageSource pSource, CallbackInfoReturnable<Boolean> cir) {
        if (pSource == damageSources().inWall())
            cir.setReturnValue(true);
    }

    @Shadow public int getTicksRequiredToFreeze() { return 140; }
    @Shadow public int getTicksFrozen() { return 0; }

    @Shadow public abstract DamageSources damageSources();
    @Shadow public abstract Component getName();
    @Shadow public abstract void remove(Entity.RemovalReason pReason);
    @Shadow public abstract AABB getBoundingBox();
    @Shadow public abstract BlockPos getOnPos();
    @Shadow public abstract Level level();


    @Shadow private Level level;

    @Inject(
            method = "getPercentFrozen",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void getPercentFrozen(CallbackInfoReturnable<Float> cir) {
        int i = this.getTicksRequiredToFreeze();
        float percent = (float)Math.min(this.getTicksFrozen(), 140) / (float)i;
        cir.setReturnValue(Math.min(percent, 0.5f));
    }

    @Inject(
            method = "extinguishFire()V",
            at = @At("HEAD"),
            cancellable = true
    )
    public void extinguishFire(CallbackInfo ci) {
        if ((Object)this instanceof LivingEntity le && le.hasEffect(MobEffectRegistrar.SOULS_AFLAME.get())) {
            ci.cancel();
        }
    }

    @Inject(
            method = "playEntityOnFireExtinguishedSound",
            at = @At("HEAD"),
            cancellable = true
    )
    public void playEntityOnFireExtinguishedSound(CallbackInfo ci) {
        if ((Object)this instanceof LivingEntity le && le.hasEffect(MobEffectRegistrar.SOULS_AFLAME.get())) {
            ci.cancel();
        }
    }

    @Inject(
            method = "setRemainingFireTicks",
            at = @At("HEAD"),
            cancellable = true
    )
    public void setRemainingFireTicks(int pRemainingFireTicks, CallbackInfo ci) {
        if (pRemainingFireTicks <= 0 && (Object)this instanceof LivingEntity le &&
            le.hasEffect(MobEffectRegistrar.SOULS_AFLAME.get())) {
            ci.cancel();
        }
    }

    // use this mixin if you want a mob to avoid damage and not even register a damage animation
    @Inject(
            method = "isCurrentlyGlowing",
            at = @At("HEAD"),
            cancellable=true
    )
    private void isCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide() && MinimapClientEvents.shouldHighlightAnimals()) {
            if ((Object) this instanceof LivingEntity le && ResourceSources.isHuntableAnimal(le)) {
                cir.setReturnValue(true);
            }
        }
    }


    // Beyond and Tabs: a cloaked unit (Shade Ranger, or any unit hidden in a Verdant thicket - ThicketCover) is
    // invisible only to its enemies. Its owner and allies get vanilla's translucent ghost, so they can still see and
    // command it; everyone else sees nothing (FogOfWarClientEvents also drops its armour and held items).
    @Inject(
            method = "isInvisibleTo",
            at = @At("HEAD"),
            cancellable = true
    )
    private void isInvisibleTo(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player == null || !((Object) this instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit))
            return;
        String owner = unit.getOwnerName();
        if (owner == null || owner.isBlank())
            return;
        String viewer = player.getName().getString();
        boolean friendly = owner.equals(viewer) || (level.isClientSide()
                ? com.solegendary.reignofnether.alliance.AlliancesClient.isAllied(owner, viewer)
                : com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(owner, viewer));
        if (friendly)
            cir.setReturnValue(false);
    }

}
