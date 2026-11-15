package com.wizardg.blockmimicry.client;

import com.wizardg.blockmimicry.block.MimicBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record MimicTint(int index) implements BlockTintSource {
    private static final int WHITE = -1;

    @Override
    public int color(BlockState state) {
        return WHITE;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        BlockState m = MimicBlock.renderMimic(level, pos);
        if (m == null) return WHITE;
        BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(m, index);
        return source != null ? source.colorInWorld(m, level, pos) : WHITE;
    }
}
