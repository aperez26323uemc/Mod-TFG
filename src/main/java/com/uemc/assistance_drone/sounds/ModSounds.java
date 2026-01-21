package com.uemc.assistance_drone.sounds;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, AssistanceDrone.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_FLYING =
            SOUNDS.register(ModKeys.DRONE_FLYING_SOUND_KEY,
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "sounds/drone_flying")
                    ));
}