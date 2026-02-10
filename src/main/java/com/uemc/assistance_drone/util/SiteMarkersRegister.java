package com.uemc.assistance_drone.util;

import com.uemc.assistance_drone.AssistanceDrone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class SiteMarkersRegister {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AssistanceDrone.MODID);
    public static final Supplier<DataComponentType<BlockPos>> START_POS =
            DATA_COMPONENTS.register(
                    ModKeys.SITE_PLANNER_START_POS_KEY,
                    () -> DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build()
            );

    public static final Supplier<DataComponentType<BlockPos>> END_POS =
            DATA_COMPONENTS.register(
                    ModKeys.SITE_PLANNER_END_POS_KEY,
                    () -> DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build()
            );
}
