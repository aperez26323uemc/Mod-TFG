package com.uemc.assistance_drone.menus;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;


public class DroneMenu extends AbstractContainerMenu {
    public final static String ID = "drone_menu";
    private final static int ROWS = 3;
    private final static int COLUMNS = 4;
    private final DroneEntity drone;

    public DroneMenu(int id, Inventory playerInventory, FriendlyByteBuf packetBuffer) {
        super(ModMenus.DRONE_MENU.get(), id);

        // Obtener el inventario del dron a partir del ID de la entidad
        int droneId = packetBuffer.readVarInt();
        this.drone = (DroneEntity) playerInventory.player.level().getEntity(droneId);

        if (drone == null) {
            return;
        }

        // Añadir los slots del dron
        for (int row = 0; row < ROWS; ++row) {
            for (int col = 0; col < COLUMNS; ++col) {
                this.addSlot(new Slot(this.drone, col + row * COLUMNS, 167 + col * 18, 18 + row * 18));
            }
        }

        // Añadir las ranuras del inventario del jugador
        // Inventario superior
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 77 + col * 18, 84 + row * 18));
            }
        }
        // Inventario inferior (hotbar)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 77 + col * 18, 142));
        }
    }

    /**
     * Handle when the stack in slot {@code pIndex} is shift-clicked. Normally this moves the stack between the player inventory and the other inventory(s).
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player pPlayer, int pIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(pIndex);
        if (slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (pIndex < ROWS*COLUMNS) {
                // Del dron al inventario
                if (!this.moveItemStackTo(itemstack1, ROWS*COLUMNS, this.slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
                // Del inventario al dron
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

    /**
     * Verifica si el menú sigue siendo válido para el jugador.
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.drone.stillValid(player);
    }

    public DroneEntity getDrone() {
        return this.drone;
    }

}
