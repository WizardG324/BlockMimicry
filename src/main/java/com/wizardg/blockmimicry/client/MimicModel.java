package com.wizardg.blockmimicry.client;

import com.wizardg.blockmimicry.block.MimicBlock;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

public class MimicModel extends DelegateBlockStateModel {
    public MimicModel(BlockStateModel base) {
        super(base);
    }

    private static BlockStateModel modelOf(BlockState mimic) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(mimic);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        BlockState m = MimicBlock.renderMimic(level, pos);
        if (m != null) modelOf(m).collectParts(level, pos, m, random, parts);
        else super.collectParts(level, pos, state, random, parts);
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState m = MimicBlock.renderMimic(level, pos);
        return m != null ? modelOf(m).particleMaterial(level, pos, m) : super.particleMaterial(level, pos, state);
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState m = MimicBlock.renderMimic(level, pos);
        return m != null ? modelOf(m).materialFlags(level, pos, m) : super.materialFlags(level, pos, state);
    }
}
