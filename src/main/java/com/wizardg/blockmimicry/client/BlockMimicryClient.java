package com.wizardg.blockmimicry.client;

import com.wizardg.blockmimicry.BlockMimicry;

import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = BlockMimicry.MODID, value = Dist.CLIENT)
public class BlockMimicryClient {
    // Tint layers forwarded to the copied block
    private static final int TINT_LAYERS = 32; // vanilla uses 0-1, generous for modded layered models

    @SubscribeEvent
    static void wrapModels(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        for (BlockState state : BlockMimicry.MIMIC_BLOCK.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(state, (s, base) -> new MimicModel(base));
        }
    }

    @SubscribeEvent
    static void registerTints(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(IntStream.range(0, TINT_LAYERS).<net.minecraft.client.color.block.BlockTintSource>mapToObj(MimicTint::new).toList(),
                BlockMimicry.MIMIC_BLOCK.get());
    }
}
