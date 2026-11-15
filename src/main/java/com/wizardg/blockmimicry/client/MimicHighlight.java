package com.wizardg.blockmimicry.client;

import com.wizardg.blockmimicry.BlockMimicry;
import com.wizardg.blockmimicry.block.entity.MimicBlockEntity;
import com.wizardg.blockmimicry.item.MimicBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = BlockMimicry.MODID, value = Dist.CLIENT)
public final class MimicHighlight {
    private static volatile boolean active; // read by chunk meshing threads

    private MimicHighlight() {}

    public static boolean active() {
        return active;
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        boolean holding = player != null && (player.getMainHandItem().getItem() instanceof MimicBlockItem
                || player.getOffhandItem().getItem() instanceof MimicBlockItem);
        if (holding == active) return;

        active = holding;
        ClientLevel level = mc.level;

        if (level == null || player == null) return;

        ChunkPos center = player.chunkPosition();
        int radius = mc.options.getEffectiveRenderDistance() + 1;
        for (int cx = center.x() - radius; cx <= center.x() + radius; cx++) {
            for (int cz = center.z() - radius; cz <= center.z() + radius; cz++) {
                if (!level.hasChunk(cx, cz)) continue;

                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof MimicBlockEntity mimic && mimic.getMimic() != null) {
                        level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
