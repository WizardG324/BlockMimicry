package com.wizardg.blockmimicry.compat;

import com.wizardg.blockmimicry.block.entity.MimicBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** When Jade is installed, show the copied block's info instead */
@WailaPlugin
public class MimicJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (accessor instanceof BlockAccessor block && block.getBlockEntity() instanceof MimicBlockEntity be) {
                BlockState m = be.getMimic();
                if (m != null) return registration.blockAccessor().from(block).blockEntity(() -> null).blockState(m).build();
            }
            return accessor;
        });
    }
}
