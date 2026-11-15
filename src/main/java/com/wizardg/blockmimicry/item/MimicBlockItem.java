package com.wizardg.blockmimicry.item;

import com.wizardg.blockmimicry.block.MimicBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

public class MimicBlockItem extends BlockItem {
    public MimicBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        InteractionResult result = super.place(ctx);
        Player player = ctx.getPlayer();
        if (result.consumesAction() && player != null) {
            InteractionHand other = ctx.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack copied = player.getItemInHand(other);
            BlockPos pos = ctx.getClickedPos();
            BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), pos, false);
            BlockPlaceContext copyCtx = new BlockPlaceContext(ctx.getLevel(), player, other, copied, hit) {
                @Override
                public BlockPos getClickedPos() {
                    return pos;
                }
            };
            MimicBlock.tryCopy(ctx.getLevel(), pos, player, copied, copyCtx);
        }
        return result;
    }
}
