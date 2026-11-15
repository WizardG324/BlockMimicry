package com.wizardg.blockmimicry;

import com.wizardg.blockmimicry.block.MimicBlock;
import com.wizardg.blockmimicry.block.entity.MimicBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import com.wizardg.blockmimicry.gametest.MimicCrashTest;
import com.wizardg.blockmimicry.item.MimicBlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BlockMimicry.MODID)
public class BlockMimicry {
    public static final String MODID = "blockmimicry";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<MimicBlock> MIMIC_BLOCK = BLOCKS.registerBlock("mimic_block", MimicBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD));
    public static final DeferredItem<MimicBlockItem> MIMIC_BLOCK_ITEM = ITEMS.registerItem("mimic_block",
            props -> new MimicBlockItem(MIMIC_BLOCK.get(), props), props -> props.useBlockDescriptionPrefix());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MimicBlockEntity>> MIMIC_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("mimic_block", () -> new BlockEntityType<>(MimicBlockEntity::new, MIMIC_BLOCK.get()));

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .icon(() -> MIMIC_BLOCK_ITEM.get().getDefaultInstance())
            .displayItems((params, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    public BlockMimicry(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        MimicCrashTest.registerFunctions(modEventBus);
    }
}
