package com.uemc.assistance_drone.mixin;

import com.uemc.assistance_drone.advancements.ModCriteriaTriggers;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BeaconBlockEntity.class)
public class BeaconBlockEntityMixin {

    @Inject(method = "applyEffects", at = @At("TAIL"))
    private static void assistanceDrone$applyToDrones(
            Level level, BlockPos pos, int beaconLevel, Holder<MobEffect> primary, Holder<MobEffect> secondary, CallbackInfo ci
    ) {
        if (level.isClientSide || primary == null) return;

        boolean primaryIsHaste = primary.equals(MobEffects.DIG_SPEED);
        boolean secondaryIsHaste = secondary != null && secondary.equals(MobEffects.DIG_SPEED);

        if (!primaryIsHaste && !secondaryIsHaste) return;

        double range = beaconLevel * 10 + 10;
        int duration = (9 + beaconLevel * 2) * 20;

        AABB aabb = new AABB(pos).inflate(range).expandTowards(0.0, level.getHeight(), 0.0);
        List<DroneEntity> drones = level.getEntitiesOfClass(DroneEntity.class, aabb);

        boolean appliedToAnyDrone = false;

        for (DroneEntity drone : drones) {
            boolean applied = false;

            if (primaryIsHaste) {
                int amplifier = 0;
                if (beaconLevel >= 4 && secondaryIsHaste) {
                    amplifier = 1;
                }
                drone.addEffect(new MobEffectInstance(primary, duration, amplifier, true, true));
                applied = true;
            }

            if (beaconLevel >= 4 && secondaryIsHaste && !primaryIsHaste) {
                drone.addEffect(new MobEffectInstance(secondary, duration, 0, true, true));
                applied = true;
            }

            if (applied) {
                appliedToAnyDrone = true;
            }
        }


        // --- TRIGGER DE ADVANCEMENT ---
        if (appliedToAnyDrone) {
            List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, aabb);
            for (ServerPlayer player : players) {
                ModCriteriaTriggers.DRONE_HASTE_MINING.get().trigger(player);
            }
        }
    }
}