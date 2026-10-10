package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.registrars.BlockEntityRegistrar;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * The owner of a {@link VineSnareBlock}. The name is saved with the chunk and synced to clients (so the owner and
 * allies can draw the snare). Live server-side snares are kept in {@link #LIVE} - a handful per player - so the
 * per-player cap (PlantVineSnare.MAX_PER_PLAYER) and the bot can count them without scanning the world.
 */
public class VineSnareBlockEntity extends BlockEntity {

    static final Set<VineSnareBlockEntity> LIVE = new HashSet<>();

    private String owner = "";

    public VineSnareBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistrar.VINE_SNARE_BLOCK_ENTITY.get(), pos, state);
    }

    public String getOwner() { return owner; }

    public void setOwner(String owner) {
        this.owner = owner == null ? "" : owner;
        setChanged();
        if (level != null && !level.isClientSide()) {
            LIVE.add(this);   // now, not at onLoad (Forge may run that a tick later): the cap must see it at once
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Live snares owned by this player in this level (server side). */
    public static int countOwned(Level level, String owner) {
        int n = 0;
        for (VineSnareBlockEntity be : LIVE)
            if (be.level == level && owner.equals(be.owner) && !be.isRemoved())
                n++;
        return n;
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
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        owner = tag.getString("owner");
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // a one-block box, so the renderer is culled like any small block (the default for a shapeless block is "always")
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition, worldPosition.offset(1, 1, 1));
    }
}
