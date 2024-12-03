package com.uemc.assistance_drone.items;

import com.uemc.assistance_drone.blocks.ModBlocks;
import net.minecraft.world.item.BlockItem;

public class TestBlockItem extends BlockItem {
    public static final String ID = "test_block_item";
    public TestBlockItem(Properties properties) {
        super(ModBlocks.TEST_BLOCK.get(), properties);
    }
}