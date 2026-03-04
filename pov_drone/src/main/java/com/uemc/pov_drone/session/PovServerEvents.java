package com.uemc.pov_drone.session;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.util.ModKeys;
import com.uemc.pov_drone.PovDrone;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = PovDrone.MODID)
public final class PovServerEvents {

    private PovServerEvents() {
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof DroneEntity drone)) {
            return;
        }
        if (!ModKeys.STATE_IDLE.equals(drone.getState())) {
            return;
        }
        if (!player.getUUID().equals(drone.getOwnerUUID())) {
            return;
        }

        PovSessionManager.tryStart(player, drone);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PovSessionManager.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PovSessionManager.onBodyDamaged(player);
        }
    }
}
