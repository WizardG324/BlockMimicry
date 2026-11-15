package com.wizardg.blockmimicry.network;

import com.wizardg.blockmimicry.BlockMimicry;
import com.wizardg.blockmimicry.block.entity.MimicBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = BlockMimicry.MODID)
public record MimicSyncPayload(BlockPos pos, BlockState state, Optional<BlockState> mimic) implements CustomPacketPayload {
    public static final Type<MimicSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BlockMimicry.MODID, "mimic_sync"));
    private static final StreamCodec<RegistryFriendlyByteBuf, BlockState> STATE = ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY).cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, MimicSyncPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MimicSyncPayload::pos,
            STATE, MimicSyncPayload::state,
            ByteBufCodecs.optional(STATE), MimicSyncPayload::mimic,
            MimicSyncPayload::new);

    @Override
    public Type<MimicSyncPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, MimicSyncPayload::handle);
    }

    // Client - main thread
    private static void handle(MimicSyncPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        if (!(level.getBlockEntity(payload.pos()) instanceof MimicBlockEntity be)) return;
        be.applySync(payload.mimic().orElse(null));
        level.setBlock(payload.pos(), payload.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        // Rebuild even when only the copied block changed
        level.sendBlockUpdated(payload.pos(), payload.state(), payload.state(), Block.UPDATE_ALL_IMMEDIATE);
    }
}
