package com.uemc.pov_drone.mixin;

import com.uemc.pov_drone.client.PovClientController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the final player.turn() call inside turnPlayer so that, while in
 * POV mode, the fully-processed yaw/pitch delta (already sensitivity-scaled,
 * smoothed for touchpad / cinematic camera, and invert-Y applied by vanilla)
 * goes to PovClientController instead of rotating the player entity.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Redirect(
            method = "turnPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void povDrone$redirectTurn(LocalPlayer player, double yaw, double pitch) {
        if (PovClientController.isActive()) {
            PovClientController.applyMouseDelta((float) yaw, (float) pitch);
        } else {
            player.turn(yaw, pitch);
        }
    }
}