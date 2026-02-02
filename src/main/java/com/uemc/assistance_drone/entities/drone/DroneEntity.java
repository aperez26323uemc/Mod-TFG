package com.uemc.assistance_drone.entities.drone;

import com.uemc.assistance_drone.entities.drone.goals.DroneFluidHandlerGoal;
import com.uemc.assistance_drone.entities.drone.goals.DroneGoalRegistry;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.menus.DroneMenu;
import com.uemc.assistance_drone.util.ModKeys;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class DroneEntity extends PathfinderMob implements MenuProvider {

    public static final String NAME = ModKeys.DRONE_ENTITY_KEY;
    private static final float DRONE_WIDTH = 0.7F;
    private static final float DRONE_HEIGHT = 0.6F;
    private static final EntityDataAccessor<String> STATE =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> HAS_PLANNER =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);

    public static Supplier<EntityType<DroneEntity>> ENTITY_TYPE_SUPPLIER =
            () -> EntityType.Builder.of(DroneEntity::new, MobCategory.MISC)
                    .sized(DRONE_WIDTH, DRONE_HEIGHT)
                    .build(NAME);

    public final AnimationState bladeAnimation = new AnimationState();

    // --- COMPONENTE LÓGICO ---
    private final DroneAiLogic aiLogic; // Nuestra caja de herramientas

    private final ItemStackHandler inventory = new ItemStackHandler(13) {
        @Override
        public int getSlotLimit(int slot) {
            return 16;
        }
    };

    public DroneEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.aiLogic = new DroneAiLogic(this);

        this.blocksBuilding = true;
        this.noPhysics = false;
        this.setNoGravity(true);
        this.setPersistenceRequired();

        this.moveControl = new DroneMoveControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    // --- ACCESO A LA LÓGICA (Para los Goals) ---
    public DroneAiLogic getLogic() {
        return this.aiLogic;
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    @Override
    public void updateFluidHeightAndDoFluidPushing() {
        return;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            ItemStack stack = this.inventory.getStackInSlot(0);
            boolean present = !stack.isEmpty() && stack.getItem() == ModItems.SITE_PLANNER.get();
            this.entityData.set(HAS_PLANNER, present);
            String currentState = this.getState();
            DroneGoalRegistry.StateDefinition def = DroneGoalRegistry.get(currentState);
            if (def != null && !def.isAvailable(this)) {
                this.setState(ModKeys.STATE_IDLE);
            }

            this.setYRot(this.getYHeadRot());
        }
        if (this.level().isClientSide) {
            this.bladeAnimation.startIfStopped(tickCount);
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        for (DroneGoalRegistry.StateDefinition def : DroneGoalRegistry.getDefinitions()) {
            // Creamos el goal usando la fábrica
            Goal goal = def.factory().apply(this);

            // Lo registramos con SU prioridad definida en el registro
            this.goalSelector.addGoal(def.priority(), goal);
        }

        this.goalSelector.addGoal(1, new DroneFluidHandlerGoal(
                this,
                state -> state.equals(ModKeys.STATE_MINE)
        ));

        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F, 1F));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    public boolean isPushable() { return true; }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
    }

    @Override
    public void travel(Vec3 pTravelVector) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, pTravelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8F));
            } else {
                this.moveRelative(this.getSpeed(), pTravelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.99F));
            }
        }
        this.calculateEntityAnimation(false);
    }

    // --- DATOS Y PERSISTENCIA ---
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, ModKeys.STATE_IDLE)
                .define(OWNER, Optional.empty())
                .define(HAS_PLANNER, false);
    }

    public boolean hasSitePlanner() {
        return this.entityData.get(HAS_PLANNER);
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
        super.addAdditionalSaveData(compound);
        compound.putString("State", this.getState());
        compound.put("Inventory", this.inventory.serializeNBT(this.registryAccess()));

        if (getOwnerUUID() != null) {
            compound.putUUID("Owner", getOwnerUUID());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("State", 8)) {
            this.setState(compound.getString("State"));
        }
        if (compound.contains("Inventory")) {
            this.inventory.deserializeNBT(this.registryAccess(), compound.getCompound("Inventory"));
        }
        if (compound.hasUUID("Owner")) {
            this.entityData.set(OWNER, Optional.of(compound.getUUID("Owner")));
        }
    }

    public String getState() {
        return this.entityData.get(STATE);
    }

    public void setState(String newState) {
        this.getEntityData().set(STATE, newState);
    }

    // --- INTERACCIÓN ---
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide()) {
            if (player.isShiftKeyDown() && (player.getUUID().equals(getOwnerUUID()))) {
                this.discard();
                this.spawnAtLocation(new ItemStack(com.uemc.assistance_drone.items.ModItems.DRONE_ITEM.get()));
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(this, buf -> buf.writeVarInt(this.getId()));
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    // --- MENU PROVIDER ---
    @Override
    public @Nullable AbstractContainerMenu createMenu(int pContainerId, @NotNull Inventory pPlayerInventory, @NotNull Player pPlayer) {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        packetBuffer.writeVarInt(this.getId());
        return new DroneMenu(pContainerId, pPlayerInventory, packetBuffer);
    }

    @Override
    protected void checkFallDamage(double pY, boolean pOnGround, net.minecraft.world.level.block.state.BlockState pState, BlockPos pPos) {}

    @Override
    protected void playStepSound(BlockPos pPos, net.minecraft.world.level.block.state.BlockState pState) {}
}