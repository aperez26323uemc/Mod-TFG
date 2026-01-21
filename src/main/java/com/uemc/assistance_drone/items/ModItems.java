package com.uemc.assistance_drone.items;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    // Create a Deferred Register to hold Items which will all be registered under the "assistance_drone" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AssistanceDrone.MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "assistance_drone" namespace
    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AssistanceDrone.MODID);
    // Create a Deferred Register to hold DataComponents which will all be registered under the "assistance_drone" namespace
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(AssistanceDrone.MODID);

    public static final DeferredItem<Item> DRONE_ITEM = ITEMS.registerItem(
            DroneItem.ID,
            DroneItem::new);

    public static final DeferredItem<Item> SITE_PLANNER = ITEMS.registerItem(
            SitePlanner.ID,
            SitePlanner::new);

    // Creates a creative tab with the id "assistance_drone:example_tab" for itemS, that is placed after the combat tab
    public static final DeferredHolder<net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TAB =
            CREATIVE_MODE_TABS.register(
                    CreativeModeTab.ID,
                    CreativeModeTab::new);

    public static final Supplier<DataComponentType<BlockPos>> START_POS = DATA_COMPONENTS.registerComponentType(
            ModKeys.SITE_PLANNER_START_POS_KEY,
            builder -> builder
                    // The codec to read/write the data to disk
                    .persistent(BlockPos.CODEC)
                    // The codec to read/write the data across the networking
                    .networkSynchronized(BlockPos.STREAM_CODEC)
    );

    public static final Supplier<DataComponentType<BlockPos>> END_POS = DATA_COMPONENTS.registerComponentType(
            ModKeys.SITE_PLANNER_END_POS_KEY,
            builder -> builder
                    // The codec to read/write the data to disk
                    .persistent(BlockPos.CODEC)
                    // The codec to read/write the data across the networking
                    .networkSynchronized(BlockPos.STREAM_CODEC)
    );
}