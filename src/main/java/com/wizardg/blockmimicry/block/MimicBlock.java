package com.wizardg.blockmimicry.block;

import com.wizardg.blockmimicry.block.entity.MimicBlockEntity;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockAndLightGetter;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import org.jspecify.annotations.Nullable;

/**
 * A block that copies another block: right-click it with a block to copy that block (consumes the block),
 * sneak right-click with an empty hand to take it back. The block also gets returned whenever the block is broken
 */
public class MimicBlock extends BaseEntityBlock {
    public static final MapCodec<MimicBlock> CODEC = simpleCodec(MimicBlock::new);
    // Occlusion, and light blocking are cached per block state, not per position, so they have to come from a state property
    public static final BooleanProperty SOLID = BooleanProperty.create("solid");

    public MimicBlock(Properties props) {
        super(props.dynamicShape()
                .isRedstoneConductor((s, level, pos) -> {
                    BlockState m = mimic(level, pos);
                    return m != null && m.isRedstoneConductor(level, pos);
                })
                .isSuffocating((s, level, pos) -> {
                    BlockState m = mimic(level, pos);
                    return m != null && m.isSuffocating(level, pos);
                })
                .isViewBlocking((s, level, pos) -> {
                    BlockState m = mimic(level, pos);
                    return m != null && m.isViewBlocking(level, pos);
                }));
        registerDefaultState(stateDefinition.any().setValue(SOLID, false));
    }

    // The copied state at a position, or null
    public static @Nullable BlockState mimic(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MimicBlockEntity be ? be.getMimic() : null;
    }

    // What a neighbor looks like to a copied block
    private static BlockState appearance(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof MimicBlock) {
            BlockState m = mimic(level, pos);
            if (m != null) return m;
        }
        return state;
    }

    // Client meshing threads must read model data rather than the block entity
    public static @Nullable BlockState renderMimic(BlockGetter level, BlockPos pos) {
        return level.getModelData(pos).get(MimicBlockEntity.MIMIC);
    }

    public static BlockState withSolid(BlockState self, @Nullable BlockState m) {
        return self.setValue(SOLID, m != null && m.isSolidRender());
    }

    // Blocks with their own block entity can't be copied
    public static boolean canMimic(BlockState m) {
        return !m.isAir() && !(m.getBlock() instanceof MimicBlock) && !m.hasBlockEntity()
                && m.getRenderShape() == RenderShape.MODEL;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOLID);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MimicBlockEntity(pos, state);
    }

    // --------- setting / clearing

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return tryCopy(level, pos, player, stack, new BlockPlaceContext(player, hand, stack, hit))
                ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    public static boolean tryCopy(Level level, BlockPos pos, Player player, ItemStack stack, BlockPlaceContext ctx) {
        if (!(level.getBlockEntity(pos) instanceof MimicBlockEntity be) || be.getMimic() != null
                || !(stack.getItem() instanceof BlockItem item)) return false;
        BlockState m = item.getBlock().getStateForPlacement(ctx);
        // Only the look matters, so a block that couldn't be placed there for real uses its default
        if (m == null) m = item.getBlock().defaultBlockState();
        if (!canMimic(m)) return false;
        if (m.hasProperty(BlockStateProperties.WATERLOGGED)) m = m.setValue(BlockStateProperties.WATERLOGGED, false);
        // Placement context computes connections for the spot beside us. Something that couldn't survive here keeps its placement shape.
        BlockState shaped = m;
        for (Direction d : Direction.values()) {
            BlockPos np = pos.relative(d);
            shaped = shaped.updateShape(level, level, pos, d, np, appearance(level, np, level.getBlockState(np)), level.getRandom());
        }
        if (!shaped.isAir()) m = shaped;
        if (!level.isClientSide()) {
            m = pairTallHalves(m, level, pos);
            be.setMimic(m);
            SoundType sound = m.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
            stack.consume(1, player);
        }
        return true;
    }

    private static BlockState pairTallHalves(BlockState m, Level level, BlockPos pos) {
        if (!m.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return m;
        if (level.getBlockEntity(pos.below()) instanceof MimicBlockEntity below && below.getMimic() != null
                && below.getMimic().is(m.getBlock()) && !isPaired(level, pos.below(), below.getMimic())) {
            BlockState lower = below.getMimic().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
            if (lower != below.getMimic()) below.setMimic(lower);
            return lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        }
        if (level.getBlockEntity(pos.above()) instanceof MimicBlockEntity above && above.getMimic() != null
                && above.getMimic().is(m.getBlock()) && !isPaired(level, pos.above(), above.getMimic())) {
            BlockState upper = above.getMimic().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            if (upper != above.getMimic()) above.setMimic(upper);
            return upper.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        }
        return m.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
    }

    private static boolean isPaired(Level level, BlockPos pos, BlockState half) {
        boolean lower = half.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
        BlockState other = mimic(level, lower ? pos.above() : pos.below());
        return other != null && other.is(half.getBlock())
                && other.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == (lower ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }

    // Scheduled by MimicBlockEntity.onLoad when the saved solid flag doesn't match the copied block
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState fixed = withSolid(state, mimic(level, pos));
        if (fixed != state) level.setBlock(pos, fixed, Block.UPDATE_ALL);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()
                || !(level.getBlockEntity(pos) instanceof MimicBlockEntity be) || be.getMimic() == null) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            BlockState old = be.getMimic();
            be.setMimic(null);
            if (!player.hasInfiniteMaterials()) player.getInventory().placeItemBackInInventory(new ItemStack(old.getBlock()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MimicBlockEntity be && be.getMimic() != null) {
            BlockState reshaped = be.getMimic().updateShape(level, ticks, pos, dir, neighborPos, appearance(level, neighborPos, neighborState), random);
            // A copied block that would break here just keeps its last shape
            if (!reshaped.isAir() && reshaped != be.getMimic()) {
                BlockState next = withSolid(state, reshaped);
                be.reshapeMimic(reshaped, next);
                return next;
            }
        }
        return state;
    }

    // --------- shape

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getShape(level, pos, ctx) : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getCollisionShape(level, pos, ctx) : Shapes.block();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getVisualShape(level, pos, ctx) : Shapes.block();
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getBlockSupportShape(level, pos) : Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return state.getValue(SOLID) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return !state.getValue(SOLID);
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState, Direction dir) {
        // Hide a neighbor's face exactly when the copied block would
        BlockState m = renderMimic(level, pos);
        if (m == null) return false;
        BlockPos neighborPos = pos.relative(dir);
        // Two mimics both copying glass should merge like real glass does
        BlockState neighbor = neighborState.getBlock() instanceof MimicBlock ? renderMimic(level, neighborPos) : neighborState;
        return neighbor != null && !Block.shouldRenderFace(level, neighborPos, neighbor, m, dir.getOpposite());
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndLightGetter level, BlockPos pos, Direction side,
                                    @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        // Block entity is safe to read from the server, while the client gets model data only
        BlockState m = level instanceof Level l && !l.isClientSide() ? mimic(level, pos) : renderMimic(level, pos);
        return m != null ? m : state;
    }

    // --------- feel

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getSoundType(level, pos, entity) : super.getSoundType(state, level, pos, entity);
    }

    @Override
    public float getFriction(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getFriction(level, pos, entity) : super.getFriction(state, level, pos, entity);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        BlockState m = mimic(level, pos);
        return m != null && m.isLadder(level, pos, entity);
    }

    // stepOn / fallOn / entityInside are deliberately NOT forwarded. The copied block's versions run at our position,
    // and many re-read or replace "themselves" there. Tripwire and pressure plates read their own properties,
    // farmland tramples to dirt, turtle eggs break, redstone ore lights up, and cauldrons drain. Each would overwrite the
    // mimic block with a real one and lose the stored block. The copy is about looks, while keeping the plain-block behavior.

    @Override
    public void updateEntityMovementAfterFallOn(BlockGetter level, Entity entity) {
        // Slime bounce / bed bounce, Entity.move asks the block at getOnPosLegacy()
        BlockState m = mimic(level, entity.getOnPosLegacy());
        if (m != null) m.getBlock().updateEntityMovementAfterFallOn(level, entity);
        else super.updateEntityMovementAfterFallOn(level, entity);
    }

    // --------- light and redstone

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        AuxiliaryLightManager aux = level.getAuxLightManager(pos);
        return aux != null ? aux.getLightAt(pos) : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true; // getSignal answers 0 unless the copied block is a power source
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        BlockState m = mimic(level, pos);
        return m != null && m.canRedstoneConnectTo(level, pos, direction);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getSignal(level, pos, dir) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getDirectSignal(level, pos, dir) : 0;
    }

    // --------- mining

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getDestroyProgress(player, level, pos) : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getExplosionResistance(level, pos, explosion) : super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    public MapColor getMapColor(BlockState state, BlockGetter level, BlockPos pos, MapColor defaultColor) {
        BlockState m = mimic(level, pos);
        return m != null ? m.getMapColor(level, pos) : defaultColor;
    }
}
