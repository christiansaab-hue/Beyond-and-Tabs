package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

/**
 * BAR's one-click mex: with a worker selected, right-clicking a metal patch (the raw iron in the ground) queues
 * your faction's Metal Extractor centred on it - no trip to the build menu. The patch you're pointing at flashes
 * a hint in the HUD while hovered.
 */
public class PatchQuickBuildClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    static long lastHintAt = 0;

    static Building extractorFor(Faction faction) {
        if (faction == null)
            return null;
        if (faction.equals(Factions.VILLAGERS))
            return Buildings.METAL_EXTRACTOR_VILLAGERS;
        if (faction.equals(Factions.MONSTERS))
            return Buildings.METAL_EXTRACTOR_MONSTERS;
        if (faction.equals(Factions.PIGLINS))
            return Buildings.METAL_EXTRACTOR_PIGLINS;
        return null;
    }

    /** The hovered patch block, if the cursor is on (or right above) one. */
    static BlockPos hoveredPatch() {
        if (MC.level == null)
            return null;
        BlockPos bp = CursorClientEvents.getPreselectedBlockPos();
        if (bp == null)
            return null;
        for (BlockPos p : new BlockPos[]{ bp, bp.below(), bp.above() })
            if (MC.level.getBlockState(p).is(MetalPatches.PATCH_BLOCK))
                return p;
        return null;
    }

    static boolean hasSelectedWorker() {
        for (LivingEntity entity : UnitClientEvents.getSelectedUnits())
            if (entity instanceof WorkerUnit)
                return true;
        return false;
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_2 || !OrthoviewClientEvents.isEnabled()
                || MC.level == null || MC.player == null)
            return;
        if (BuildingClientEvents.getBuildingToPlace() != null || HudClientEvents.isMouseOverAnyButtonOrHud())
            return;
        if (!hasSelectedWorker())
            return;
        BlockPos patch = hoveredPatch();
        if (patch == null)
            return;
        Building extractor = extractorFor(PlayerClientEvents.getFaction());
        if (!(extractor instanceof MetalExtractor))
            return;
        ArrayList<Integer> builderIds = new ArrayList<>();
        for (LivingEntity entity : UnitClientEvents.getSelectedUnits())
            if (entity instanceof WorkerUnit)
                builderIds.add(entity.getId());
        int[] ids = new int[builderIds.size()];
        for (int i = 0; i < ids.length; i++)
            ids[i] = builderIds.get(i);

        // BAR's area mex: Shift+right-click queues an extractor on this patch and every free patch nearby,
        // nearest first, all on the same workers (they walk the circuit)
        java.util.List<BlockPos> targets = com.solegendary.reignofnether.keybinds.Keybindings.shiftMod.isDown()
                ? freePatchesNear(patch, 40) : java.util.List.of(patch);
        int queued = 0;
        String firstError = null;
        for (BlockPos target : targets) {
            BlockPos originPos = target.offset(-2, 0, -2);   // 5x5 centred on the patch; the origin IS the ground block (blocks go at origin.y+1)
            // validate here so the player hears WHY when a patch can't take an extractor (the server rejects silently)
            String error = BuildingValidators.getPlacementValidityError(MC.level, extractor, originPos,
                    MC.player.getName().getString(), Rotation.NONE, false, false, true);
            if (error != null) {
                if (firstError == null) firstError = error;
                continue;
            }
            BuildingServerboundPacket.placeAndQueueBuilding(extractor, originPos, Rotation.NONE,
                    MC.player.getName().getString(), ids, queued > 0);
            queued++;
        }
        if (queued == 0) {
            HudClientEvents.showTemporaryMessage(Component.translatable("hud.reignofnether.extractor_blocked",
                    firstError != null ? Component.translatable(firstError).getString() : "").getString());
            evt.setCanceled(true);
            return;
        }
        HudClientEvents.showTemporaryMessage(queued > 1
                ? Component.translatable("hud.reignofnether.building_extractors", queued).getString()
                : Component.translatable("hud.reignofnether.building_extractor").getString());
        evt.setCanceled(true);   // don't also issue a move order
    }

    /**
     * Patch centres within range of `from` with no building on them yet, nearest first. Patches are found by
     * scanning the loaded surface for patch blocks and clustering the plus-shapes into centres.
     */
    static java.util.List<BlockPos> freePatchesNear(BlockPos from, int range) {
        java.util.List<BlockPos> blocks = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = from.getX() - range; x <= from.getX() + range; x++)
            for (int z = from.getZ() - range; z <= from.getZ() + range; z++) {
                int top = MC.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = top + 1; y >= top - 1; y--)
                    if (MC.level.getBlockState(p.set(x, y, z)).is(MetalPatches.PATCH_BLOCK)) {
                        blocks.add(new BlockPos(x, y, z));
                        break;
                    }
            }
        java.util.List<BlockPos> centres = new ArrayList<>();
        for (BlockPos b : blocks) {   // cluster the plus shapes: keep one centre per group
            boolean near = false;
            for (int i = 0; i < centres.size(); i++)
                if (centres.get(i).distSqr(b) < 6 * 6) {
                    near = true;
                    break;
                }
            if (!near)
                centres.add(b);
        }
        centres.removeIf(c -> {
            for (BuildingPlacement bp : BuildingClientEvents.getBuildings())
                if (!bp.isDestroyedServerside && bp.originPos.distSqr(c) < 9 * 9)
                    return true;
            return false;
        });
        centres.sort((a, b) -> Double.compare(a.distSqr(from), b.distSqr(from)));
        return centres;
    }

    /** A gentle hint while hovering a patch with a worker selected. */
    @SubscribeEvent
    public static void onRenderTick(net.minecraftforge.event.TickEvent.RenderTickEvent evt) {
        if (evt.phase != net.minecraftforge.event.TickEvent.Phase.END || !OrthoviewClientEvents.isEnabled())
            return;
        if (BuildingClientEvents.getBuildingToPlace() != null || !hasSelectedWorker())
            return;
        if (hoveredPatch() == null)
            return;
        long now = System.currentTimeMillis();
        if (now - lastHintAt > 2500) {
            lastHintAt = now;
            HudClientEvents.showTemporaryMessage(Component.translatable("hud.reignofnether.patch_hint").getString());
        }
    }
}
