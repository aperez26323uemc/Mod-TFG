package com.uemc.assistance_drone.entities.drone;

import com.uemc.assistance_drone.menus.DroneMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class DroneEntity extends Entity implements ContainerEntity {

    public static final String ID = "drone";
    private static final float DRONE_WIDTH = 0.7F;
    private static final float DRONE_HEIGHT = 0.6F;

    /// Supplier utilizado para registrar la entidad
    public static Supplier<EntityType<DroneEntity>> ENTITY_TYPE_SUPPLIER =
            () -> EntityType.Builder.of(DroneEntity::new, MobCategory.MISC)
                    .sized(DRONE_WIDTH, DRONE_HEIGHT)
                    .build(ID);

    /// Constructor
    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        DroneStateList.registerState(new DroneState("IDLE"));
        DroneStateList.registerState(new DroneState("FOLLOW"));
        this.blocksBuilding = true;
        this.noPhysics = false;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    /// Datos síncronos ////////////////////////////////////////

    private static final EntityDataAccessor<String> STATE =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private NonNullList<ItemStack> itemStacks = NonNullList.withSize(12, ItemStack.EMPTY);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, "IDLE")
               .define(OWNER, Optional.empty());
    }

    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public Player getOwner() {
        UUID uuid = getOwnerUUID();
        return uuid != null ? level().getPlayerByUUID(uuid) : null;
    }

    public void setOwner(Player player) {
        this.entityData.set(OWNER, Optional.of(player.getUUID()));
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        compound.putString("State", this.getState());
        addChestVehicleSaveData(compound, this.registryAccess());
        if (getOwnerUUID() != null) {
            compound.putUUID("Owner", getOwnerUUID());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        if (compound.contains("State", 8)) { // 8 es el tipo para String
            String savedState = compound.getString("State");
            this.setState(savedState);
        }
        if (compound.hasUUID("Owner")) {
            this.entityData.set(OWNER, Optional.of(compound.getUUID("Owner")));
        }
        readChestVehicleSaveData(compound, this.registryAccess());
    }

    public String getState() {
        return this.entityData.get(STATE);
    }

    public void setState(String newState) {
        this.getEntityData().set(STATE, newState);
    }


    ///Animaciones ////////////////////////////////////////
    public final AnimationState bladeAnimation = new AnimationState();


    /// Variables para manejar el movimiento del dron
    private Vec3 targetPosition = new Vec3(0, 0, 0);
    public void setTargetPosition(double x, double y, double z) {
        this.targetPosition = new Vec3(x, y, z);
    }
    private static final double MAX_HORIZONTAL_SPEED = 0.3;
    private static final double MAX_VERTICAL_SPEED = 0.15;
    private List<BlockPos> currentPath = new ArrayList<>();
    private int currentPathIndex = 0;
    private static final int PATH_UPDATE_INTERVAL = 20;
    private int pathUpdateTimer = 0;


    /// Funcionalidad ////////////////////////////////////////

    @Override
    public void tick() {
        pushEntities();
        stateManager();
        interpolatePosRot();
        super.tick();
    }

    protected void pushEntities() {
        List<Entity> list = this.level().getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this));
        for (Entity entity1 : list) {
            this.push(entity1);
        }
    }

    /**
     * DO NOT OVERWRITE (Inject only at tail)
     * <p>
     * Maneja los estados y el comportamiento
     */
    private void stateManager() {
        if(!DroneStateList.isValidState(this.getState())) {
            this.setState("IDLE");
        }
        this.bladeAnimation.startIfStopped(tickCount);
        if(this.getState().equals("IDLE")) {
            if(!level().isClientSide()) {
                updateTarget();
                moveToPos(targetPosition, false, true);
                lookAtPos(targetPosition);
            }
        }
        if (this.getState().equals("FOLLOW")) {
            if(!level().isClientSide()){
                updateFollowTarget();
                updatePath();
                followPath();
                lookAtPos(targetPosition);
            }
        }
    }


    /// CLIENT INTERPOLATION ///

    @OnlyIn(Dist.CLIENT)
    private int lerpSteps = 0;
    @OnlyIn(Dist.CLIENT)
    private double lerpX, lerpY, lerpZ;
    @OnlyIn(Dist.CLIENT)
    private float lerpYRot, lerpXRot;

    @OnlyIn(Dist.CLIENT)
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // When a network packet arrives, store the target state
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps;
    }

    @OnlyIn(Dist.CLIENT)
    private void interpolatePosRot() {
        // Only run interpolation on the events side
        if (this.level().isClientSide && lerpSteps > 0) {
            // Calculate the fraction for this tick.
            double fraction = 1.0 / (double) lerpSteps;

            // Interpolate positions.
            double newX = Mth.lerp(fraction, this.getX(), lerpX);
            double newY = Mth.lerp(fraction, this.getY(), lerpY);
            double newZ = Mth.lerp(fraction, this.getZ(), lerpZ);

            // For rotations, use the rotLerp helper to correctly wrap angles.
            float newYRot = Mth.rotLerp((float) fraction, this.getYRot(), lerpYRot);
            float newXRot = Mth.rotLerp((float) fraction, this.getXRot(), lerpXRot);

            // Update the entity's position and rotation.
            this.setPos(newX, newY, newZ);
            this.setYRot(newYRot);
            this.setXRot(newXRot);

            // Decrement the interpolation counter.
            lerpSteps--;
        }
    }

    /// COMMON MOVEMENT ///

    private void lookAtPos(Vec3 targetPos) {
        // Vector desde el dron hasta el objetivo
        double dx = targetPos.x - this.getX();
        double dz = targetPos.z - this.getZ();

        // Calcular yaw (rotación horizontal)
        float desiredYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90F;
        // Se mantiene el offset de 90° como se requiere

        // Interpolación suave del yaw:
        float currentYaw = this.getYRot();
        float angleDifference = Mth.wrapDegrees(desiredYaw - currentYaw);
        float smoothFactor = 0.5F;
        float newYaw = currentYaw + smoothFactor * angleDifference;

        // Asignar la rotación suavizada en lugar de la directa
        this.setYRot(newYaw);
    }

    private void moveToPos(Vec3 targetPos, boolean allowXZ, boolean allowBounce) {
        Vec3 newVelocity = calculateVelocity(targetPos, allowXZ, allowBounce);
        setDeltaMovement(newVelocity);
        move(MoverType.SELF, getDeltaMovement());
    }

    private Vec3 calculateVelocity(Vec3 targetPos, boolean allowXZ, boolean allowBounce) {
        if (allowBounce) {}
            return calculateBounceVelocity(targetPos, allowXZ);
        /*} else {
            return calculateSmoothVelocity(targetPos, allowXZ);
        }*/
    }

    private Vec3 calculateBounceVelocity(Vec3 targetPos, boolean allowXZ) {
        double dt = 1.0 / 20.0;
        Vec3 currentVel = getDeltaMovement();

        double dx = allowXZ ? targetPos.x - getX() : 0.0;
        double dz = allowXZ ? targetPos.z - getZ() : 0.0;
        double dy = targetPos.y - getY();

        // PD Controller for bounce mode
        double k = 0.2, d = 0.6;
        double ax = allowXZ ? (k * dx - d * 1.5 * currentVel.x) : 0.0;
        double az = allowXZ ? (k * dz - d * 1.5 * currentVel.z) : 0.0;
        double ay = k * dy - d * currentVel.y;

        Vec3 acceleration = new Vec3(ax, ay, az);
        Vec3 newVelocity = currentVel.add(acceleration.scale(dt));

        // Clamp velocities
        return new Vec3(
                allowXZ ? Mth.clamp(newVelocity.x, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED) : 0.0,
                Mth.clamp(newVelocity.y, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED),
                allowXZ ? Mth.clamp(newVelocity.z, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED) : 0.0
        );
    }

    /*private Vec3 calculateSmoothVelocity(Vec3 targetPos, boolean allowXZ) {
        double horizontalGain = 0.3;
        double verticalGain = 0.1;

        double dx = allowXZ ? targetPos.x - getX() : 0.0;
        double dz = allowXZ ? targetPos.z - getZ() : 0.0;
        double dy = targetPos.y - getY();

        double desiredVx = allowXZ ? dx * horizontalGain : 0.0;
        double desiredVz = allowXZ ? dz * horizontalGain : 0.0;
        double desiredVy = dy * verticalGain;

        return new Vec3(
                Mth.clamp(desiredVx, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED),
                Mth.clamp(desiredVy, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED),
                Mth.clamp(desiredVz, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED)
        );
    }*/

    private double verticalRaycast(double x, double y, double z, boolean upwards) {
        double rayLength = 10.0;
        Vec3 from = new Vec3(x, y, z);
        Vec3 to = new Vec3(x, upwards ? y + rayLength : y - rayLength, z);

        BlockHitResult result = level().clip(new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY,
                this
        ));

        return result.getType() == HitResult.Type.BLOCK
                ? result.getLocation().y
                : (upwards ? level().getMaxBuildHeight() : level().getMinBuildHeight());
    }


    /// STATE IDLE ///

    private void updateTarget() {
        double floorY = verticalRaycast(this.getX(), this.getY(), this.getZ(), false);
        double maxY = verticalRaycast(this.getX(), this.getY(), this.getZ(), true) - 0.75;

        double desiredY = floorY + 1.5;
        // Mantener la misma X y Z
        double targetX = this.targetPosition.x;
        double targetZ = this.targetPosition.z;
        // Si hay un jugador cerca, le fija como target
        double radius = 8.0;
        Player nearestPlayer = this.level().getNearestPlayer(this, radius);
        if (nearestPlayer != null) {
            desiredY = nearestPlayer.getEyeY();
            targetX = nearestPlayer.getX();
            targetZ = nearestPlayer.getZ();
        }
        double targetYFinal = Mth.clamp(desiredY, Math.min(floorY + 1.0, maxY), maxY);

        setTargetPosition(targetX, targetYFinal, targetZ);
    }


    /// STATE FOLLOW ///

    private void updateFollowTarget() {
        double targetX = getOwner().getX();
        double targetZ = getOwner().getZ();
        double desiredY = getOwner().getEyeY();

        double floorY = verticalRaycast(targetX, desiredY, targetZ, false);
        double maxY = verticalRaycast(targetX, desiredY, targetZ, true) - 0.75;

        // Mantener la misma X y Z
        double targetYFinal = Mth.clamp(desiredY, Math.min(floorY + 1.0, maxY), maxY);

        setTargetPosition(targetX, targetYFinal, targetZ);
    }

    private void updatePath() {
        if (pathUpdateTimer-- <= 0) {
            BlockPos targetPos = new BlockPos(
                    Mth.floor(targetPosition.x),
                    Mth.floor(targetPosition.y),
                    Mth.floor(targetPosition.z)
            );

            DronePathFinder pathfinder = new DronePathFinder(
                    this.level(),
                    this.blockPosition(),
                    targetPos
            );

            currentPath = pathfinder.findPath();
            currentPathIndex = 0;
            pathUpdateTimer = PATH_UPDATE_INTERVAL;

        }
    }

    private void followPath() {
        if (currentPath.isEmpty() || currentPathIndex >= currentPath.size()) return;

        BlockPos nextPos = getFarthestReachable();
        if (nextPos != null) {
            Vec3 target = new Vec3(
                    nextPos.getX() + 0.5,
                    nextPos.getY() + 0.4,
                    nextPos.getZ() + 0.5
            );

            moveToPos(target, true, true);

            if (this.position().distanceToSqr(target) < 1.5) {
                currentPathIndex = Math.min(currentPathIndex + 1, currentPath.size() - 1);
            }
        }
    }

    private BlockPos getFarthestReachable() {
        for (int i = currentPath.size() - 1; i >= currentPathIndex; i--) {
            BlockPos candidate = currentPath.get(i);
            if (isDirectPathClear(this.blockPosition(), candidate)) {
                currentPathIndex = i;
                return candidate;
            }
        }
        return currentPath.get(currentPathIndex);
    }

    private boolean isDirectPathClear(BlockPos start, BlockPos end) {
        ClipContext context = new ClipContext(
                new Vec3(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5),
                new Vec3(end.getX() + 0.5, end.getY() + 0.5, end.getZ() + 0.5),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        );

        return level().clip(context).getType() == HitResult.Type.MISS;
    }

    /// Lógica de interacción ////////////////////////////////////////

    /**
     * Makes the drone interactable
     */
    @Override
    public boolean isPickable(){
        return true;
    }

    /**
     * Reemplaza la lógica de interacción para abrir el menú del dron.
     *
     * @param player El jugador que interactúa
     * @param hand La mano usada para interactuar
     * @return El resultado de la interacción
     */
    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if(!this.level().isClientSide()){
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(this, buf -> {
                    buf.writeVarInt(this.getId());
                });
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }


    /// Container ////////////////////////////////////////

    @Override
    public @Nullable ResourceKey<LootTable> getLootTable() {
        return null;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> pLootTable) {
    }

    @Override
    public long getLootTableSeed() {
        return 0;
    }

    @Override
    public void setLootTableSeed(long pLootTableSeed) {
    }

    @Override
    public NonNullList<ItemStack> getItemStacks() {
        return this.itemStacks;
    }

    @Override
    public void clearItemStacks() {
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public int getContainerSize() {
        return 12;
    }

    @Override
    public int getMaxStackSize() {
        return 8;
    }

    @Override
    public ItemStack getItem(int pSlot) {
        return this.getItemStacks().get(pSlot);
    }

    /**
     * Removes up to a specified number of items from an inventory slot and returns them in a new stack.
     */
    @Override
    public ItemStack removeItem(int pSlot, int pAmount) {
        return ContainerHelper.removeItem(this.getItemStacks(), pSlot, pAmount);
    }

    /**
     * Removes a stack from the given slot and returns it.
     */
    @Override
    public ItemStack removeItemNoUpdate(int pSlot) {
        ItemStack itemstack = this.getItemStacks().get(pSlot);
        if (itemstack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.getItemStacks().set(pSlot, ItemStack.EMPTY);
            return itemstack;
        }
    }

    /**
     * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
     */
    @Override
    public void setItem(int pSlot, ItemStack pStack) {
        this.getItemStacks().set(pSlot, pStack);
        pStack.limitSize(this.getMaxStackSize(pStack));
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(@NotNull Player pPlayer) {
        return this.isAlive() && pPlayer.distanceToSqr(this) <= 16.0;
    }

    @Override
    public void clearContent() {
        this.getItemStacks().clear();
    }


    /// Menu Provider ////////////////////////////////////////

    @Override
    public @NotNull Component getDisplayName() {
        return Component.literal("Assistance Drone");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int pContainerId, @NotNull Inventory pPlayerInventory, @NotNull Player pPlayer) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        packetBuffer.writeVarInt(this.getId());
        return new DroneMenu(pContainerId, pPlayerInventory, packetBuffer);
    }

}