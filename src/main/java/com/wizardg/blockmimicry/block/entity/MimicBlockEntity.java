package com.wizardg.blockmimicry.block.entity;

import com.wizardg.blockmimicry.BlockMimicry;
import com.wizardg.blockmimicry.block.MimicBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import com.wizardg.blockmimicry.network.MimicSyncPayload;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

/** Holds the block state being copied. Null = not copying anything. */
public class MimicBlockEntity extends BlockEntity {
    // The copied state for the client renderer
    public static final ModelProperty<BlockState> MIMIC = new ModelProperty<>();

    private volatile @Nullable BlockState mimic;

    public MimicBlockEntity(BlockPos pos, BlockState state) {
        super(BlockMimicry.MIMIC_BLOCK_ENTITY.get(), pos, state);
    }

    public @Nullable BlockState getMimic() {
        return mimic;
    }

    // Change and push the copied block on the server side
    public void setMimic(@Nullable BlockState newMimic) {
        BlockState oldMimic = mimic;
        mimic = newMimic;
        setChanged();
        if (level == null) return;
        BlockState state = getBlockState();
        BlockState next = MimicBlock.withSolid(state, newMimic);

        if (level instanceof ServerLevel server) {
            PacketDistributor.sendToPlayersTrackingChunk(server, ChunkPos.containing(worldPosition),
                    new MimicSyncPayload(worldPosition, next, Optional.ofNullable(newMimic)));
        }
        if (next != state) {
            level.setBlock(worldPosition, next, Block.UPDATE_ALL); // also updates neighbors and their shapes
        } else {
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            level.updateNeighborsAt(worldPosition, next.getBlock());
            next.updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
        }

        if ((oldMimic != null && oldMimic.isSignalSource()) || (newMimic != null && newMimic.isSignalSource())) {
            for (Direction d : Direction.values()) level.updateNeighborsAt(worldPosition.relative(d), next.getBlock());
        }
        applyLight(true);
    }

    public void applySync(@Nullable BlockState newMimic) {
        mimic = newMimic;
        requestModelDataUpdate();
        applyLight(true);
    }

    public void reshapeMimic(BlockState reshaped, BlockState nextState) {
        mimic = reshaped;
        if (level instanceof ServerLevel server) {
            setChanged();
            PacketDistributor.sendToPlayersTrackingChunk(server, ChunkPos.containing(worldPosition),
                    new MimicSyncPayload(worldPosition, nextState, Optional.of(reshaped)));
        } else {
            requestModelDataUpdate();
        }
    }

    private void applyLight(boolean recheck) {
        if (level == null) return;
        AuxiliaryLightManager aux = level.getAuxLightManager(worldPosition);
        if (aux != null) aux.setLightAt(worldPosition, mimic == null ? 0 : mimic.getLightEmission());
        if (recheck) level.getLightEngine().checkBlock(worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        applyLight(false);

        if (level instanceof ServerLevel && MimicBlock.withSolid(getBlockState(), mimic) != getBlockState()) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && mimic != null) Block.popResource(level, pos, new ItemStack(mimic.getBlock()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("mimic", BlockState.CODEC, mimic);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        BlockState old = mimic;
        mimic = input.read("mimic", BlockState.CODEC).orElse(null);
        if (level != null && level.isClientSide() && old != mimic) {
            applySync(mimic);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public ModelData getModelData() {
        BlockState m = mimic;
        return m == null ? ModelData.EMPTY : ModelData.of(MIMIC, m);
    }
}
