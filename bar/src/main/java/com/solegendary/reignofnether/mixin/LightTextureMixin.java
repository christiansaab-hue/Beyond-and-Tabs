package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An RTS is never played in the dark. While the RTS camera is on, block light below this floor is lifted so the
 * Fallen's permanent midnight (and any night) stays readable - shadows and torches still read, nothing is
 * fullbright. Off the RTS camera, vanilla lighting is untouched. (The gamma option clamps at 1.0, so this is the
 * standard way to do it.)
 */
@Mixin(LightTexture.class)
public class LightTextureMixin {

    private static final float RTS_MIN_BRIGHTNESS = 0.55f;

    @Inject(method = "getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F",
            at = @At("RETURN"), cancellable = true)
    private static void reignofnether$liftNightInRtsView(DimensionType dimensionType, int lightLevel,
                                                          CallbackInfoReturnable<Float> cir) {
        if (OrthoviewClientEvents.isEnabled() && cir.getReturnValueF() < RTS_MIN_BRIGHTNESS)
            cir.setReturnValue(RTS_MIN_BRIGHTNESS + cir.getReturnValueF() * 0.4f);
    }
}
