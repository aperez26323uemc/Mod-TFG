package com.uemc.assistance_drone.menus.client;

import com.uemc.assistance_drone.entities.drone.goals.IStateGoal;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DroneStateButton extends Button {
    public static final int BUTTON_WIDTH = 57;
    public static final int BUTTON_HEIGHT = 20;
    private IStateGoal state;

    public DroneStateButton(int x, int y, IStateGoal state, OnPress onPress) {
        super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                state != null ? state.getButtonLabel() : CommonComponents.EMPTY,
                onPress, DEFAULT_NARRATION);
        this.state = state;

        if (state != null) {
            this.setTooltip(Tooltip.create(state.getButtonTooltip()));
        }
    }

    /**
     * Obtiene el ID del estado (ej. "IDLE", "FOLLOW")
     */
    public String getStateId() {
        return this.state != null ? this.state.getStateId() : "";
    }

    public void setState(IStateGoal state) {
        this.state = state;
        if (state != null) {
            this.setMessage(state.getButtonLabel());
            this.setTooltip(Tooltip.create(state.getButtonTooltip()));
        } else {
            this.setMessage(CommonComponents.EMPTY);
            this.setTooltip(null);
        }
    }
}