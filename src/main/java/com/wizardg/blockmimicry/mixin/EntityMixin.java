package com.wizardg.blockmimicry.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wizardg.blockmimicry.block.MimicBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Walking speed and jump height come from Block.getSpeedFactor() and Block.getJumpFactor(),
 * which have no position, so there is no block-side hook. Swap in the copied state where Entity looks them up.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @WrapOperation(method = {"getBlockSpeedFactor", "getBlockJumpFactor"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState blockmimicry$copiedState(Level level, BlockPos pos, Operation<BlockState> original) {
        BlockState state = original.call(level, pos);
        if (state.getBlock() instanceof MimicBlock) {
            BlockState m = MimicBlock.mimic(level, pos);
            if (m != null) return m;
        }
        return state;
    }
}
