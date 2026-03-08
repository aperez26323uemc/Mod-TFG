package com.uemc.assistance_drone.menus;

import com.mojang.datafixers.util.Pair;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.items.SitePlanner;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side container for the drone inventory UI.
 * <p>
 * Exposes the drone storage slots and a dedicated Site Planner slot,
 * enforcing placement rules and distance-based access validation.
 */
public class DroneMenu extends AbstractContainerMenu {

    public static final String ID = ModKeys.DRONE_MENU_KEY;

    private static final int DRONE_INV_ROWS = 3;
    private static final int DRONE_INV_COLS = 4;
    private static final int DRONE_SLOT_COUNT =
            (DRONE_INV_ROWS * DRONE_INV_COLS) + 1;

    static final ResourceLocation EMPTY_SLOT_SITE_PLANNER =
            ResourceLocation.fromNamespaceAndPath(
                    AssistanceDrone.MODID,
                    "item/empty_slot_site_planner"
            );

    private final DroneEntity drone;

    public DroneMenu(
            int id,
            Inventory playerInventory,
            FriendlyByteBuf buffer
    ) {
        super(ModMenus.DRONE_MENU.get(), id);

        if (buffer != null) {
            int droneId = buffer.readVarInt();
            this.drone = (DroneEntity)
                    playerInventory.player.level().getEntity(droneId);
        } else {
            this.drone = null;
        }

        IItemHandler handler =
                this.drone != null
                        ? this.drone.getInventory()
                        : new ItemStackHandler(13);

        initializeSlots(playerInventory, handler);
    }

    /* ------------------------------------------------------------ */
    /* Slot Setup                                                   */
    /* ------------------------------------------------------------ */

    private void initializeSlots(
            Inventory playerInventory,
            IItemHandler handler
    ) {
        // Site Planner slot
        this.addSlot(new SlotItemHandler(handler, 0, 117, 52) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return drone != null
                        && stack.is(ModItems.SITE_PLANNER.get())
                        && SitePlanner.isConfigured(stack);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(
                        InventoryMenu.BLOCK_ATLAS,
                        EMPTY_SLOT_SITE_PLANNER
                );
            }

            @Override
            public boolean mayPickup(Player player) {
                return drone != null;
            }
        });

        // Drone storage slots
        for (int row = 0; row < DRONE_INV_ROWS; row++) {
            for (int col = 0; col < DRONE_INV_COLS; col++) {
                int index = 1 + col + row * DRONE_INV_COLS;

                this.addSlot(new SlotItemHandler(
                        handler,
                        index,
                        177 + col * 18,
                        33 + row * 18
                ) {
                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        return drone != null;
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return drone != null;
                    }
                });
            }
        }

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    /* ------------------------------------------------------------ */
    /* Item Transfer                                                */
    /* ------------------------------------------------------------ */

    @Override
    public @NotNull ItemStack quickMoveStack(
            @NotNull Player player,
            int index
    ) {
        if (this.drone == null) return ItemStack.EMPTY;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < DRONE_SLOT_COUNT) {
                if (!this.moveItemStackTo(
                        stack,
                        DRONE_SLOT_COUNT,
                        this.slots.size(),
                        true
                )) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(
                        stack,
                        0,
                        DRONE_SLOT_COUNT,
                        false
                )) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    /* ------------------------------------------------------------ */
    /* Player Inventory                                             */
    /* ------------------------------------------------------------ */

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(
                        inventory,
                        col + row * 9 + 9,
                        87 + col * 18,
                        108 + row * 18
                ));
            }
        }
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(
                    inventory,
                    col,
                    87 + col * 18,
                    166
            ));
        }
    }

    /* ------------------------------------------------------------ */
    /* Validation                                                   */
    /* ------------------------------------------------------------ */

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.drone != null
                && this.drone.isAlive()
                && player.distanceToSqr(this.drone) <= 64.0;
    }

    public DroneEntity getDrone() {
        return this.drone;
    }
}
