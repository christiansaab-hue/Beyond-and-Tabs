package com.solegendary.reignofnether.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin extends Projectile {

    @Shadow private ItemStack tridentItem;
    @Shadow private boolean dealtDamage;

    protected ThrownTridentMixin(EntityType<? extends Projectile> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    // replace bounce logic (on hitting an enemy at the time as another trident) with pierce logic instead
    @Inject(
            method = "onHitEntity",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void onHitEntity(EntityHitResult pResult, CallbackInfo ci) {
        ci.cancel();

        Entity $$1 = pResult.getEntity();
        float $$2 = 8.0F;
        if ($$1 instanceof LivingEntity $$3) {
            $$2 += EnchantmentHelper.getDamageBonus(this.tridentItem, $$3.getMobType());
        }
        Entity $$4 = this.getOwner();
        DamageSource $$5 = this.damageSources().trident(this, $$4 == null ? this : $$4);
        SoundEvent $$6 = SoundEvents.TRIDENT_HIT;
        if ($$1.hurt($$5, $$2)) {
            if ($$1.getType() == EntityType.ENDERMAN) {
                return;
            }
            if ($$1 instanceof LivingEntity) {
                LivingEntity $$7 = (LivingEntity)$$1;
                if ($$4 instanceof LivingEntity) {
                    this.dealtDamage = true;
                    this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01, -0.1, -0.01));
                    EnchantmentHelper.doPostHurtEffects($$7, $$4);
                    EnchantmentHelper.doPostDamageEffects((LivingEntity)$$4, $$7);
                }
            }
        }

        this.playSound($$6, 1.0F, 1.0F);
    }
}

