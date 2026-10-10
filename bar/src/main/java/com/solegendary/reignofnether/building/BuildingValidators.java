package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.piglins.CentralPortal;
import com.solegendary.reignofnether.building.buildings.piglins.PortalBasic;
import com.solegendary.reignofnether.building.buildings.piglins.PortalPocket;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.building.buildings.villagers.TownCentre;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.nether.NetherBlocks;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.building.BuildingUtils.isBridge;

public class BuildingValidators {

    // minimum % of blocks below a building that need to be supported by a solid block for it to be placeable
    // 1 means you can't have any gaps at all, 0 means you can place buildings in mid-air
    // BAR forgives gentle slopes, so this is lower than RoN's 0.6, and columns with a 1-block gap (filled with
    // terrain on placement, see BuildingServerEvents.levelFootprint) count as supported
    private static final float MIN_SUPPORTED_BLOCKS_PERCENT = 0.5f;
    // a footprint column with no ground within this many blocks below is hanging over a cliff
    private static final int CLIFF_DEPTH = 3;
    // max share of footprint columns allowed over a cliff (one corner over a hole is fine, a cliff edge isn't)
    private static final float MAX_CLIFF_COLUMNS_PERCENT = 0.25f;
    private static final float MIN_NETHER_BLOCKS_PERCENT = 0.8f; // piglin buildings must be build on at least 80%
    private static final int MIN_BRIDGE_SIZE = 10; // a bridge must have at least 10 blocks to be placeable
    private static final float MIN_BRIDGE_LIQUID_BLOCKS_PERCENT = 0.20f; // at least 20% of covered blocks must be liquid
    private static final float MAX_BRIDGE_LIQUID_BLOCKS_PERCENT = 0.95f; // at least 5% of covered blocks must be solid

    public static boolean isPlacementValid(Level level, Building building, BlockPos placementPos, String ownerName, Rotation rotation,
                                           boolean isDiagonalBridge, boolean isSandbox, boolean ignoreFog) {
        return getPlacementValidityError(level, building, placementPos, ownerName, rotation, isDiagonalBridge, isSandbox, ignoreFog) == null;
    }

    @Nullable
    public static String getPlacementValidityError(Level level, Building building, BlockPos originPos, String ownerName, Rotation rotation,
                                                   boolean isDiagonalBridge, boolean isSandbox, boolean ignoreFog) {
        if (level == null || building == null)
            return "Unknown error";

        ArrayList<BuildingBlock> relativeBlocks;
        if (building instanceof AbstractBridge bridge)
            relativeBlocks = bridge.getRelativeBlockData(level, isDiagonalBridge);
        else
            relativeBlocks = building.getRelativeBlockData(level);

        ArrayList<BuildingBlock> absoluteBlocks = BuildingUtils.getAbsoluteBlockData(relativeBlocks, level, originPos, rotation);

        if (isBuildingPlacementInAirOrOnIllegalBlocks(level, building, absoluteBlocks)) {
            return "building.reignofnether.ground_not_flat";
        } else if (isBuildingPlacementClipping(level, building, absoluteBlocks)) {
            return "building.reignofnether.ground_not_flat";
        } else if (isOverlappingAnyOtherBuilding(level, building, absoluteBlocks) && !isSandbox) {
            return "building.reignofnether.too_close";
        } else if (!isNonPiglinOrOnNetherBlocks(level, building, originPos, absoluteBlocks)) {
            return "building.reignofnether.must_be_nether";
        } else if (!isNonBridgeOrValidBridge(level, building, originPos, absoluteBlocks)) {
            return "building.reignofnether.must_be_liquid";
        } else if (!isInBrightChunk(level, absoluteBlocks, ownerName) && !isSandbox && !ignoreFog) {
            return "building.reignofnether.unexplored";
        } else if (!isBuildingPlacementWithinWorldBorder(level, building, absoluteBlocks)) {
            return "building.reignofnether.outside_map";
        } else if (!isNotTutorialOrNearValidCapitolPosition(level, building, originPos)) {
            return "building.reignofnether.build_centre_here";
        } else if (building instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor
                && !com.solegendary.reignofnether.resources.MetalPatches.isOnPatch(level, originPos, 5, 5)) {
            return "building.reignofnether.needs_metal_patch";
        }
        return null;
    }

    // Soft blocks (grass, flowers, snow layers, leaves...) never block a footprint: they are cleared on placement.
    // Fluids are NOT soft even though water/lava are replaceable - buildings never go in water.
    public static boolean isSoftBlock(BlockState bs) {
        if (!bs.getFluidState().isEmpty())
            return false;
        return bs.isAir() || bs.canBeReplaced() || bs.getBlock() instanceof LeavesBlock
                || bs.getBlock() instanceof SnowLayerBlock || bs.getBlock() instanceof BushBlock;
    }

    // ground a building can stand on (leaves, barriers and bottom slabs don't count, as in RoN)
    public static boolean isSupportingBlock(BlockState bs) {
        return bs.isSolid() && !isSoftBlock(bs) &&
                !(bs.getBlock() instanceof BarrierBlock) &&
                !(bs.getBlock() instanceof SlabBlock && bs.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM);
    }

    // disallow any building block from clipping into any other existing blocks
    // BAR-style slope tolerance: soft blocks are ignored anywhere, and solid terrain poking up into the bottom layer
    // (a 1-block step) is allowed - the foundation replaces it / it is cleared on placement. Terrain any higher, or
    // any fluid, still clips (so the low side of a cliff is rejected).
    private static boolean isBuildingPlacementClipping(Level level, Building building, List<BuildingBlock> blocks) {
        if (level == null) {
            return false;
        }
        if (isBridge(building) || level.getGameRules().getRule(GameRuleRegistrar.SLANTED_BUILDING).get()) {
            return false;
        }
        int minY = BuildingUtils.getMinCorner(blocks).getY();
        for (BuildingBlock block : blocks) {
            BlockState bsBuilding = block.getBlockState();
            if (!bsBuilding.isSolid() && bsBuilding.getFluidState().isEmpty())
                continue;
            BlockPos bp = block.getBlockPos();
            BlockState bsWorld = level.getBlockState(bp);
            if (!bsWorld.getFluidState().isEmpty())
                return true;
            if (!bsWorld.isSolid() || isSoftBlock(bsWorld))
                continue;
            if (bp.getY() == minY)
                continue; // up to 1 block of terrain inside the bottom layer is levelled on placement
            return true;
        }
        return false;
    }

    // disallow the building borders from overlapping any other's, even if they don't collide physical blocks
    // also allow for a 1 block gap between buildings so units can spawn and stairs don't have their blockstates
    // messed up
    private static boolean isOverlappingAnyOtherBuilding(Level level, Building building, List<BuildingBlock> blocks) {
        List<BuildingPlacement> buildings = BuildingUtils.getBuildingsList(level.isClientSide());

        BlockPos minPos = BuildingUtils.getMinCorner(blocks);//.offset(-1, -1, -1);
        BlockPos maxPos = BuildingUtils.getMaxCorner(blocks);//.offset(1, 1, 1);

        for (BuildingPlacement bpl : buildings) {
            for (BuildingBlock block : bpl.blocks) {
                if (isBridge(building)) {
                    continue;
                }
                BlockPos bp = block.getBlockPos();
                if (bp.getX() >= minPos.getX() && bp.getX() <= maxPos.getX() &&
                    bp.getY() >= minPos.getY() && bp.getY() <= maxPos.getY() &&
                    bp.getZ() >= minPos.getZ() && bp.getZ() <= maxPos.getZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNonPiglinOrOnNetherBlocks(Level level, Building building, BlockPos originPos, List<BuildingBlock> blocks) {
        if (isBridge(building)) {
            return true;
        }
        boolean netherTerrainCustomBuilding = building instanceof CustomBuilding cb && cb.netherTerrainOnly;
        if (!netherTerrainCustomBuilding && !building.getFaction().equals(Factions.PIGLINS) || building instanceof CentralPortal) {
            return true;
        }
        // BAR's map economy is faction-neutral: the Legion's extractors and windmills go on any ground, because
        // metal patches and open wind are out on the map, far beyond the portal's netherrack
        if (building instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor
                || building instanceof com.solegendary.reignofnether.building.buildings.shared.WindGenerator) {
            return true;
        }
        if (building instanceof PortalBasic || building instanceof PortalPocket) {
            return true;
        }
        // the shared economy buildings follow the metal patches and the wind, not the nether
        if (building instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor
                || building instanceof com.solegendary.reignofnether.building.buildings.shared.WindGenerator) {
            return true;
        }
        return isOnNetherBlocks(level, blocks, originPos, true);
    }

    public static boolean isOnNetherBlocks(Level level, List<BuildingBlock> blocks, BlockPos originPos, boolean absolutePos) {
        int netherBlocksBelow = 0;
        int blocksBelow = 0;
        int minY = BuildingUtils.getMinCorner(blocks).getY();
        for (BuildingBlock block : blocks) {
            if (block.getBlockPos().getY() == minY && level != null) {
                BlockPos bp = block.getBlockPos();
                if (!absolutePos)
                    bp = bp.offset(originPos);
                BlockState bs = block.getBlockState(); // building block
                if (bs.isSolid()) {
                    blocksBelow += 1;
                    if (NetherBlocks.isNetherBlock(level, bp.below())) {
                        netherBlocksBelow += 1;
                    }
                }
            }
        }
        if (blocksBelow <= 0) {
            return false; // avoid division by 0
        }
        return ((float) netherBlocksBelow / (float) blocksBelow) > MIN_NETHER_BLOCKS_PERCENT;
    }


    // Depth of the gap under a footprint column's bottom block: 0 = on ground (or terrain poking into the bottom
    // layer), 1 = a 1-block gap (filled on placement), ..., CLIFF_DEPTH + 1 = no ground found, -1 = water/lava below.
    public static int getGapDepth(Level level, BlockPos bottomPos) {
        if (isSupportingBlock(level.getBlockState(bottomPos)))
            return 0;
        for (int d = 0; d <= CLIFF_DEPTH; d++) {
            BlockState bs = level.getBlockState(bottomPos.below(d + 1));
            if (!bs.getFluidState().isEmpty())
                return -1;
            if (isSupportingBlock(bs))
                return d;
        }
        return CLIFF_DEPTH + 1;
    }

    // At least MIN_SUPPORTED_BLOCKS_PERCENT of the solid blocks at the base of the building must stand on ground
    // (directly, or over a 1-block gap that gets filled on placement), none may sit over water/lava, and at most
    // MAX_CLIFF_COLUMNS_PERCENT may hang over a drop deeper than CLIFF_DEPTH. Columns over ice are ignored as in RoN.
    private static boolean isBuildingPlacementInAirOrOnIllegalBlocks(Level level, Building building, List<BuildingBlock> blocks) {
        if (isBridge(building) || level.getGameRules().getRule(GameRuleRegistrar.SLANTED_BUILDING).get()) {
            return false;
        }
        BlockPos minPos = BuildingUtils.getMinCorner(blocks);
        int supportedColumns = 0;
        int cliffColumns = 0;
        int blocksBelow = 0;
        for (BuildingBlock block : blocks) {
            if (block.getBlockPos().getY() == minPos.getY()) {
                BlockPos bp = block.getBlockPos();
                BlockState bs = block.getBlockState(); // building block
                if (!bs.isSolid() || level.getBlockState(bp.below()).getBlock() instanceof IceBlock)
                    continue;
                blocksBelow += 1;
                int depth = getGapDepth(level, bp);
                if (depth < 0)
                    return true; // over water or lava
                if (depth <= 1)
                    supportedColumns += 1;
                else if (depth > CLIFF_DEPTH)
                    cliffColumns += 1;
            }
        }
        if (blocksBelow <= 0) {
            return false; // avoid division by 0
        }
        return ((float) supportedColumns / (float) blocksBelow) < MIN_SUPPORTED_BLOCKS_PERCENT ||
                ((float) cliffColumns / (float) blocksBelow) > MAX_CLIFF_COLUMNS_PERCENT;
    }

    private static boolean isBuildingPlacementWithinWorldBorder(Level level, Building building, List<BuildingBlock> blocks) {
        if (level == null || building == null)
            return false;
        if (level.getGameRules().getRule(GameRuleRegistrar.BUILDINGS_OUTSIDE_BORDER).get())
            return true;

        int minX = 999999;
        int minZ = 999999;
        int maxX = -999999;
        int maxZ = -999999;
        for (BuildingBlock block : blocks) {
            var bp = block.getBlockPos();
            if (bp.getX() < minX) {
                minX = bp.getX();
            }
            if (bp.getZ() < minZ) {
                minZ = bp.getZ();
            }
            if (bp.getX() > maxX) {
                maxX = bp.getX();
            }
            if (bp.getZ() > maxZ) {
                maxZ = bp.getZ();
            }
        }
        BlockPos minPos = BuildingUtils.getMinCorner(blocks);
        BlockPos maxPos = BuildingUtils.getMaxCorner(blocks);

        return level.getWorldBorder().isWithinBounds(minPos.getX(), minPos.getZ()) &&
                level.getWorldBorder().isWithinBounds(maxPos.getX(), maxPos.getZ()) &&
                level.getWorldBorder().isWithinBounds(maxPos.getX(), minPos.getZ()) &&
                level.getWorldBorder().isWithinBounds(minPos.getX(), maxPos.getZ());
    }


    // bridges should be connected to land or another bridge and be touching water
    private static boolean isNonBridgeOrValidBridge(Level level, Building building, BlockPos originPos, List<BuildingBlock> blocks) {
        if (!isBridge(building)) {
            return true;
        }
        BlockPos minPos = BuildingUtils.getMinCorner(blocks);
        int placeableBlocks = 0;
        for (BuildingBlock block : blocks)
            if (!AbstractBridge.shouldCullBlock(originPos, block, level, true) && !block.getBlockState()
                    .isAir()) {
                placeableBlocks += 1;
            }
        if (placeableBlocks < MIN_BRIDGE_SIZE) {
            return false;
        }

        int bridgeBlocks = 0;
        int waterBlocksClipping = 0;
        for (BuildingBlock block : blocks) {
            if (block.getBlockState().isAir()) {
                continue;
            }
            BlockPos bp = block.getBlockPos();
            BlockState bs = block.getBlockState(); // building block
            BlockState bsWorld = level.getBlockState(bp); // world block

            // top y level should not be touching any water at all
            if (block.getBlockPos().getY() == minPos.getY() + 1) {
                if ((bs.getBlock() instanceof FenceBlock) && !bsWorld.getFluidState().isEmpty()) {
                    return false;
                }
            }

            if (block.getBlockPos().getY() == minPos.getY()) {
                bridgeBlocks += 1;
                if (!bsWorld.getFluidState().isEmpty() || bsWorld.getBlock() instanceof SeagrassBlock
                        || bsWorld.getBlock() instanceof KelpBlock) {
                    waterBlocksClipping += 1;
                }
            }
        }
        if (bridgeBlocks <= 0) {
            return false; // avoid division by 0
        }
        float percentWater = (float) waterBlocksClipping / (float) bridgeBlocks;
        return percentWater > MIN_BRIDGE_LIQUID_BLOCKS_PERCENT && percentWater < MAX_BRIDGE_LIQUID_BLOCKS_PERCENT;
    }

    private static boolean isNotTutorialOrNearValidCapitolPosition(Level level, Building building, BlockPos originPos) {
        if (!level.isClientSide())
            return true;
        if (!TutorialClientEvents.isEnabled())
            return true;
        if (!(building instanceof TownCentre))
            return true;
        return TutorialClientEvents.BUILD_CAPITOL_POS.distSqr(originPos) < 625; // 25 block range
    }

    public static boolean isInBrightChunk(Level level, List<BuildingBlock> blocks, String ownerName) {
        BlockPos minPos = BuildingUtils.getMinCorner(blocks);
        BlockPos maxPos = BuildingUtils.getMaxCorner(blocks);
        BlockPos centrePos = new BlockPos((minPos.getX() + maxPos.getX()) / 2, (minPos.getY() + maxPos.getY()) / 2, (minPos.getZ() + maxPos.getZ()) / 2);
        return isInBrightChunk(level, centrePos, ownerName);
    }

    public static boolean isInBrightChunk(Level level, BlockPos centrePos, String ownerName) {
        if (level.isClientSide()) {
            return FogOfWarClientEvents.isInBrightChunk(centrePos);
        } else {
            return FogOfWarServerEvents.isBlockVisibleFor(ownerName, centrePos.getX(), centrePos.getZ());
        }
    }
}
