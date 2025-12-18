package com.uemc.assistance_drone.menus;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler; // IMPORTANTE
import org.jetbrains.annotations.NotNull;

public class DroneMenu extends AbstractContainerMenu {
    public final static String ID = "drone_menu";
    private final static int ROWS = 3;
    private final static int COLUMNS = 4;
    private final DroneEntity drone;

    public DroneMenu(int id, Inventory playerInventory, FriendlyByteBuf packetBuffer) {
        super(ModMenus.DRONE_MENU.get(), id);

        int droneId = packetBuffer.readVarInt();
        this.drone = (DroneEntity) playerInventory.player.level().getEntity(droneId);

        if (drone == null) return;

        for (int row = 0; row < ROWS; ++row) {
            for (int col = 0; col < COLUMNS; ++col) {
                this.addSlot(new SlotItemHandler(this.drone.getInventory(), col + row * COLUMNS, 167 + col * 18, 18 + row * 18));
            }
        }

        // Inventario del Jugador (Sigue siendo Slot normal)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 77 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 77 + col * 18, 142));
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player pPlayer, int pIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(pIndex);
        if (slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (pIndex < ROWS*COLUMNS) {
                if (!this.moveItemStackTo(itemstack1, ROWS*COLUMNS, this.slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, ROWS*COLUMNS, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.drone.isAlive() && player.distanceToSqr(this.drone) <= 64.0;
    }

    public DroneEntity getDrone() {
        return this.drone;
    }
}