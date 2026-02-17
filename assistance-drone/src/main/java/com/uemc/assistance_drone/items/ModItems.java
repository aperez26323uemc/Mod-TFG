package com.uemc.assistance_drone.items;

import com.uemc.assistance_drone.AssistanceDrone;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    // Create a Deferred Register to hold Items which will all be registered under the "assistance_drone" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AssistanceDrone.MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "assistance_drone" namespace
    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AssistanceDrone.MODID);

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
}