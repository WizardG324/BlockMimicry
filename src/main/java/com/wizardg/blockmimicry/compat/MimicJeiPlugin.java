package com.wizardg.blockmimicry.compat;

import com.wizardg.blockmimicry.BlockMimicry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** JEI already lists the block and its recipe on its own. This adds an info page on how to use it and only loads with JEI. */
@JeiPlugin
public class MimicJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(BlockMimicry.MODID, "jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(BlockMimicry.MIMIC_BLOCK_ITEM.get(), Component.translatable("jei.blockmimicry.mimic_block.info"));
    }
}
