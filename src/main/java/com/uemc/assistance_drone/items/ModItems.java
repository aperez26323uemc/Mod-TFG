package com.uemc.assistance_drone.items;

import com.uemc.assistance_drone.AssistanceDrone;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    // Create a Deferred Register to hold Items which will all be registered under the "assistance_drone" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AssistanceDrone.MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "assistance_drone" namespace
    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AssistanceDrone.MODID);

    // Creates a new BlockItem with the id "assistance_drone:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> TEST_BLOCK_ITEM =
            ITEMS.registerItem(
                    TestBlockItem.ID,
                    TestBlockItem::new
            );

    /* Creates a new food item with the id "assistance_drone:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));*/

    // Creates a creative tab with the id "assistance_drone:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TAB =
            CREATIVE_MODE_TABS.register(
                    CreativeModeTab.ID,
                    CreativeModeTab::new);
}