package com.wizardg.blockmimicry.client;

import com.mojang.math.Transformation;
import com.wizardg.blockmimicry.block.MimicBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.QuadTransforms;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class MimicModel extends DelegateBlockStateModel {
    private static final float HIGHLIGHT_GROWTH = 1.004f;
    private final List<BlockStateModelPart> highlight;

    public MimicModel(BlockStateModel base) {
        super(base);
        this.highlight = enlarged(base);
    }

    private static BlockStateModel modelOf(BlockState mimic) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(mimic);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        BlockState m = MimicBlock.renderMimic(level, pos);
        if (m == null) {
            super.collectParts(level, pos, state, random, parts);
            return;
        }
        modelOf(m).collectParts(level, pos, m, random, parts);
        if (MimicHighlight.active()) parts.addAll(highlight);
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState m = MimicBlock.renderMimic(level, pos);
        return m != null ? modelOf(m).particleMaterial(level, pos, m) : super.particleMaterial(level, pos, state);
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState m = MimicBlock.renderMimic(level, pos);
        if (m == null) return super.materialFlags(level, pos, state);
        int flags = modelOf(m).materialFlags(level, pos, m);
        // The frame is cut out, tell the section builder about it too, or a solid copy wouldn't get a cutout layer.
        return MimicHighlight.active() ? flags | delegate.materialFlags() : flags;
    }

    private static List<BlockStateModelPart> enlarged(BlockStateModel base) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        base.collectParts(RandomSource.create(0L), parts);
        Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) {
                    for (int v = 0; v < 4; v++) {
                        min.min(quad.position(v));
                        max.max(quad.position(v));
                    }
                }
            }
        }
        if (min.x > max.x) return List.of(); // no geometry
        Vector3f center = new Vector3f(min).add(max).mul(0.5f);
        Transformation grow = new Transformation(new Matrix4f().translate(center).scale(HIGHLIGHT_GROWTH).translate(center.negate(new Vector3f())));
        return parts.stream().<BlockStateModelPart>map(part -> new Enlarged(part, grow)).toList();
    }

    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

    private static final class Enlarged implements BlockStateModelPart {
        private final BlockStateModelPart source;
        private final Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
        private final List<BakedQuad> unculled;

        Enlarged(BlockStateModelPart source, Transformation grow) {
            this.source = source;
            for (Direction d : Direction.values()) culled.put(d, transform(source.getQuads(d), grow));
            this.unculled = transform(source.getQuads(null), grow);
        }

        private static List<BakedQuad> transform(List<BakedQuad> quads, Transformation grow) {
            return quads.stream().map(q -> QuadTransforms.applyTransformation(q, grow)).toList();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction side) {
            return side == null ? unculled : culled.get(side);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return source.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return source.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return source.materialFlags();
        }
    }
}
