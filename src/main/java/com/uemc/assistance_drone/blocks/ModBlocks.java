package com.uemc.assistance_drone.blocks;

import com.uemc.assistance_drone.AssistanceDrone;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AssistanceDrone.MODID);

    public static final DeferredBlock<Block> TEST_BLOCK =
            BLOCKS.registerBlock(
                    TestBlock.ID,
                    TestBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
            );
}