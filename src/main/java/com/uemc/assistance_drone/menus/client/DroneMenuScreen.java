package com.uemc.assistance_drone.menus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.DroneGoalRegistry;
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
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class DroneMenuScreen extends AbstractContainerScreen<DroneMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "textures/screens/drone_screen.png");
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");

    // MODELO: Lista de definiciones obtenida del registro
    private final List<DroneGoalRegistry.StateDefinition> displayableStates = new ArrayList<>();

    // VISTA: Array de botones "tontos" (Solo pintan lo que les digamos)
    private final int maxStatesCount = 7; // Fijo a 7 para coincidir con la altura del scroll (139px)
    private final DroneStateButton[] stateButtons = new DroneStateButton[maxStatesCount];

    private int scrollOff;
    private boolean isDragging;

    public DroneMenuScreen(DroneMenu droneMenu, Inventory inventory, Component title) {
        super(droneMenu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 191;

        // 1. Cargar datos del modelo (Registry)
        this.displayableStates.addAll(DroneGoalRegistry.getDefinitions());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Render del Scroller
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        this.renderScroller(guiGraphics, i, j);

        // CONTROLADOR: Actualizar contenido de los botones según el Scroll
        for (int index = 0; index < stateButtons.length; index++) {
            int listIndex = index + scrollOff;
            DroneStateButton btn = stateButtons[index];

            if (listIndex < displayableStates.size() && btn != null) {
                // Obtenemos la definición del modelo
                DroneGoalRegistry.StateDefinition def = displayableStates.get(listIndex);

                // Inyectamos datos en la vista (Update State)
                btn.updateState(def.id(), def.getLabel(), def.getTooltip());
                btn.visible = true;
            } else if (btn != null) {
                // Limpiamos el botón si no hay dato
                btn.updateState(null, CommonComponents.EMPTY, CommonComponents.EMPTY);
                btn.visible = false;
            }
        }

        // CONTROLADOR: Sincronizar estado visual de los botones
        String currentStateId = this.menu.getDrone().getState();

        for (DroneStateButton button : this.stateButtons) {
            if (button != null) {
                // Activo si NO es el estado actual (para que no puedas clicar el que ya tienes)
                button.active = !button.visible || !button.getStateId().equals(currentStateId);
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
            pGuiGraphics.blitSprite(SCROLLER_SPRITE, pPosX + 67, pPosY + 33 + i1, 0, 6, 27);
        } else {
            pGuiGraphics.blitSprite(SCROLLER_DISABLED_SPRITE, pPosX + 67, pPosY + 33, 0, 6, 27);
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
        int textColor = 4210752;
        Component modesLabel = Component.translatable(ModKeys.GUI_DRONE_MENU_MODES_LABEL);
        // Asegúrate de importar ModItems correctamente
        Component sitePlannerLabel = ModItems.SITE_PLANNER.get().getDescription();
        Component storageLabel = Component.translatable(ModKeys.GUI_DRONE_MENU_STORAGE_LABEL);

        guiGraphics.drawString(this.font, this.menu.getDrone().getDisplayName(), 8, 6, textColor, false);
        guiGraphics.drawString(this.font, modesLabel, 10, 20, textColor, false);
        guiGraphics.drawString(this.font, sitePlannerLabel, 88, 20, textColor, false);
        guiGraphics.drawString(this.font, storageLabel, 178, 20, textColor, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 88, 95, textColor, false);
    }

    @Override
    public void init() {
        super.init();

        GridLayout layout = new GridLayout();

        // Generación de botones (INYECCIÓN DE DEPENDENCIAS EN UI)
        for (int i = 0; i < maxStatesCount; i++) {
            // Datos iniciales (si existen)
            DroneGoalRegistry.StateDefinition def = (i < displayableStates.size()) ? displayableStates.get(i) : null;

            String id = (def != null) ? def.id() : null;
            Component label = (def != null) ? def.getLabel() : CommonComponents.EMPTY;
            Component tooltip = (def != null) ? def.getTooltip() : CommonComponents.EMPTY;

            // Creamos el botón con el constructor Senior (Sin IStateGoal)
            stateButtons[i] = new DroneStateButton(0, 0, id, label, tooltip, btn -> {
                DroneStateButton dBtn = (DroneStateButton)btn;
                if (dBtn.getStateId() != null && !dBtn.getStateId().isEmpty()) {
                    updateDroneState(dBtn.getStateId());
                }
            });

            // Layout vertical (fila i, columna 0)
            layout.addChild(stateButtons[i], i, 0);
        }

        layout.arrangeElements();
        // Coordenadas ajustadas según tu snippet
        layout.setPosition(this.leftPos + 9, this.topPos + 33);
        layout.visitWidgets(this::addRenderableWidget);
    }

    private void updateDroneState(String stateId) {
        int droneId = menu.getDrone().getId();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new DroneStateMessage(droneId, stateId)
        );
    }
}