package com.uemc.assistance_drone.items;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;

public class CreativeModeTab extends net.minecraft.world.item.CreativeModeTab {
    public static final String ID = "assistance_drone";

    private static final net.minecraft.world.item.CreativeModeTab.Builder BUILDER = builder()
            .title(Component.translatable("itemGroup.assistance_drone")) // The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT) // Last CreativeModeTab prior to this one
            .icon(() -> ModItems.TEST_BLOCK_ITEM.get().getDefaultInstance()) // The CreativeModeTab icon
            .displayItems(CreativeModeTab::populateTabItems); // Calls a separate method to add items to the tab

    /**
     * @param parameters The parameters for the tab display.
     * @param output     The output where items are added.
     */
    private static void populateTabItems(ItemDisplayParameters parameters, Output output) {
        // Add items to the tab. For your own tabs, this method is preferred over the event
        output.accept(ModItems.TEST_BLOCK_ITEM.get());
        // Add more items here if necessary:
        // output.accept(ModItems.OTHER_ITEM.get());
    }

    public CreativeModeTab() {
        super(BUILDER);
    }
}