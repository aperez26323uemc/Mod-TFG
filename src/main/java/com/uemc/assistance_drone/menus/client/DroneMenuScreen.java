package com.uemc.assistance_drone.menus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.goals.DroneGoalRegistry;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.menus.DroneMenu;
import com.uemc.assistance_drone.networking.DroneStateMessage;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GUI screen for the drone's inventory and state management interface.
 * <p>
 * Provides a scrollable list of available drone states with dynamic filtering based on
 * current drone conditions (e.g., Site Planner presence). The screen updates in real-time
 * to show/hide states as items are added or removed from the drone's inventory.
 * </p>
 * <p>
 * State buttons are rendered with visual feedback for the currently active state and
 * automatically disabled states are removed from view.
 * </p>
 *
 * @see DroneMenu
 * @see DroneGoalRegistry
 */
@OnlyIn(Dist.CLIENT)
public class DroneMenuScreen extends AbstractContainerScreen<DroneMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "textures/screens/drone_screen.png");
    private static final ResourceLocation SCROLLER_SPRITE =
            ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE =
            ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");

    private static final int MAX_VISIBLE_STATES = 7;

    private final List<DroneGoalRegistry.StateDefinition> allStates = new ArrayList<>();
    private List<DroneGoalRegistry.StateDefinition> visibleStates = new ArrayList<>();
    private final DroneStateButton[] stateButtons = new DroneStateButton[MAX_VISIBLE_STATES];

    private int scrollOff;
    private boolean isDragging;

    /**
     * Constructs the drone menu screen.
     *
     * @param droneMenu the container menu instance
     * @param inventory the player's inventory
     * @param title the screen title component
     */
    public DroneMenuScreen(DroneMenu droneMenu, Inventory inventory, Component title) {
        super(droneMenu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 191;

        this.allStates.addAll(DroneGoalRegistry.getDefinitions());
        this.visibleStates.addAll(this.allStates);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    /**
     * Renders the entire screen including background, buttons and scrollbar.
     * <p>
     * Dynamically filters the state list each frame based on current drone conditions,
     * allowing real-time UI updates as inventory changes.
     * </p>
     */
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);

        if (this.menu.getDrone() != null) {
            this.visibleStates = this.allStates.stream()
                    .filter(def -> def.isAvailable(this.menu.getDrone()))
                    .toList();
        } else {
            this.visibleStates = Collections.emptyList();
        }

        int maxScroll = Math.max(0, visibleStates.size() - MAX_VISIBLE_STATES);
        this.scrollOff = Mth.clamp(this.scrollOff, 0, maxScroll);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        renderScrollbar(guiGraphics);
        updateStateButtons();

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * Renders the scrollbar with dynamic sizing based on the amount of visible states.
     */
    private void renderScrollbar(GuiGraphics guiGraphics) {
        int totalVisible = visibleStates.size();
        int i = totalVisible + 1 - MAX_VISIBLE_STATES;

        if (i > 1) {
            int j = 139 - (27 + (i - 1) * 139 / i);
            int k = 1 + j / i + 139 / i;
            int i1 = Math.min(113, this.scrollOff * k);
            if (this.scrollOff == i - 1) {
                i1 = 113;
            }
            guiGraphics.blitSprite(SCROLLER_SPRITE, this.leftPos + 67, this.topPos + 33 + i1, 0, 6, 27);
        } else {
            guiGraphics.blitSprite(SCROLLER_DISABLED_SPRITE, this.leftPos + 67, this.topPos + 33, 0, 6, 27);
        }
    }

    /**
     * Updates all state buttons with current data from the filtered state list.
     * <p>
     * Buttons are marked inactive when displaying the drone's current state to provide
     * visual feedback. Buttons beyond the list range are hidden.
     * </p>
     */
    private void updateStateButtons() {
        String currentStateId = (this.menu.getDrone() != null) ? this.menu.getDrone().getState() : "";

        for (int index = 0; index < stateButtons.length; index++) {
            int listIndex = index + scrollOff;
            DroneStateButton btn = stateButtons[index];

            if (listIndex < visibleStates.size() && btn != null) {
                DroneGoalRegistry.StateDefinition def = visibleStates.get(listIndex);
                btn.updateState(def.id(), def.getLabel(), def.getTooltip());
                btn.visible = true;
                btn.active = !def.id().equals(currentStateId);
            } else if (btn != null) {
                btn.updateState(null, CommonComponents.EMPTY, CommonComponents.EMPTY);
                btn.visible = false;
            }
        }
    }

    /**
     * Determines if the state list can be scrolled based on item count.
     *
     * @param numStates number of states in the filtered list
     * @return true if scrolling is needed
     */
    private boolean canScroll(int numStates) {
        return numStates > MAX_VISIBLE_STATES;
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        int i = visibleStates.size();
        if (this.canScroll(i)) {
            int j = i - MAX_VISIBLE_STATES;
            this.scrollOff = Mth.clamp((int)((double)this.scrollOff - pScrollY), 0, j);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        int i = visibleStates.size();
        if (this.isDragging) {
            int j = this.topPos + 18;
            int k = j + 139;
            int l = i - MAX_VISIBLE_STATES;
            float f = ((float)pMouseY - (float)j - 13.5F) / ((float)(k - j) - 27.0F);
            f = f * (float)l + 0.5F;
            this.scrollOff = Mth.clamp((int)f, 0, l);
            return true;
        } else {
            return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
        }
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        this.isDragging = false;
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        if (this.canScroll(visibleStates.size())
                && pMouseX > (double)(i + 63)
                && pMouseX < (double)(i + 63 + 6)
                && pMouseY > (double)(j + 18)
                && pMouseY <= (double)(j + 18 + 139 + 1)) {
            this.isDragging = true;
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    /**
     * Renders text labels for the different sections of the UI.
     */
    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int textColor = 4210752;

        Component modesLabel = Component.translatable(ModKeys.GUI_DRONE_MENU_MODES_LABEL);
        Component sitePlannerLabel = ModItems.SITE_PLANNER.get().getDescription();
        Component storageLabel = Component.translatable(ModKeys.GUI_DRONE_MENU_STORAGE_LABEL);

        if (this.menu.getDrone() != null) {
            guiGraphics.drawString(this.font, this.menu.getDrone().getDisplayName(), 8, 6, textColor, false);
        }
        guiGraphics.drawString(this.font, modesLabel, 10, 20, textColor, false);
        guiGraphics.drawString(this.font, sitePlannerLabel, 88, 20, textColor, false);
        guiGraphics.drawString(this.font, storageLabel, 178, 20, textColor, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 88, 95, textColor, false);
    }

    /**
     * Initializes all UI components including state selection buttons.
     * <p>
     * Buttons are arranged in a vertical grid layout and configured with click handlers
     * that send state change requests to the server.
     * </p>
     */
    @Override
    public void init() {
        super.init();

        GridLayout layout = new GridLayout();

        for (int i = 0; i < MAX_VISIBLE_STATES; i++) {
            stateButtons[i] = new DroneStateButton(0, 0, null, CommonComponents.EMPTY, CommonComponents.EMPTY, btn -> {
                DroneStateButton dBtn = (DroneStateButton)btn;
                if (dBtn.getStateId() != null && !dBtn.getStateId().isEmpty()) {
                    updateDroneState(dBtn.getStateId());
                }
            });
            layout.addChild(stateButtons[i], i, 0);
        }

        layout.arrangeElements();
        layout.setPosition(this.leftPos + 9, this.topPos + 33);
        layout.visitWidgets(this::addRenderableWidget);
    }

    /**
     * Sends a state change request to the server for the drone.
     *
     * @param stateId the new state identifier to set
     */
    private void updateDroneState(String stateId) {
        if (this.menu.getDrone() == null) return;

        int droneId = menu.getDrone().getId();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new DroneStateMessage(droneId, stateId)
        );
    }

    /**
     * Renders tooltips for hovered UI elements.
     * <p>
     * Provides contextual help for the Site Planner slot when empty.
     * </p>
     */
    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && !this.hoveredSlot.hasItem()) {
            if (this.hoveredSlot.index == 0) {
                guiGraphics.renderTooltip(this.font,
                        Component.translatable(ModKeys.GUI_SITE_PLANNER_SLOT_HINT), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}