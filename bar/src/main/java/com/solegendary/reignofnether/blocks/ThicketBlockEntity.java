package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.registrars.BlockEntityRegistrar;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The owner (and cut damage) of a {@link ThicketBlock}. The owner is saved with the chunk; the server keeps every
 * live thicket in {@link #LIVE} so the per-player caps (PlantThicket / Overgrowth) and the bot can count them
 * without scanning the world. Never ticks. The owner is not synced: only the server decides cover, slow and cuts.
 */
public class ThicketBlockEntity extends BlockEntity {

    /** Thickets a player may plant with Seedshapers (3x3 patches): enough for a few ambush spots, an 8v8 perf bound. */
    public static final int MAX_PLANTED = 40;
    /** Hard cap including the Grove Warden's Overgrowth, which may grow past the planting cap but never past this. */
    public static final int MAX_TOTAL = 100;

    static final Set<ThicketBlockEntity> LIVE = new HashSet<>();

    private String owner = "";
    int hits = 0;
    long lastCutAt = Long.MIN_VALUE / 2;

    public ThicketBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistrar.THICKET_BLOCK_ENTITY.get(), pos, state);
    }

    public String getOwner() { return owner; }

    public int getHits() { return hits; }

    public void setOwner(String owner) {
        this.owner = owner == null ? "" : owner;
        setChanged();
        if (level != null && !level.isClientSide())
            LIVE.add(this);   // now, not at onLoad (Forge may run that a tick later): the cap must see it at once
    }

    /** Live thickets owned by this player in this level (server side). */
    public static int countOwned(Level level, String owner) {
        int n = 0;
        for (ThicketBlockEntity be : LIVE)
            if (be.level == level && owner.equals(be.owner) && !be.isRemoved())
                n++;
        return n;
    }

    /** Clears every thicket this player owns in this level (game test cleanup). */
    public static void removeAllOwned(Level level, String owner) {
        List<BlockPos> at = new ArrayList<>();
        for (ThicketBlockEntity be : LIVE)
            if (be.level == level && owner.equals(be.owner))
                at.add(be.worldPosition);
        for (BlockPos p : at)
            level.removeBlock(p, false);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide())
            LIVE.add(this);
    }

    @Override
    public void setRemoved() {
        LIVE.remove(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        LIVE.remove(this);
        super.onChunkUnloaded();
    }

    /** Server stop: drop every reference so a new world starts clean. */
    public static void clearAll() {
        LIVE.clear();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("owner", owner);
        tag.putInt("hits", hits);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        owner = tag.getString("owner");
        hits = tag.getInt("hits");
    }
}
