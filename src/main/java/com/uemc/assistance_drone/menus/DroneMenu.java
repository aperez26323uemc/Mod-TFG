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
 * Container menu for the drone's inventory interface.
 * <p>
 * Provides access to the drone's storage slots and a dedicated slot for the Site Planner item.
 * The menu validates Site Planner placement and handles item transfer between a player and drone inventories.
 * </p>
 *
 * @see DroneEntity
 * @see SitePlanner
 */
public class DroneMenu extends AbstractContainerMenu {
    public final static String ID = ModKeys.DRONE_MENU_KEY;

    private static final int DRONE_INV_ROWS = 3;
    private static final int DRONE_INV_COLS = 4;
    private static final int DRONE_SLOT_COUNT = (DRONE_INV_ROWS * DRONE_INV_COLS) + 1;

    static final ResourceLocation EMPTY_SLOT_SITE_PLANNER =
            ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "item/empty_slot_site_planner");

    private final DroneEntity drone;

    /**
     * Constructs a drone menu from network data.
     * <p>
     * This constructor is called on both client and server sides. On the server side,
     * the packet buffer contains the drone's entity ID. On the client side, the buffer
     * may be null during initial creation, and the menu will be synchronized later.
     * </p>
     *
     * @param id the menu window ID
     * @param playerInventory the player's inventory
     * @param packetBuffer network buffer containing drone entity ID (may be null on client)
     */
    public DroneMenu(int id, Inventory playerInventory, FriendlyByteBuf packetBuffer) {
        super(ModMenus.DRONE_MENU.get(), id);

        if (packetBuffer != null) {
            int droneId = packetBuffer.readVarInt();
            this.drone = (DroneEntity) playerInventory.player.level().getEntity(droneId);
        } else {
            this.drone = null;
        }

        IItemHandler handler = (this.drone != null) ?
                this.drone.getInventory() : new ItemStackHandler(13);

        initializeSlots(playerInventory, handler);
    }

    /**
     * Initializes all container slots including drone inventory and player inventory.
     *
     * @param playerInventory the player's inventory to link
     * @param handler the item handler (real or dummy) to use for slots
     */
    private void initializeSlots(Inventory playerInventory, IItemHandler handler) {
        // Site Planner slot with validation
        this.addSlot(new SlotItemHandler(handler, 0, 117, 52){
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return drone != null && stack.is(ModItems.SITE_PLANNER.get()) && SitePlanner.isConfigured(stack);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, DroneMenu.EMPTY_SLOT_SITE_PLANNER);
            }

            @Override
            public boolean mayPickup(Player playerIn) {
                return drone != null;
            }
        });

        // Drone storage grid
        for (int row = 0; row < DRONE_INV_ROWS; ++row) {
            for (int col = 0; col < DRONE_INV_COLS; ++col) {
                int index = 1 + (col + row * DRONE_INV_COLS);
                this.addSlot(new SlotItemHandler(handler, index, 177 + col * 18, 33 + row * 18) {
                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        return drone != null;
                    }

                    @Override
                    public boolean mayPickup(Player playerIn) {
                        return drone != null;
                    }
                });
            }
        }

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    /**
     * Handles shift-click item transfer between a player and drone inventories.
     *
     * @param player the player performing the shift-click
     * @param index the slot index being clicked
     * @return the remaining item stack after transfer
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        if (this.drone == null) return ItemStack.EMPTY;

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index < DRONE_SLOT_COUNT) {
                // Drone to Player
                if (!this.moveItemStackTo(itemstack1, DRONE_SLOT_COUNT, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Player to Drone
                if (!this.moveItemStackTo(itemstack1, 0, DRONE_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    /**
     * Adds player main inventory slots (27 slots in 3 rows).
     */
    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 87 + col * 18, 108 + row * 18));
            }
        }
    }

    /**
     * Adds player hotbar slots (9 slots).
     */
    private void addPlayerHotbar(Inventory playerInventory) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 87 + col * 18, 166));
        }
    }

    /**
     * Validates if the menu can still be accessed by the player.
     * <p>
     * The menu remains valid while the drone exists, is alive, and is within 8 blocks of the player.
     * </p>
     *
     * @param player the player attempting to access the menu
     * @return true if the menu is still accessible
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.drone != null && this.drone.isAlive() && player.distanceToSqr(this.drone) <= 64.0;
    }

    /**
     * Gets the drone entity associated with this menu.
     *
     * @return the drone entity, or null if not yet synchronized
     */
    public DroneEntity getDrone() {
        return this.drone;
    }
}