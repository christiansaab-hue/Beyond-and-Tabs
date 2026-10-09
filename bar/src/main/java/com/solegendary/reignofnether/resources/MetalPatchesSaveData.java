package com.solegendary.reignofnether.resources;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/** Persists the metal patch centres stamped at match start, so they survive saving and reloading the world. */
public class MetalPatchesSaveData extends SavedData {

    /** dimension id -> patch centres */
    public final List<Entry> entries = new ArrayList<>();

    public record Entry(String dimension, BlockPos pos) { }

    private static MetalPatchesSaveData create() {
        return new MetalPatchesSaveData();
    }

    @Nonnull
    public static MetalPatchesSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return create();
        }
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(MetalPatchesSaveData::load, MetalPatchesSaveData::create, "saved-metal-patches");
    }

    public static MetalPatchesSaveData load(CompoundTag tag) {
        MetalPatchesSaveData data = create();
        ListTag ltag = (ListTag) tag.get("patches");
        if (ltag != null) {
            for (Tag ctag : ltag) {
                CompoundTag ptag = (CompoundTag) ctag;
                data.entries.add(new Entry(ptag.getString("dim"),
                    new BlockPos(ptag.getInt("x"), ptag.getInt("y"), ptag.getInt("z"))));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag ltag = new ListTag();
        for (Entry entry : entries) {
            CompoundTag ptag = new CompoundTag();
            ptag.putString("dim", entry.dimension());
            ptag.putInt("x", entry.pos().getX());
            ptag.putInt("y", entry.pos().getY());
            ptag.putInt("z", entry.pos().getZ());
            ltag.add(ptag);
        }
        tag.put("patches", ltag);
        return tag;
    }
}
