package com.wizardg.blockmimicry.gametest;

import com.mojang.logging.LogUtils;
import com.wizardg.blockmimicry.BlockMimicry;
import com.wizardg.blockmimicry.block.MimicBlock;
import com.wizardg.blockmimicry.block.entity.MimicBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Copies every registered block into a mimic block and runs every hook the mimic forwards to the copied block,
 * collecting crashes instead of stopping at the first. Each failure is logged with its stack trace.
 * Not covered: client rendering (model, tint, face culling) since that needs a real client.
 */
@EventBusSubscriber(modid = BlockMimicry.MODID)
public class MimicCrashTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Blocks with huge state counts are sampled evenly down to this many states
    private static final int MAX_STATES_PER_BLOCK = 64;

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, BlockMimicry.MODID);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ALL_BLOCKS =
            FUNCTIONS.register("all_blocks", () -> MimicCrashTest::allBlocks);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BlockMimicry.MODID, path);
    }

    // Dev environments only (runGameTestServer, runClient): never registered in a player's installed mod
    public static void registerFunctions(IEventBus modBus) {
        if (GameTestHooks.isGametestEnabled()) FUNCTIONS.register(modBus);
    }

    @SubscribeEvent
    static void register(RegisterGameTestsEvent event) {
        if (!GameTestHooks.isGametestEnabled()) return;
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(id("default"));
        event.registerTest(id("all_blocks"), new FunctionGameTestInstance(ALL_BLOCKS.getKey(),
                new TestData<>(env, id("empty"), 100, 0, true)));
    }

    private static void allBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos partner = pos.east();   // a second mimic copying the same block: fence links, door halves (above is pos.above())
        BlockPos changing = pos.north(); // a neighbor that changes, so the copy reshapes
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5); // standing on the mimic
        BlockState mimicBlock = BlockMimicry.MIMIC_BLOCK.get().defaultBlockState();

        List<String> failures = new ArrayList<>();
        int copied = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof MimicBlock) continue;
            String stage = "setup";
            try {
                level.setBlock(pos, mimicBlock, Block.UPDATE_ALL);
                level.setBlock(partner, mimicBlock, Block.UPDATE_ALL);
                level.setBlock(pos.above(), mimicBlock, Block.UPDATE_ALL);
                level.setBlock(changing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                MimicBlockEntity be = (MimicBlockEntity) level.getBlockEntity(pos);

                if (block.asItem() instanceof BlockItem item) {
                    stage = "copy (right-click)";
                    for (BlockPos p : List.of(pos, partner, pos.above())) {
                        ItemStack stack = new ItemStack(item);
                        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false);
                        MimicBlock.tryCopy(level, p, player, stack, new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
                    }
                    if (be.getMimic() != null) {
                        copied++;
                        stage = "hooks";
                        exercise(level, pos, player);
                        stage = "neighbour change";
                        level.setBlock(changing, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                        level.setBlock(changing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        exercise(level, pos, player);
                    }
                }

                List<BlockState> states = block.getStateDefinition().getPossibleStates();
                int step = Math.max(1, states.size() / MAX_STATES_PER_BLOCK);
                for (int i = 0; i < states.size(); i += step) {
                    BlockState s = states.get(i);
                    if (!MimicBlock.canMimic(s)) continue;
                    stage = "state " + s;
                    be.setMimic(s);
                    exercise(level, pos, player);
                }

                stage = "clear";
                be.setMimic(null);
            } catch (Throwable t) {
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                failures.add(id + " [" + stage + "]: " + t);
                LOGGER.error("Mimic crash test: {} failed during {}", id, stage, t);
            }
        }
        for (BlockPos p : List.of(pos, partner, pos.above(), changing)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        LOGGER.info("Mimic crash test: {} blocks copied via right-click, {} failures", copied, failures.size());
        if (failures.isEmpty()) helper.succeed();
        else helper.fail(failures.size() + " blocks crash as a mimic (full traces in the log): "
                + String.join(" | ", failures.subList(0, Math.min(5, failures.size()))));
    }

    // Every hook MimicBlock forwards to the copied block, through the public BlockState API the game uses
    private static void exercise(ServerLevel level, BlockPos pos, Player player) {
        BlockState s = level.getBlockState(pos);
        CollisionContext ctx = CollisionContext.of(player);
        s.getShape(level, pos, ctx);
        s.getCollisionShape(level, pos, ctx);
        s.getVisualShape(level, pos, ctx);
        s.getBlockSupportShape(level, pos);
        s.getSoundType(level, pos, player);
        s.getFriction(level, pos, player);
        s.isLadder(level, pos, player);
        s.getLightEmission(level, pos);
        s.isRedstoneConductor(level, pos);
        s.isSuffocating(level, pos);
        s.isViewBlocking(level, pos);
        s.getMapColor(level, pos);
        s.getDestroyProgress(player, level, pos);
        for (Direction d : Direction.values()) {
            s.getSignal(level, pos, d);
            s.getDirectSignal(level, pos, d);
            s.canRedstoneConnectTo(level, pos, d);
            s.isFaceSturdy(level, pos, d);
            s.getAppearance(level, pos, d, null, null);
        }
        s.getBlock().updateEntityMovementAfterFallOn(level, player);
        s.getExplosionResistance(level, pos, new ServerExplosion(level, null, null, null, Vec3.atCenterOf(pos), 1, false,
                Explosion.BlockInteraction.KEEP));
        // Walk speed and jump height go through EntityMixin: move the player standing on the mimic, then jump.
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.move(MoverType.SELF, new Vec3(0.01, -0.01, 0));
        player.jumpFromGround();
        player.setDeltaMovement(Vec3.ZERO);
    }
}
