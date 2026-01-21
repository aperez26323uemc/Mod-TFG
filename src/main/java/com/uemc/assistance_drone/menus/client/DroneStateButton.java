package com.uemc.assistance_drone.menus.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DroneStateButton extends Button {
    public static final int BUTTON_WIDTH = 57;
    public static final int BUTTON_HEIGHT = 20;

    // Guardamos el ID solo para saber qué enviar al servidor al hacer clic
    private String stateId;

    // CONSTRUCTOR: Recibe YA los textos. No los calcula. Inyección de Dependencias en la UI.
    public DroneStateButton(int x, int y, String stateId, Component label, Component tooltip, OnPress onPress) {
        super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
        this.updateState(stateId, label, tooltip);
    }

    public String getStateId() {
        return this.stateId;
    }

    public void updateState(String stateId, Component label, Component tooltip) {
        this.stateId = stateId;
        if (stateId != null) {
            this.setMessage(label);
            this.setTooltip(Tooltip.create(tooltip));
        } else {
            this.setMessage(CommonComponents.EMPTY);
            this.setTooltip(null);
            this.active = false; // Seguridad extra
        }
    }
}