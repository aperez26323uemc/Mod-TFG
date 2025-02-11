package com.uemc.assistance_drone.menus.client;

import com.uemc.assistance_drone.entities.drone.DroneState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
class DroneStateButton extends Button {
    public static final int BUTTON_WIDTH = 57;
    public static final int BUTTON_HEIGHT = 20;
    private DroneState state;

    /**
     * Crea un botón que muestra el estado del dron.
     *
     * @param x Posición X del botón.
     * @param y Posición Y del botón.
     * @param state El string que representa el estado.
     * @param onPress Acción a ejecutar al presionar el botón.
     */
    public DroneStateButton(int x, int y, DroneState state, OnPress onPress) {
        // Utilizamos Component.literal(state) para que el botón muestre el estado
        super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal(state.getStateName()), onPress, DEFAULT_NARRATION);
        this.state = state;
    }

    public String getState() {
        return this.state.getStateName();
    }

    public void setState(DroneState state) {
        this.state = state;
        this.setMessage(state != null ? Component.literal(state.getStateName()) : CommonComponents.EMPTY);
    }
}
