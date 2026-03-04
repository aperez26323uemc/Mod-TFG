package com.uemc.pov_drone.mixin;

import com.uemc.pov_drone.client.PovClientController;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    /**
     * Intercepts raw key events during POV mode. Passes through all input when a
     * screen is open. Whitelists movement keys and ESC/F-keys; checks the attack
     * binding for exit intent; cancels everything else to keep KeyMapping state
     * clean and prevent vanilla game actions.
     */
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void povDrone$filterInputs(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (!PovClientController.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_F3 || isFunctionKey(key)) {
            return;
        }
        if (mc.player == null) {
            return;
        }

        if (action == GLFW.GLFW_PRESS && mc.options.keyAttack.matches(key, scanCode)) {
            PovClientController.tryExitByRaycast();
        }

        boolean allowedKey = mc.options.keyUp.matches(key, scanCode)
                || mc.options.keyDown.matches(key, scanCode)
                || mc.options.keyLeft.matches(key, scanCode)
                || mc.options.keyRight.matches(key, scanCode)
                || mc.options.keyJump.matches(key, scanCode)
                || mc.options.keyShift.matches(key, scanCode);

        if (!allowedKey) {
            ci.cancel();
        }
    }

    private static boolean isFunctionKey(int key) {
        return key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12;
    }
}