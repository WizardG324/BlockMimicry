package com.wizardg.blockmimicry.datagen;

import com.wizardg.blockmimicry.BlockMimicry;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = BlockMimicry.MODID, value = Dist.CLIENT) // datagen uses client model classes
public class ModDataGen {
    @SubscribeEvent
    static void gather(GatherDataEvent.Client event) {
        PackOutput out = event.getGenerator().getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        event.getGenerator().addProvider(true, new Models(out));
        event.getGenerator().addProvider(true, new Lang(out));
        event.getGenerator().addProvider(true, new BlockTagsProvider(out, lookup, BlockMimicry.MODID) {
            @Override
            protected void addTags(HolderLookup.Provider registries) {
                tag(BlockTags.MINEABLE_WITH_AXE).add(BlockMimicry.MIMIC_BLOCK.get());
            }
        });
        event.getGenerator().addProvider(true, new LootTableProvider(out, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK)), lookup));
        event.getGenerator().addProvider(true, new Recipes.Runner(out, lookup));
    }

    static class Models extends ModelProvider {
        Models(PackOutput out) {
            super(out, BlockMimicry.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            blocks.createTrivialCube(BlockMimicry.MIMIC_BLOCK.get());
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return BlockMimicry.BLOCKS.getEntries().stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return BlockMimicry.ITEMS.getEntries().stream();
        }
    }

    static class Lang extends LanguageProvider {
        Lang(PackOutput out) {
            super(out, BlockMimicry.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            addBlock(BlockMimicry.MIMIC_BLOCK, "Mimic Block");
            add("itemGroup." + BlockMimicry.MODID, "Block Mimicry");
            add("jei.blockmimicry.mimic_block.info", "Right-click a placed Mimic Block with any block to copy it. "
                    + "It takes on that block's look, shape, sounds, light, redstone power and mining speed.\n\n"
                    + "Sneak and right-click with an empty hand to take the block back out. "
                    + "Blocks that store items or data, like chests, can't be copied.");
        }
    }

    static class Loot extends BlockLootSubProvider {
        Loot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            dropSelf(BlockMimicry.MIMIC_BLOCK.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return BlockMimicry.BLOCKS.getEntries().stream().<Block>map(Holder::value).toList();
        }
    }

    static class Recipes extends RecipeProvider {
        Recipes(HolderLookup.Provider registries, RecipeOutput output) {
            super(registries, output);
        }

        @Override
        protected void buildRecipes() {
            // Any dye around any wool = 4 mimic blocks
            shaped(RecipeCategory.BUILDING_BLOCKS, BlockMimicry.MIMIC_BLOCK.get(), 4)
                    .pattern(" D ")
                    .pattern("DWD")
                    .pattern(" D ")
                    .define('D', tag(Tags.Items.DYES))
                    .define('W', tag(ItemTags.WOOL))
                    .unlockedBy("has_wool", has(ItemTags.WOOL))
                    .save(output);
        }

        static class Runner extends RecipeProvider.Runner {
            Runner(PackOutput out, CompletableFuture<HolderLookup.Provider> lookup) {
                super(out, lookup);
            }

            @Override
            protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
                return new Recipes(registries, output);
            }

            @Override
            public String getName() {
                return BlockMimicry.MODID + " recipes";
            }
        }
    }
}
