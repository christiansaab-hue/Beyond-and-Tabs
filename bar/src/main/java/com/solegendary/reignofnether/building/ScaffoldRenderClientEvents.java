package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Construction scaffolds: while a building is going up, a light timber (or bone, or blackstone) shell stands round
 * its footprint - poles at the corners and every ~3 blocks, a rail at each level up to one above the current work
 * face - so a construction site reads as a busy site from the RTS camera instead of a bare wireframe. When the
 * building completes, the scaffold fades out over {@link #FADE_TICKS} ticks.
 *
 * Pure client-side rendering (no blocks, no packets). Only the nearest {@link #MAX_SCAFFOLDS} sites are drawn,
 * nothing in strategic zoom, and the frame loop allocates nothing: quads are pushed straight from the cached
 * baked models into fixed buffers, the pose is rewritten in place per box, and selection uses fixed arrays.
 */
public class ScaffoldRenderClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    private static final int MAX_SCAFFOLDS = 24;
    private static final double MAX_RENDER_DIST_SQR = 160 * 160;
    private static final double LOD_DIST_SQR = 80 * 80;   // further than this: corner poles and every other rail only
    private static final float POLE_SPACING = 3f;
    private static final float POLE = 0.16f;
    private static final float RAIL = 0.11f;
    private static final float GAP = 0.55f;               // pole centres sit this far outside the footprint
    private static final int FADE_TICKS = 30;
    private static final int VIS_RECHECK_TICKS = 10;      // fog visibility is cached per site, not looked up per frame

    /** Per-placement bookkeeping; WeakHashMap so removed placements drop out without a cleanup pass. */
    private static final class State {
        boolean seenUnbuilt;    // only sites we watched being built get a fade (not ones that load in complete)
        long builtAt = -1;
        boolean visible;
        long visCheckedAt = Long.MIN_VALUE;
    }
    private static final Map<BuildingPlacement, State> states = new WeakHashMap<>();

    // nearest-N selection, reused every frame
    private static final BuildingPlacement[] picked = new BuildingPlacement[MAX_SCAFFOLDS];
    private static final double[] pickedDist = new double[MAX_SCAFFOLDS];
    private static final float[] pickedAlpha = new float[MAX_SCAFFOLDS];
    private static int pickedCount = 0;

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Matrix4f basePose = new Matrix4f();
    private static final Matrix3f baseNormal = new Matrix3f();
    private static final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();

    // per-draw context (render thread only)
    private static PoseStack.Pose pose;
    private static BlockRenderDispatcher renderer;
    private static VertexConsumer consumer;
    private static double camX, camY, camZ;
    private static int light;
    private static float alpha;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (MC.level == null || StrategicViewClientEvents.isStrategicView())
            return;

        long now = MC.level.getGameTime();
        float partial = evt.getPartialTick();
        Vec3 cam = evt.getCamera().getPosition();
        camX = cam.x;
        camY = cam.y;
        camZ = cam.z;

        pickedCount = 0;
        List<BuildingPlacement> buildings = BuildingClientEvents.getBuildings();
        for (int i = 0; i < buildings.size(); i++) {
            BuildingPlacement p = buildings.get(i);
            Building building = p.getBuilding();
            if (building == null || building instanceof AbstractBridge)
                continue;   // a scaffold round a bridge span just looks like a fence across the river
            State s = states.get(p);
            float a;
            if (!p.isBuilt) {
                if (s == null) {
                    s = new State();
                    states.put(p, s);
                }
                s.seenUnbuilt = true;
                s.builtAt = -1;
                a = 1f;
            } else {
                if (s == null || !s.seenUnbuilt)
                    continue;
                if (s.builtAt < 0)
                    s.builtAt = now;
                float age = (now - s.builtAt) + partial;
                if (age >= FADE_TICKS) {
                    s.seenUnbuilt = false;
                    continue;
                }
                a = 1f - age / FADE_TICKS;
            }
            double dx = p.centrePos.getX() + 0.5 - camX;
            double dy = p.centrePos.getY() + 0.5 - camY;
            double dz = p.centrePos.getZ() + 0.5 - camZ;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > MAX_RENDER_DIST_SQR)
                continue;
            if (pickedCount == MAX_SCAFFOLDS && d2 >= pickedDist[MAX_SCAFFOLDS - 1])
                continue;
            // a scaffold gives away construction progress, so only draw sites in sight (cached per site)
            if (now - s.visCheckedAt >= VIS_RECHECK_TICKS || now < s.visCheckedAt) {
                s.visible = FogOfWarClientEvents.isInBrightChunk(p.centrePos);
                s.visCheckedAt = now;
            }
            if (!s.visible)
                continue;
            insert(p, d2, a);
        }
        if (pickedCount == 0)
            return;

        PoseStack matrix = evt.getPoseStack();
        renderer = MC.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        // both are fixed buffers in RenderBuffers, so holding the two consumers at once is safe
        VertexConsumer solid = buffers.getBuffer(Sheets.cutoutBlockSheet());
        VertexConsumer fading = null;

        matrix.pushPose();
        pose = matrix.last();
        basePose.set(pose.pose());
        baseNormal.set(pose.normal());
        for (int i = 0; i < pickedCount; i++) {
            alpha = pickedAlpha[i];
            if (alpha < 1f) {
                if (fading == null)
                    fading = buffers.getBuffer(Sheets.translucentCullBlockSheet());
                consumer = fading;
            } else {
                consumer = solid;
            }
            drawScaffold(picked[i], pickedDist[i] > LOD_DIST_SQR);
            picked[i] = null;   // don't pin placements past their removal
        }
        matrix.popPose();
        pose = null;
        consumer = null;

        buffers.endBatch(Sheets.cutoutBlockSheet());
        if (fading != null)
            buffers.endBatch(Sheets.translucentCullBlockSheet());
    }

    /** Insertion into the distance-sorted pick list, dropping the furthest when full. */
    private static void insert(BuildingPlacement p, double d2, float a) {
        int i = pickedCount < MAX_SCAFFOLDS ? pickedCount++ : MAX_SCAFFOLDS - 1;
        while (i > 0 && pickedDist[i - 1] > d2) {
            picked[i] = picked[i - 1];
            pickedDist[i] = pickedDist[i - 1];
            pickedAlpha[i] = pickedAlpha[i - 1];
            i--;
        }
        picked[i] = p;
        pickedDist[i] = d2;
        pickedAlpha[i] = a;
    }

    private static void drawScaffold(BuildingPlacement p, boolean far) {
        int minY = p.minCorner.getY();
        int levels = p.maxCorner.getY() - minY + 1;
        float pct;
        if (p.isBuilt) {
            pct = 1f;
        } else {
            int total = p.getBlocksTotal();
            pct = total > 0 ? Mth.clamp((float) p.getBlocksPlaced() / total, 0f, 1f) : 0f;
        }
        // RoN lays blocks bottom-up, so placed% maps to the height of the work face; the scaffold stands one level
        // above it (that's where the builders are working)
        int builtLevels = Math.max(1, Mth.ceil(pct * levels));
        int railLevels = Math.min(levels, builtLevels + 1);
        float top = minY + railLevels;

        double x0 = p.minCorner.getX() - GAP, x1 = p.maxCorner.getX() + 1 + GAP;
        double z0 = p.minCorner.getZ() - GAP, z1 = p.maxCorner.getZ() + 1 + GAP;
        float lenX = (float) (x1 - x0), lenZ = (float) (z1 - z0);

        // materials and corner dressing per faction (FactionTraits); unknown factions get a plain spruce frame
        FactionTraits traits = FactionTraits.of(p.getFaction());
        BlockState pole = traits.scaffoldPole, rail = traits.scaffoldRail;
        FactionTraits.ScaffoldDecor decor = traits.scaffoldDecor;

        lightPos.set(p.centrePos.getX(), Math.min((int) top + 1, p.maxCorner.getY() + 1), p.centrePos.getZ());
        light = LevelRenderer.getLightColor(MC.level, lightPos);

        // poles: corners plus every ~POLE_SPACING blocks along each side (corners only when far)
        float poleH = top - minY + 0.3f;   // sunk 0.3 into the ground so uneven terrain doesn't show a gap
        int nX = far ? 1 : Math.max(1, Math.round(lenX / POLE_SPACING));
        int nZ = far ? 1 : Math.max(1, Math.round(lenZ / POLE_SPACING));
        for (int i = 0; i <= nX; i++) {
            double x = x0 + lenX * i / nX;
            box(pole, x - POLE / 2, minY - 0.3, z0 - POLE / 2, POLE, poleH, POLE);
            box(pole, x - POLE / 2, minY - 0.3, z1 - POLE / 2, POLE, poleH, POLE);
        }
        for (int j = 1; j < nZ; j++) {
            double z = z0 + lenZ * j / nZ;
            box(pole, x0 - POLE / 2, minY - 0.3, z - POLE / 2, POLE, poleH, POLE);
            box(pole, x1 - POLE / 2, minY - 0.3, z - POLE / 2, POLE, poleH, POLE);
        }

        // rails: one ring per level up to the top (every other level when far)
        for (int k = 1; k <= railLevels; k += far ? 2 : 1) {
            double y = minY + k - RAIL;
            box(rail, x0 - RAIL / 2, y, z0 - RAIL / 2, lenX + RAIL, RAIL, RAIL);
            box(rail, x0 - RAIL / 2, y, z1 - RAIL / 2, lenX + RAIL, RAIL, RAIL);
            box(rail, x0 - RAIL / 2, y, z0 + RAIL / 2, RAIL, RAIL, lenZ - RAIL);
            box(rail, x1 - RAIL / 2, y, z0 + RAIL / 2, RAIL, RAIL, lenZ - RAIL);
            if (decor == FactionTraits.ScaffoldDecor.BONE_LANTERNS && !far) {
                // Gravebound: bone knuckles lash the rails to the corner poles
                corner(Blocks.BONE_BLOCK.defaultBlockState(), x0, y, z0, 0.26f);
                corner(Blocks.BONE_BLOCK.defaultBlockState(), x1, y, z0, 0.26f);
                corner(Blocks.BONE_BLOCK.defaultBlockState(), x0, y, z1, 0.26f);
                corner(Blocks.BONE_BLOCK.defaultBlockState(), x1, y, z1, 0.26f);
            }
        }

        // corner dressing, each faction its own
        for (int c = 0; c < 4; c++) {
            boolean hiX = (c & 1) != 0, hiZ = (c & 2) != 0;
            double cx = hiX ? x1 : x0, cz = hiZ ? z1 : z0;
            float ox = hiX ? 1f : -1f, oz = hiZ ? 1f : -1f;
            switch (decor) {
                case PENNANTS -> {
                    // Sunforged: a short white pennant off each corner pole's head, flying outward
                    box(pole, cx - POLE / 2, top, cz - POLE / 2, POLE, 0.7f, POLE);
                    double fx = hiX ? cx + POLE / 2 : cx - POLE / 2 - 0.55;
                    box(Blocks.WHITE_WOOL.defaultBlockState(), fx, top - 0.05, cz - 0.02, 0.55f, 0.7f, 0.04f);
                }
                // Gravebound: a soul lantern sits on each corner pole (the model is the lantern; the block is air round it)
                case BONE_LANTERNS -> box(Blocks.SOUL_LANTERN.defaultBlockState(), cx - 0.5, top, cz - 0.5, 1f, 1f, 1f);
                case CHAINS -> {
                    // Horde: two links of chain hang just outside each corner, swinging from the top rail
                    double hx = cx + ox * 0.35 - 0.5, hz = cz + oz * 0.35 - 0.5;
                    box(Blocks.CHAIN.defaultBlockState(), hx, top - 1.1, hz, 1f, 1f, 1f);
                    if (railLevels > 1)
                        box(Blocks.CHAIN.defaultBlockState(), hx, top - 2.1, hz, 1f, 1f, 1f);
                }
                // Verdant: a plain lantern hangs on each corner pole of the living-wood frame
                case LANTERNS -> box(Blocks.LANTERN.defaultBlockState(), cx - 0.5, top, cz - 0.5, 1f, 1f, 1f);
                default -> { }
            }
        }
    }

    private static void corner(BlockState state, double cx, double y, double cz, float size) {
        box(state, cx - size / 2, y - (size - RAIL) / 2, cz - size / 2, size, size, size);
    }

    /**
     * Draws a unit block model stretched to (sx, sy, sz) at world (x, y, z). Rewrites the shared pose in place (same
     * maths as PoseStack.translate + scale) instead of pushPose, which would allocate two matrices per box.
     */
    private static void box(BlockState state, double x, double y, double z, float sx, float sy, float sz) {
        pose.pose().set(basePose)
            .translate((float) (x - camX), (float) (y - camY), (float) (z - camZ))
            .scale(sx, sy, sz);
        float fx = 1f / sx, fy = 1f / sy, fz = 1f / sz;
        float inv = Mth.fastInvCubeRoot(fx * fy * fz);
        pose.normal().set(baseNormal).scale(inv * fx, inv * fy, inv * fz);

        BakedModel model = renderer.getBlockModel(state);
        for (Direction d : DIRECTIONS) {
            RANDOM.setSeed(42L);
            quads(model.getQuads(state, d, RANDOM, ModelData.EMPTY, null));
        }
        RANDOM.setSeed(42L);
        quads(model.getQuads(state, null, RANDOM, ModelData.EMPTY, null));
    }

    private static void quads(List<BakedQuad> list) {
        for (int q = 0, n = list.size(); q < n; q++)
            consumer.putBulkData(pose, list.get(q), 1f, 1f, 1f, alpha, light, OverlayTexture.NO_OVERLAY, false);
    }
}
