package com.uemc.assistance_drone.menus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.uemc.assistance_drone.entities.drone.DroneGoalRegistry;
import com.uemc.assistance_drone.entities.drone.goals.IStateGoal;
import com.uemc.assistance_drone.menus.DroneMenu;
import com.uemc.assistance_drone.networking.DroneStateMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class DroneMenuScreen extends AbstractContainerScreen<DroneMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("assistance_drone", "textures/screens/drone_screen.png");
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");

    // Lista dinámica de objetivos/estados obtenidos del registro
    private final List<IStateGoal> displayableStates = new ArrayList<>();

    // Array fijo de botones para mostrar los estados visibles (máximo 7)
    private final int maxStatesCount;
    private final DroneStateButton[] stateButtons;

    private int scrollOff;
    private boolean isDragging;

    public DroneMenuScreen(DroneMenu droneMenu, Inventory inventory, Component title) {
        super(droneMenu, inventory, title);
        this.imageWidth = 245;

        // 1. Rellenar la lista usando las factorías del registro
        DroneGoalRegistry.getEntries().forEach((id, factory) -> {
            // Instanciamos el goal temporalmente para leer sus datos (ID, Etiqueta)
            Goal g = factory.apply(this.menu.getDrone());
            if (g instanceof IStateGoal stateGoal) {
                displayableStates.add(stateGoal);
            }
        });

        // 2. Calcular cuántos botones caben o hay disponibles (máx 7)
        // NOTA: Si hay 0 estados, evitamos crash con Math.min
        this.maxStatesCount = Math.min(7, Math.max(1, displayableStates.size()));
        this.stateButtons = new DroneStateButton[maxStatesCount];
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);

        // Obtenemos el ID del estado actual del dron (string)
        String currentStateId = this.menu.getDrone().getState();

        // Actualizamos estado activo/inactivo de los botones visibles
        for (DroneStateButton button : this.stateButtons) {
            if (button != null) {
                // El botón está activo si NO es el estado actual
                button.active = !button.visible || !button.getStateId().equals(currentStateId);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        this.renderScroller(guiGraphics, i, j);

        // Actualizamos el contenido de los botones según el scroll
        for (int index = 0; index < stateButtons.length; index++) {
            int listIndex = index + scrollOff;
            if (listIndex < displayableStates.size() && stateButtons[index] != null) {
                IStateGoal state = displayableStates.get(listIndex);
                stateButtons[index].setState(state); // Pasamos el IStateGoal
                stateButtons[index].visible = true;
            } else if (stateButtons[index] != null) {
                stateButtons[index].visible = false;
            }
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderScroller(GuiGraphics pGuiGraphics, int pPosX, int pPosY) {
        int i = displayableStates.size() + 1 - 7;
        if (i > 1) {
            int j = 139 - (27 + (i - 1) * 139 / i);
            int k = 1 + j / i + 139 / i;
            int i1 = Math.min(113, this.scrollOff * k);
            if (this.scrollOff == i - 1) {
                i1 = 113;
            }
            pGuiGraphics.blitSprite(SCROLLER_SPRITE, pPosX + 63, pPosY + 18 + i1, 0, 6, 27);
        } else {
            pGuiGraphics.blitSprite(SCROLLER_DISABLED_SPRITE, pPosX + 63, pPosY + 18, 0, 6, 27);
        }
    }

    private boolean canScroll(int pNumOffers) {
        return pNumOffers > 7;
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        int i = displayableStates.size();
        if (this.canScroll(i)) {
            int j = i - 7;
            this.scrollOff = Mth.clamp((int)((double)this.scrollOff - pScrollY), 0, j);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        int i = displayableStates.size();
        if (this.isDragging) {
            int j = this.topPos + 18;
            int k = j + 139;
            int l = i - 7;
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
        if (this.canScroll(displayableStates.size())
                && pMouseX > (double)(i + 63)
                && pMouseX < (double)(i + 63 + 6)
                && pMouseY > (double)(j + 18)
                && pMouseY <= (double)(j + 18 + 139 + 1)) {
            this.isDragging = true;
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Etiquetas personalizadas si son necesarias
    }

    @Override
    public void init() {
        super.init();

        net.minecraft.client.gui.layouts.GridLayout layout = new net.minecraft.client.gui.layouts.GridLayout();

        // Generación de botones
        for (int i = 0; i < maxStatesCount; i++) {
            // Obtenemos el estado inicial si existe
            IStateGoal initialState = (i < displayableStates.size()) ? displayableStates.get(i) : null;

            // Creamos el botón. IMPORTANTE: DroneStateButton debe actualizarse para aceptar IStateGoal
            stateButtons[i] = new DroneStateButton(0, 0, initialState, btn -> {
                DroneStateButton dBtn = (DroneStateButton)btn;
                if (dBtn.getStateId() != null && !dBtn.getStateId().isEmpty()) {
                    updateDroneState(dBtn.getStateId());
                }
            });

            layout.addChild(stateButtons[i], i, 0);
        }

        layout.arrangeElements();
        layout.setPosition(this.leftPos + 5, this.topPos + 18);
        layout.visitWidgets(this::addRenderableWidget);
    }

    private void updateDroneState(String stateId) {
        int droneId = menu.getDrone().getId();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new DroneStateMessage(droneId, stateId)
        );
    }
}