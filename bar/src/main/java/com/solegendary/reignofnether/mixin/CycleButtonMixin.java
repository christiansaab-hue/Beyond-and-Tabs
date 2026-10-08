package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.blocks.RTSStructureBlock;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.properties.StructureMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CycleButton.class)
public abstract class CycleButtonMixin<T> {

    @Shadow private T value;

    // prevent showing the load screen on an RTS Structure block
    @Inject(
            method = "cycleValue",
            at = @At("TAIL")
    )
    private void cycleValue(int pDelta, CallbackInfo ci) {
        Minecraft MC = Minecraft.getInstance();
        if (value == StructureMode.LOAD && MC.level != null && MC.player != null) {
            if (PlayerClientEvents.lastUsedBlockState != null &&
                PlayerClientEvents.lastUsedBlockState.getBlock() instanceof RTSStructureBlock)
                ((CycleButton)(Object)this).onPress();
        }
    }
}