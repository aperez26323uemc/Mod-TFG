package com.uemc.assistance_drone.entities.drone;

import com.uemc.assistance_drone.menus.DroneMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.items.wrapper.EntityArmorInvWrapper;
import net.neoforged.neoforge.items.wrapper.EntityHandsInvWrapper;
import org.jetbrains.annotations.NotNull;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.function.Supplier;

public class DroneEntity extends Entity {

    public static String ID = "drone";

    @NotNull protected String state;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        state = "IDLE";
    }

    public static Supplier<EntityType<DroneEntity>> ENTITY_TYPE_SUPPLIER =
            () -> EntityType.Builder.of(DroneEntity::new, MobCategory.MISC)
            .sized(0.7F, 0.7F)
            .build(ID);

    @Override
    public void tick() {
        super.tick();
    }

    /**
     * Makes the drone interactable
     * @return true
     */
    @Override
    public boolean isPickable(){
        return true;
    }

    @Override
    public @NotNull InteractionResult interactAt(@NotNull Player player, @NotNull Vec3 vec, @NotNull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        InteractionResult retval = InteractionResult.sidedSuccess(this.level().isClientSide());
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.literal("Assistance Drone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    packetBuffer.writeBlockPos(player.blockPosition());
                    packetBuffer.writeByte(0);
                    packetBuffer.writeVarInt(DroneEntity.this.getId());
                    return new DroneMenu(id, inventory, packetBuffer);
                }
            }, buf -> {
                buf.writeBlockPos(player.blockPosition());
                buf.writeByte(0);
                buf.writeVarInt(this.getId());
            });
        }
        super.interactAt(player, vec, hand);
        return retval;
    }

    // See the Data and Networking article for information about these methods.
    private final ItemStackHandler inventory = new ItemStackHandler(0) {
        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    };

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        compound.put("InventoryCustom", inventory.serializeNBT(this.registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        if (compound.get("InventoryCustom") instanceof CompoundTag inventoryTag)
            inventory.deserializeNBT(this.registryAccess(), inventoryTag);
    }


    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
    }
}