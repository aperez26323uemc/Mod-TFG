package com.uemc.assistance_drone.menus;

import com.mojang.datafixers.util.Pair;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.util.ModKeys;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class DroneMenu extends AbstractContainerMenu {
    public final static String ID = ModKeys.DRONE_MENU_KEY;
    private final DroneEntity drone;
    static final ResourceLocation EMPTY_SLOT_SITE_PLANNER =
            ResourceLocation.fromNamespaceAndPath(
                    AssistanceDrone.MODID,
                    "item/empty_slot_site_planner");

    // Define constants for slot counting to make quickMoveStack easier
    private static final int DRONE_INV_ROWS = 3;
    private static final int DRONE_INV_COLS = 4;
    private static final int DRONE_SLOT_COUNT = (DRONE_INV_ROWS * DRONE_INV_COLS) + 1; // +1 for Site Planner

    public DroneMenu(int id, Inventory playerInventory, FriendlyByteBuf packetBuffer) {
        super(ModMenus.DRONE_MENU.get(), id);

        int droneId = packetBuffer.readVarInt();
        this.drone = (DroneEntity) playerInventory.player.level().getEntity(droneId);

        if (this.drone == null) return;

        // 1. Site Planner Slot (Index 0)
        this.addSlot(new SlotItemHandler(this.drone.getInventory(), 0, 117, 52){
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.SITE_PLANNER);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, DroneMenu.EMPTY_SLOT_SITE_PLANNER);
            }
        });

        // 2. Drone Inventory (Starting at Index 1)
        for (int row = 0; row < DRONE_INV_ROWS; ++row) {
            for (int col = 0; col < DRONE_INV_COLS; ++col) {
                int index = 1 + (col + row * DRONE_INV_COLS);
                this.addSlot(new SlotItemHandler(this.drone.getInventory(), index, 177 + col * 18, 33 + row * 18));
            }
        }

        // 3. Player Inventory
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            // From Drone to Player
            if (index < DRONE_SLOT_COUNT) {
                if (!this.moveItemStackTo(itemstack1, DRONE_SLOT_COUNT, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            }
            // From Player to Drone
            else {
                // Try to put in drone inventory (indices 0 to DRONE_SLOT_COUNT)
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

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 87 + col * 18, 108 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 87 + col * 18, 166));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.drone != null && this.drone.isAlive() && player.distanceToSqr(this.drone) <= 64.0;
    }

    public DroneEntity getDrone() {
        return this.drone;
    }
}