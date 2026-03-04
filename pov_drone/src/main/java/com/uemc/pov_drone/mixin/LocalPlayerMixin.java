package com.uemc.pov_drone.mixin;

import com.uemc.pov_drone.client.PovClientController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the arm-swing animation on the local player while in drone POV mode.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V",
            at = @At("HEAD"), cancellable = true)
    private void povDrone$suppressSwing(InteractionHand hand, CallbackInfo ci) {
        if (PovClientController.isActive()) {
            ci.cancel();
        }
    }
}