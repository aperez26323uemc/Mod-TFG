package com.uemc.assistance_drone.menus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.uemc.assistance_drone.entities.drone.DroneState;
import com.uemc.assistance_drone.entities.drone.DroneStateList;
import com.uemc.assistance_drone.menus.DroneMenu;
import com.uemc.assistance_drone.network.DroneStateMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class DroneMenuScreen extends AbstractContainerScreen<DroneMenu>{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("assistance_drone", "textures/screens/drone_screen.png");
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");
    private final DroneStateList states;
    // Array fijo de 7 botones para mostrar los estados visibles
    private final int maxStatesCount;
    private final DroneStateButton[] stateButtons;
    private int scrollOff;
    private boolean isDragging;

    public DroneMenuScreen(DroneMenu droneMenu, net.minecraft.world.entity.player.Inventory inventory, Component title) {
        super(droneMenu, inventory, title);
        this.imageWidth = 245;
        states = DroneStateList.getAllowedStates();
        maxStatesCount = Math.min(7, states.size());
        stateButtons = new DroneStateButton[maxStatesCount];
    }

    /**
     * Renderiza el fondo del menú.
     *
     * @param guiGraphics Objeto para manejar gráficos GUI.
     * @param partialTicks Tiempo parcial entre ticks.
     * @param gx           Posición X del mouse.
     * @param gy           Posición Y del mouse.
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    /**
     * Renderiza los elementos del menú, incluyendo el fondo, iconos y tooltips.
     *
     * @param guiGraphics Objeto para manejar gráficos GUI.
     * @param mouseX      Posición X del mouse.
     * @param mouseY      Posición Y del mouse.
     * @param partialTicks Tiempo parcial entre ticks.
     */
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks); // Renderiza el fondo oscuro cuando el menú está abierto
        super.render(guiGraphics, mouseX, mouseY, partialTicks); // Llama al renderizado de la clase padre

        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        this.renderScroller(guiGraphics, i, j);

        // Aquí actualizamos cada botón con el estado correspondiente según scrollOff.
        // Por ejemplo, el botón 0 mostrará el estado en states.get(0 + scrollOff),
        // el botón 1 mostrará states.get(1 + scrollOff), etc.
        for (int index = 0; index < stateButtons.length; index++) {
            int listIndex = index + scrollOff;
            if (listIndex < states.size()) {
                DroneState state = states.get(listIndex);
                stateButtons[index].setState(state);
                stateButtons[index].visible = true;
            } else {
                stateButtons[index].visible = false;
            }
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY); // Renderiza cualquier tooltip si es necesario
    }

    private void renderScroller(GuiGraphics pGuiGraphics, int pPosX, int pPosY) {
        int i = states.size() + 1 - 7;
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
        int i = states.size();
        if (this.canScroll(i)) {
            int j = i - 7;
            this.scrollOff = Mth.clamp((int)((double)this.scrollOff - pScrollY), 0, j);
        }

        return true;
    }

    /**
     * Called when the mouse is dragged within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param pMouseX the X coordinate of the mouse.
     * @param pMouseY the Y coordinate of the mouse.
     * @param pButton the button that is being dragged.
     * @param pDragX  the X distance of the drag.
     * @param pDragY  the Y distance of the drag.
     */
    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        int i = states.size();
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

    /**
     * Called when a mouse button is clicked within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param pMouseX the X coordinate of the mouse.
     * @param pMouseY the Y coordinate of the mouse.
     * @param pButton the button that was clicked.
     */
    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        this.isDragging = false;
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        if (this.canScroll(states.size())
                && pMouseX > (double)(i + 63)
                && pMouseX < (double)(i + 63 + 6)
                && pMouseY > (double)(j + 18)
                && pMouseY <= (double)(j + 18 + 139 + 1)) {
            this.isDragging = true;
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }


    /**
     * Renderiza las etiquetas del menú (título y nombre del inventario del jugador).
     *
     * @param guiGraphics Objeto para manejar gráficos GUI.
     * @param mouseX      Posición X del mouse.
     * @param mouseY      Posición Y del mouse.
     */
    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Puedes personalizar las etiquetas aquí. Por ahora, dejaremos vacío.
    }

    /**
     * Inicializa los elementos interactivos del menú.
     */
    @Override
    public void init() {
        super.init();

            int x = this.leftPos + 5;
            int startY = this.topPos + 18;

        for (int i = 0; i < maxStatesCount; i++) {
            int y = startY + i * DroneStateButton.BUTTON_HEIGHT;
            // Se asigna un estado inicial si existe; si no, se asigna null.
            DroneState initialState = (i < states.size()) ? states.get(i) : null;
            DroneStateButton button = new DroneStateButton(x, y, initialState, btn -> {
                // Acción al pulsar el botón: podrías actualizar el estado actual del dron.

                String state = ((DroneStateButton)btn).getState();

                // Enviamos un mensaje al servidor para que cambie el estado
                int droneId = menu.getDrone().getId(); // ID de la entidad en el cliente
                PacketDistributor.sendToServer(new DroneStateMessage(droneId, state));
                System.out.println("Estado seleccionado: " + menu.getDrone().getState());
            });
            stateButtons[i] = button;
            this.addRenderableWidget(button);
        }
    }

}
