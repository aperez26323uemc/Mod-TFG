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
 * Client-side screen for drone inventory and state selection.
 * <p>
 * Displays a scrollable list of available drone states, dynamically filtered
 * based on the drone's current conditions.
 */
@OnlyIn(Dist.CLIENT)
public class DroneMenuScreen extends AbstractContainerScreen<DroneMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    AssistanceDrone.MODID,
                    "textures/screens/drone_screen.png"
            );

    private static final ResourceLocation SCROLLER_SPRITE =
            ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE =
            ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");

    private static final int MAX_VISIBLE_STATES = 7;

    private final List<DroneGoalRegistry.StateDefinition> allStates = new ArrayList<>();
    private List<DroneGoalRegistry.StateDefinition> visibleStates = new ArrayList<>();
    private final DroneStateButton[] stateButtons =
            new DroneStateButton[MAX_VISIBLE_STATES];

    private int scrollOff;
    private boolean isDragging;

    public DroneMenuScreen(
            DroneMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 191;

        this.allStates.addAll(DroneGoalRegistry.getDefinitions());
        this.visibleStates.addAll(this.allStates);
    }

    /* ------------------------------------------------------------ */
    /* Rendering                                                    */
    /* ------------------------------------------------------------ */

    @Override
    protected void renderBg(
            GuiGraphics guiGraphics,
            float partialTicks,
            int gx,
            int gy
    ) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(
                TEXTURE,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                this.imageHeight
        );
    }

    @Override
    public void render(
            @NotNull GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
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

    private void renderScrollbar(GuiGraphics guiGraphics) {
        int total = visibleStates.size();
        int overflow = total + 1 - MAX_VISIBLE_STATES;

        if (overflow > 1) {
            int barHeight = 139 - (27 + (overflow - 1) * 139 / overflow);
            int step = 1 + barHeight / overflow + 139 / overflow;
            int y = Math.min(113, this.scrollOff * step);

            if (this.scrollOff == overflow - 1) {
                y = 113;
            }

            guiGraphics.blitSprite(
                    SCROLLER_SPRITE,
                    this.leftPos + 67,
                    this.topPos + 33 + y,
                    0,
                    6,
                    27
            );
        } else {
            guiGraphics.blitSprite(
                    SCROLLER_DISABLED_SPRITE,
                    this.leftPos + 67,
                    this.topPos + 33,
                    0,
                    6,
                    27
            );
        }
    }

    private void updateStateButtons() {
        String currentState =
                this.menu.getDrone() != null ? this.menu.getDrone().getState() : "";

        for (int i = 0; i < stateButtons.length; i++) {
            int listIndex = i + scrollOff;
            DroneStateButton button = stateButtons[i];

            if (listIndex < visibleStates.size()) {
                DroneGoalRegistry.StateDefinition def =
                        visibleStates.get(listIndex);

                button.updateState(
                        def.id(),
                        def.getLabel(),
                        def.getTooltip()
                );
                button.visible = true;
                button.active = !def.id().equals(currentState);
            } else {
                button.updateState(
                        null,
                        CommonComponents.EMPTY,
                        CommonComponents.EMPTY
                );
                button.visible = false;
            }
        }
    }

    /* ------------------------------------------------------------ */
    /* Input Handling                                               */
    /* ------------------------------------------------------------ */

    private boolean canScroll(int count) {
        return count > MAX_VISIBLE_STATES;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        int count = visibleStates.size();
        if (canScroll(count)) {
            int max = count - MAX_VISIBLE_STATES;
            this.scrollOff = Mth.clamp(
                    (int) (this.scrollOff - scrollY),
                    0,
                    max
            );
        }
        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (this.isDragging) {
            int top = this.topPos + 18;
            int bottom = top + 139;
            int max = visibleStates.size() - MAX_VISIBLE_STATES;

            float factor =
                    ((float) mouseY - top - 13.5F) /
                            ((bottom - top) - 27.0F);

            this.scrollOff = Mth.clamp(
                    (int) (factor * max + 0.5F),
                    0,
                    max
            );
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        this.isDragging = false;

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        if (canScroll(visibleStates.size())
                && mouseX > left + 63
                && mouseX < left + 69
                && mouseY > top + 18
                && mouseY <= top + 158) {
            this.isDragging = true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /* ------------------------------------------------------------ */
    /* Labels & Init                                                */
    /* ------------------------------------------------------------ */

    @Override
    protected void renderLabels(
            @NotNull GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {
        int color = 0x404040;

        if (this.menu.getDrone() != null) {
            guiGraphics.drawString(
                    this.font,
                    this.menu.getDrone().getDisplayName(),
                    8, 6,
                    color,
                    false
            );
        }

        guiGraphics.drawString(
                this.font,
                Component.translatable(ModKeys.GUI_DRONE_MENU_MODES_LABEL),
                10, 20,
                color,
                false
        );

        guiGraphics.drawString(
                this.font,
                ModItems.SITE_PLANNER.get().getDescription(),
                88, 20,
                color,
                false
        );

        guiGraphics.drawString(
                this.font,
                Component.translatable(ModKeys.GUI_DRONE_MENU_STORAGE_LABEL),
                178, 20,
                color,
                false
        );

        guiGraphics.drawString(
                this.font,
                this.playerInventoryTitle,
                88, 95,
                color,
                false
        );
    }

    @Override
    public void init() {
        super.init();

        GridLayout layout = new GridLayout();

        for (int i = 0; i < MAX_VISIBLE_STATES; i++) {
            stateButtons[i] = new DroneStateButton(
                    0, 0,
                    null,
                    CommonComponents.EMPTY,
                    CommonComponents.EMPTY,
                    btn -> {
                        DroneStateButton stateBtn = (DroneStateButton) btn;
                        if (stateBtn.getStateId() != null) {
                            sendStateChange(stateBtn.getStateId());
                        }
                    }
            );
            layout.addChild(stateButtons[i], i, 0);
        }

        layout.arrangeElements();
        layout.setPosition(this.leftPos + 9, this.topPos + 33);
        layout.visitWidgets(this::addRenderableWidget);
    }

    /* ------------------------------------------------------------ */
    /* Networking                                                   */
    /* ------------------------------------------------------------ */

    private void sendStateChange(String stateId) {
        if (this.menu.getDrone() == null) return;

        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new DroneStateMessage(
                        this.menu.getDrone().getId(),
                        stateId
                )
        );
    }

    /* ------------------------------------------------------------ */
    /* Tooltips                                                     */
    /* ------------------------------------------------------------ */

    @Override
    protected void renderTooltip(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {
        if (this.hoveredSlot != null
                && !this.hoveredSlot.hasItem()
                && this.hoveredSlot.index == 0) {

            guiGraphics.renderTooltip(
                    this.font,
                    Component.translatable(
                            ModKeys.GUI_SITE_PLANNER_SLOT_HINT
                    ),
                    mouseX,
                    mouseY
            );
            return;
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
