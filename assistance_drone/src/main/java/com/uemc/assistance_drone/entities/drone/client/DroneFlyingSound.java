package com.uemc.assistance_drone.entities.drone.client;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.sounds.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DroneFlyingSound extends AbstractTickableSoundInstance {
    private final DroneEntity drone;

    public DroneFlyingSound(DroneEntity drone) {
        // Usamos el sonido registrado, categoría NEUTRAL y sonido aleatorio desactivado
        super(ModSounds.DRONE_FLYING.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.drone = drone;
        this.looping = true; // ¡Vital!
        this.delay = 0;
        this.volume = 0.1F; // Empieza bajito
        this.pitch = 1.0F; // Tono base

        // Posición inicial
        this.x = drone.getX();
        this.y = drone.getY();
        this.z = drone.getZ();
    }

    @Override
    public void tick() {
        if (this.drone.isRemoved()) {
            this.stop();
            return;
        }

        this.x = this.drone.getX();
        this.y = this.drone.getY();
        this.z = this.drone.getZ();

        // Velocidad horizontal
        float speed = (float) this.drone.getDeltaMovement().horizontalDistance();

        // --- AJUSTE DE PITCH (TONO) ---
        // Antes: 1.0F + (speed * 2.0F) -> Demasiado cambio
        // Ahora: 1.0F + (speed * 0.4F) -> Mucho más sutil.
        // Si va a tope (0.6), el pitch será 1.24 (un 24% más agudo), suficiente para notar aceleración.
        this.pitch = 1.0F + (speed * 0.4F);

        // --- AJUSTE DE VOLUMEN ---
        // Base: 0.5F (Para que se oiga estando quieto)
        // Extra por velocidad: hasta +0.5F
        // Total máximo: 1.0F (El estándar de Minecraft)
        this.volume = 0.5F + Math.min(speed, 0.5F);
    }

    // Configuración para que se oiga bien en 3D
    @Override
    public boolean isRelative() {
        return false;
    }
}