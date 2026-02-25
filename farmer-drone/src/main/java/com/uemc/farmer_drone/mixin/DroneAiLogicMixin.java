package com.uemc.farmer_drone.mixin;

import com.uemc.assistance_drone.entities.drone.DroneAiLogic;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.IPlantable;
import net.neoforged.neoforge.common.PlantType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends {@link DroneAiLogic#placeBlock(BlockPos, ItemStack)} so seed items implementing
 * {@link IPlantable} can be planted on farmland even when they are not plain {@code BlockItem}s.
 */
@Mixin(value = DroneAiLogic.class, remap = false)
public abstract class DroneAiLogicMixin {

    @Shadow @Final private DroneEntity drone;

    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private void farmer_drone$placePlantable(BlockPos pos, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IPlantable plantable)) {
            return;
        }

        Level level = drone.level();
        BlockPos farmlandPos = pos.below();

        if (!level.getBlockState(farmlandPos).is(Blocks.FARMLAND)) {
            return;
        }

        if (plantable.getPlantType(level, pos) != PlantType.CROP) {
            return;
        }

        if (!level.getBlockState(pos).canBeReplaced()) {
            cir.setReturnValue(false);
            return;
        }

        BlockState plantState = plantable.getPlant(level, pos);
        if (!plantState.canSurvive(level, pos)) {
            cir.setReturnValue(false);
            return;
        }

        boolean placed = level.setBlock(pos, plantState, 3);
        if (placed && !drone.level().isClientSide) {
            stack.shrink(1);
        }

        cir.setReturnValue(placed);
    }
}
