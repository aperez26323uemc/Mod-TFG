package com.uemc.assistance_drone.items;

import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;

public class CreativeModeTab extends net.minecraft.world.item.CreativeModeTab {
    public static final String ID = ModKeys.CREATIVE_MODE_TAB_KEY;

    private static final net.minecraft.world.item.CreativeModeTab.Builder BUILDER = builder()
            .title(Component.translatable(ModKeys.GUI_CREATIVE_MODE_TAB_TITLE)) // The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT) // Last CreativeModeTab prior to this one
            .icon(() -> ModItems.DRONE_ITEM.get().getDefaultInstance()) // The CreativeModeTab icon
            .displayItems(CreativeModeTab::populateTabItems); // Calls a separate method to add items to the tab

    /**
     * @param parameters The parameters for the tab display.
     * @param output     The output where items are added.
     */
    private static void populateTabItems(ItemDisplayParameters parameters, Output output) {
        output.accept(ModItems.DRONE_ITEM.get());
        output.accept(ModItems.SITE_PLANNER.get());
    }

    public CreativeModeTab() {
        super(BUILDER);
    }
}