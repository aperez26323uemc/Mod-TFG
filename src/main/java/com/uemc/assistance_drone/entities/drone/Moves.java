package com.uemc.assistance_drone.entities.drone;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public interface Moves {
    void mod_TFG$move(CallbackInfo ci);
}
