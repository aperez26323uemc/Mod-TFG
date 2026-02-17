package com.uemc.assistance_drone.datagen;

import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.sounds.ModSounds;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;

public class ModSoundProvider extends SoundDefinitionsProvider {

    public ModSoundProvider(PackOutput output, ExistingFileHelper helper) {
        super(output, AssistanceDrone.MODID, helper);
    }

    @Override
    public void registerSounds() {
        add(ModSounds.DRONE_FLYING, definition()
                .with(
                        sound(AssistanceDrone.MODID + ":" + ModKeys.DRONE_FLYING_SOUND_KEY)
                                .volume(0.1f)
                                .pitch(1.0f)
                )
                .subtitle(ModKeys.SUBTITLE_FLYING_KEY)
        );
    }
}