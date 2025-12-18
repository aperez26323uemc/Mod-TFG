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
        // Patrón:
        // I I I  (Hierro, Hierro, Hierro)
        // R D R  (Redstone, Diamante, Redstone)
        // I M I  (Hierro, Motor/Pistón, Hierro)

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.DRONE_ITEM.get())
                .pattern("III")
                .pattern("RDR")
                .pattern("IPI")
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND_BLOCK)
                .define('P', Items.PISTON)
                //.unlockedBy("has_blueprint", has(ModItems.BluePrint.get()))
                .save(output);

        // RECETA DEL BLUEPRINT
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BluePrint.get())
                .requires(Items.PAPER)
                .requires(Items.BLUE_DYE)
                .unlockedBy("has_paper", has(Items.PAPER))
                .save(output);
    }
}
