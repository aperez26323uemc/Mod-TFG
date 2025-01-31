package com.uemc.assistance_drone.entities.drone;

import com.uemc.assistance_drone.menus.DroneMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class DroneEntity extends Entity {

    public static final String ID = "drone";

    @NotNull protected String state;

    public final AnimationState bladeAnimation = new AnimationState();

    // Variables para manejar el objetivo de movimiento y mirada
    private Vec3 targetPosition = new Vec3(0, 0, 0); // Objetivo actual (x, y, z)

    // Variables de velocidad para cada eje
    private double velocityX = 0.0;
    private double velocityY = 0.0;
    private double velocityZ = 0.0;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        state = "IDLE";
    }

    public static Supplier<EntityType<DroneEntity>> ENTITY_TYPE_SUPPLIER =
            () -> EntityType.Builder.of(DroneEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.6F)
                    .build(ID);

    @Override
    public void tick() {
        super.tick();
        stateManager();
        performMovementAndLook();
    }

    private void stateManager() {
        if (this.state.equals("OFF")){
            this.bladeAnimation.stop();
            return;
        }
        this.bladeAnimation.startIfStopped(tickCount);
        if(this.state.equals("IDLE")) {
            updateTarget();
        }
    }

    /**
     * Actualiza el objetivo del dron basado en la lógica actual.
     * Puedes expandir esta lógica para incluir diferentes estados o condiciones.
     */
    private void updateTarget() {
        double floorY = findFloorWithRaycast(this.getX(), this.getY(), this.getZ());
        double maxY = findCeilingWithRaycast(this.getX(), this.getY(), this.getZ()) - 0.75;

        double desiredY = floorY + 1.5;
        // Mantener la misma X y Z
        double targetX = this.targetPosition.x;
        double targetZ = this.targetPosition.z;
        // Si hay jugadores cerca, intentar igualar la altura de sus ojos
        double radius = 8.0;
        Player nearestPlayer = this.level().getNearestPlayer(this, radius);
        if (nearestPlayer != null) {
            desiredY = nearestPlayer.getEyeY();
            targetX = nearestPlayer.getX();
            targetZ = nearestPlayer.getZ();
        }
        double targetYFinal = Math.clamp(desiredY, Math.min(floorY + 1.0, maxY), maxY);

        setTargetPosition(targetX, targetYFinal, targetZ);
    }

    /**
     * Establece el objetivo del dron.
     *
     * @param x Coordenada X del objetivo
     * @param y Coordenada Y del objetivo
     * @param z Coordenada Z del objetivo
     */
    public void setTargetPosition(double x, double y, double z) {
        this.targetPosition = new Vec3(x, y, z);
    }

    /**
     * Realiza el movimiento y la rotación hacia el objetivo.
     */
    private void performMovementAndLook() {
        moveToPos(targetPosition, false);
        lookAtPos(targetPosition);
    }

    /**
     * Gira el dron para mirar hacia una posición específica.
     *
     * @param targetPos Objeto Vec3 que representa la posición del objetivo
     */
    public void lookAtPos(Vec3 targetPos) {
        // Vector desde el dron hasta el objetivo
        double dx = targetPos.x - this.getX();
        double dz = targetPos.z - this.getZ();
        double dy = targetPos.y - this.getY();

        // Calcular yaw (rotación horizontal)
        float desiredYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;

        // Calcular pitch (rotación vertical), limitada a ±5 grados
        float horizontalDist = (float)Math.sqrt(dx * dx + dz * dz);
        float desiredPitch = (float)(-Math.atan2(dy, horizontalDist) * (180.0 / Math.PI));
        float maxPitch = 5.0F;
        desiredPitch = Mth.clamp(desiredPitch, -maxPitch, maxPitch);

        // Asignar rotaciones a la entidad
        this.setYRot(desiredYaw);
        this.setXRot(desiredPitch);
    }

    public void moveToPos(Vec3 targetPos, boolean allowXZ) {
        double desiredX = targetPos.x;
        double desiredY = targetPos.y;
        double desiredZ = targetPos.z;

        // Calcular diferencias solo si el movimiento está permitido en el eje
        double deltaX = allowXZ ? desiredX - this.getX() : 0.0;
        double deltaY = desiredY - this.getY(); // Movimiento vertical siempre permitido
        double deltaZ = allowXZ ? desiredZ - this.getZ() : 0.0;

        double k = 0.4; // Constante del resorte
        double d = 0.4; // Constante de amortiguación
        float dt = 1.0F / 20.0F; // Tiempo por tick (20 ticks por segundo)

        // Calcular aceleración por eje, solo si el movimiento está permitido
        double accelerationX = allowXZ ? (k * deltaX - d * this.velocityX) : 0.0;
        double accelerationZ = allowXZ ? (k * deltaZ - d * this.velocityZ) : 0.0;
        double accelerationY = k * deltaY - d * this.velocityY; // Movimiento vertical siempre permitido

        // Actualizar velocidades
        if (allowXZ) {
            this.velocityX += accelerationX * dt;
            this.velocityX = Mth.clamp(this.velocityX, -0.15, 0.15);
            this.velocityZ += accelerationZ * dt;
            this.velocityZ = Mth.clamp(this.velocityZ, -0.15, 0.15);
        } else {
            this.velocityX = 0.0;
            this.velocityZ = 0.0;
        }

        this.velocityY += accelerationY * dt;
        this.velocityY = Mth.clamp(this.velocityY, -0.15, 0.15);

        // Calcular movimiento por eje
        double moveX = this.velocityX;
        double moveY = this.velocityY;
        double moveZ = this.velocityZ;

        // Establecer la nueva velocidad
        this.setDeltaMovement(moveX, moveY, moveZ);

        // Mover la entidad
        this.move(MoverType.SELF, this.getDeltaMovement());
    }


    /**
     * Raycast vertical hacia abajo para encontrar el primer bloque sólido.
     * Retorna la posición exacta de impacto (Y). Si no impacta, retorna minBuildHeight().
     */
    private double findFloorWithRaycast(double x, double y, double z) {
        double rayLength = 10.0; // cuánto se extiende el rayo
        Vec3 from = new Vec3(x, y, z);
        Vec3 to   = new Vec3(x, y - rayLength, z);

        ClipContext ctx = new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        );

        BlockHitResult result = this.level().clip(ctx);
        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getLocation().y;
        }
        return this.level().getMinBuildHeight();
    }

    /**
     * Raycast vertical hacia arriba para encontrar el primer bloque sólido.
     * Retorna la posición exacta de impacto (Y). Si no impacta, retorna maxBuildHeight().
     */
    private double findCeilingWithRaycast(double x, double y, double z) {
        double rayLength = 10.0;
        Vec3 from = new Vec3(x, y, z);
        Vec3 to   = new Vec3(x, y + rayLength, z);

        ClipContext ctx = new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        );

        BlockHitResult result = this.level().clip(ctx);
        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getLocation().y;
        }
        return this.level().getMaxBuildHeight();
    }

    /**
     * Makes the drone interactable
     * @return true
     */
    @Override
    public boolean isPickable(){
        return true;
    }

    /**
     * Reemplaza la lógica de interacción para abrir el menú del dron.
     *
     * @param player El jugador que interactúa
     * @param vec La posición de interacción
     * @param hand La mano usada para interactuar
     * @return El resultado de la interacción
     */
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

    // Data Synched
    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        // Aquí puedes definir datos sincronizados si lo necesitas
    }
}
