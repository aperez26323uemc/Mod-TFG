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
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class DroneMenuScreen extends AbstractContainerScreen<DroneMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "textures/screens/drone_screen.png");
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");

    // MODELO COMPLETO: Todos los estados registrados en el sistema
    private final List<DroneGoalRegistry.StateDefinition> allStates = new ArrayList<>();

    // VISTA FILTRADA: Solo los estados disponibles actualmente (se recalcula dinámicamente)
    private List<DroneGoalRegistry.StateDefinition> visibleStates = new ArrayList<>();

    // VISTA: Array de botones "tontos" (Solo pintan lo que les digamos)
    private final int maxStatesCount = 7; // Fijo a 7 para coincidir con la altura del scroll (139px)
    private final DroneStateButton[] stateButtons = new DroneStateButton[maxStatesCount];

    private int scrollOff;
    private boolean isDragging;

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

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);

        // 1. FILTRADO DINÁMICO (Lógica Senior)
        // Reconstruimos la lista visible en cada frame.
        // Esto permite que los botones aparezcan/desaparezcan al instante si el jugador mueve items.
        this.visibleStates = this.allStates.stream()
                .filter(def -> def.isAvailable(this.menu.getDrone()))
                .toList();

        // Ajustamos el scroll por si la lista se ha encogido de repente
        int maxScroll = Math.max(0, visibleStates.size() - 7);
        this.scrollOff = Mth.clamp(this.scrollOff, 0, maxScroll);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // 2. RENDER DEL SCROLLER (Usando el tamaño de visibleStates)
        int totalVisible = visibleStates.size();
        int i = totalVisible + 1 - 7;
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

        // 3. ACTUALIZACIÓN DE BOTONES
        String currentStateId = this.menu.getDrone().getState();

        for (int index = 0; index < stateButtons.length; index++) {
            int listIndex = index + scrollOff;
            DroneStateButton btn = stateButtons[index];

            if (listIndex < visibleStates.size() && btn != null) {
                // Obtenemos la definición de la lista FILTRADA
                DroneGoalRegistry.StateDefinition def = visibleStates.get(listIndex);

                // Inyectamos datos en la vista (Update State)
                btn.updateState(def.id(), def.getLabel(), def.getTooltip());
                btn.visible = true;

                // Lógica de estado activo:
                // Solo deshabilitamos el botón si ES el estado actual (para indicar selección).
                // Como ya filtramos la lista, sabemos que el estado ES disponible.
                boolean isCurrent = def.id().equals(currentStateId);
                btn.active = !isCurrent;

            } else if (btn != null) {
                // Limpiamos el botón si no hay dato (fuera de rango de la lista visible)
                btn.updateState(null, CommonComponents.EMPTY, CommonComponents.EMPTY);
                btn.visible = false;
            }
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private boolean canScroll(int pNumOffers) {
        return pNumOffers > 7;
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        int i = visibleStates.size(); // IMPORTANTE: Usar visibleStates
        if (this.canScroll(i)) {
            int j = i - 7;
            this.scrollOff = Mth.clamp((int)((double)this.scrollOff - pScrollY), 0, j);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        int i = visibleStates.size(); // IMPORTANTE: Usar visibleStates
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
        // IMPORTANTE: Usar visibleStates
        if (this.canScroll(visibleStates.size())
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

        // Uso de constantes desde ModKeys para mantenibilidad
        Component modesLabel = Component.translatable(ModKeys.GUI_DRONE_MENU_MODES_LABEL);
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
            // Inicializamos los botones vacíos.
            stateButtons[i] = new DroneStateButton(0, 0, null, CommonComponents.EMPTY, CommonComponents.EMPTY, btn -> {
                DroneStateButton dBtn = (DroneStateButton)btn;
                if (dBtn.getStateId() != null && !dBtn.getStateId().isEmpty()) {
                    updateDroneState(dBtn.getStateId());
                }
            });

            // Layout vertical (fila i, columna 0)
            layout.addChild(stateButtons[i], i, 0);
        }

        layout.arrangeElements();
        layout.setPosition(this.leftPos + 9, this.topPos + 33);
        layout.visitWidgets(this::addRenderableWidget);
    }

    private void updateDroneState(String stateId) {
        int droneId = menu.getDrone().getId();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new DroneStateMessage(droneId, stateId)
        );
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && !this.hoveredSlot.hasItem()) {
            if (this.hoveredSlot.index == 0) {
                guiGraphics.renderTooltip(this.font, Component.translatable(ModKeys.GUI_SITE_PLANNER_SLOT_HINT), mouseX, mouseY);
                return;
            }
        }

        // 2. Comportamiento estándar (Tooltips de items y botones)
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}