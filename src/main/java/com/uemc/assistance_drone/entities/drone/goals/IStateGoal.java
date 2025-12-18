package com.uemc.assistance_drone.entities.drone.goals;

import net.minecraft.network.chat.Component;

public interface IStateGoal {
    // El ID interno que se envía por red
    String getStateId();

    // El texto para el botón (ej: "drone.state.follow")
    default Component getButtonLabel() {
        return Component.translatable("drone.state." + getStateId());
    }

    // El tooltip para el botón
    default Component getButtonTooltip() {
        return Component.translatable("drone.state." + getStateId() + ".desc");
    }
}