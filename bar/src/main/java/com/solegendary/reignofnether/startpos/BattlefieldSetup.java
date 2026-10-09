package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Forms the battlefield for matches that start directly (Quick Battle, /bot add) rather than through the readied
 * start-position countdown. Each starting player or bot registers its base position here; a few seconds after the
 * last arrival the battlefield forms once: bases and lanes cleared, metal patches stamped, and the great boundary
 * wall raised around everyone. Anyone who joins an already-formed battlefield gets their base cleared and their
 * patches stamped inside the existing arena instead.
 */
public class BattlefieldSetup {

    /** Ticks to wait after the last arrival before forming the battlefield (lets Quick Battle's bot join too). */
    static final int SETTLE_TICKS = 160;   // 8 seconds

    static final List<BlockPos> pending = new ArrayList<>();
    static int settleTicksLeft = -1;

    public static synchronized void onStart(ServerLevel level, BlockPos pos) {
        if (level == null)
            return;
        // an already-formed battlefield (this session or a reloaded save): slot the newcomer into the arena
        if (!MetalPatches.getPatches(level).isEmpty() && settleTicksLeft < 0 && pending.isEmpty()) {
            double cx = 0, cz = 0;
            List<BlockPos> patches = MetalPatches.getPatches(level);
            for (BlockPos p : patches) {
                cx += p.getX();
                cz += p.getZ();
            }
            cx /= patches.size();
            cz /= patches.size();
            StartAreaClearing.addLateBase(level, pos, (float) cx, (float) cz);
            ReignOfNether.LOGGER.info("[BattlefieldSetup] late base at [{}, {}] joined the arena", pos.getX(), pos.getZ());
            return;
        }
        pending.add(pos);
        settleTicksLeft = SETTLE_TICKS;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        pending.clear();
        settleTicksLeft = -1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || settleTicksLeft < 0 || evt.getServer() == null)
            return;
        if (--settleTicksLeft >= 0)
            return;
        List<BlockPos> positions;
        synchronized (BattlefieldSetup.class) {
            positions = new ArrayList<>(pending);
            pending.clear();
            settleTicksLeft = -1;
        }
        if (positions.isEmpty())
            return;
        ServerLevel level = evt.getServer().overworld();
        ReignOfNether.LOGGER.info("[BattlefieldSetup] forming the battlefield for {} bases", positions.size());
        StartAreaClearing.clearStartAreas(level, positions);
        MetalPatches.syncToClients(level);
        BattlefieldWall.build(level, positions);
    }
}
