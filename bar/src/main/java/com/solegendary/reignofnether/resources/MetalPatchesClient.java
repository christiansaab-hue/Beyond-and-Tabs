package com.solegendary.reignofnether.resources;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Clientside copy of every metal patch centre on the map, synced from the server; read by the minimap. */
public final class MetalPatchesClient {
    private MetalPatchesClient() { }

    private static final List<BlockPos> patches = new CopyOnWriteArrayList<>();

    public static List<BlockPos> getPatches() {
        return patches;
    }

    public static void sync(List<BlockPos> newPatches) {
        patches.clear();
        patches.addAll(newPatches);
    }

    public static void clear() {
        patches.clear();
    }
}
