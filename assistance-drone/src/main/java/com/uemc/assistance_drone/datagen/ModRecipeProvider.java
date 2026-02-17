package com.uemc.assistance_drone.datagen;

import com.uemc.assistance_drone.items.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {

        // RECETA DEL DRON
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.DRONE_ITEM.get())
                .pattern("Q Q")
                .pattern("DCR")
                .pattern("FIF")
                .define('Q', Items.QUARTZ)
                .define('D', Items.DIAMOND)
                .define('C', Items.COPPER_INGOT)
                .define('R', Items.REDSTONE)
                .define('F', Items.IRON_INGOT)
                .define('I', Items.IRON_BLOCK)
                .unlockedBy("drone", has(Items.QUARTZ))
                .save(output);

        // RECETA DE Planificador de Zona
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SITE_PLANNER.get())
                .pattern("IID")
                .pattern("GMC")
                .pattern("IIR")
                .define('I', Items.IRON_INGOT)
                .define('D', Items.DIAMOND)
                .define('G', Items.GOLD_INGOT)
                .define('M', Items.MAP)
                .define('C', Items.COPPER_INGOT)
                .define('R', Items.REDSTONE)
                .unlockedBy("site_planner", has(ModItems.DRONE_ITEM))
                .save(output);
    }
}
