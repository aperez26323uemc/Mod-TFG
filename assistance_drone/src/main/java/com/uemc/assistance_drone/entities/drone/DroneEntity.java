package com.uemc.assistance_drone.entities.drone;

import com.uemc.assistance_drone.entities.drone.goals.DroneFluidHandlerGoal;
import com.uemc.assistance_drone.entities.drone.goals.DroneGoalRegistry;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.menus.DroneMenu;
import com.uemc.assistance_drone.util.ModKeys;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Flying utility entity controlled by an internal state-based AI.
 * <p>
 * The drone supports inventory handling, ownership, persistent state,
 * and a modular goal system driven by {@link DroneGoalRegistry}.
 */
public class DroneEntity extends PathfinderMob implements MenuProvider {

    /* ------------------------------------------------------------ */
    /* Constants & Entity Data                                      */
    /* ------------------------------------------------------------ */

    public static final String NAME = ModKeys.DRONE_ENTITY_KEY;
    private static final float DRONE_WIDTH = 0.7F;
    private static final float DRONE_HEIGHT = 0.6F;

    private static final EntityDataAccessor<String> STATE =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> HAS_PLANNER =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);

    public static final Supplier<EntityType<DroneEntity>> ENTITY_TYPE_SUPPLIER =
            () -> EntityType.Builder.of(DroneEntity::new, MobCategory.MISC)
                    .sized(DRONE_WIDTH, DRONE_HEIGHT)
                    .build(NAME);

    /* ------------------------------------------------------------ */
    /* Instance Fields                                              */
    /* ------------------------------------------------------------ */

    public final AnimationState bladeAnimation = new AnimationState();

    private final DroneAiLogic aiLogic;

    private final ItemStackHandler inventory = new ItemStackHandler(13) {
        @Override
        public int getSlotLimit(int slot) {
            return 16;
        }
    };

    /* ------------------------------------------------------------ */
    /* Construction & Attributes                                    */
    /* ------------------------------------------------------------ */

    public DroneEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.aiLogic = new DroneAiLogic(this);

        this.blocksBuilding = true;
        this.noPhysics = false;
        this.setNoGravity(true);
        this.setPersistenceRequired();

        this.moveControl = new DroneMoveControl(this);

        this.setPathfindingMalus(PathType.WATER, 0.5F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 1.0F);
        this.setPathfindingMalus(PathType.LAVA, 1.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 0.5F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.5F);
    }

    /**
     * Defines the base attributes of the drone entity.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    /* ------------------------------------------------------------ */
    /* Accessors                                                    */
    /* ------------------------------------------------------------ */

    /**
     * Returns the AI logic controller used by goals.
     */
    public DroneAiLogic getLogic() {
        return this.aiLogic;
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    /**
     * Checks whether the internal inventory contains any items.
     */
    public boolean isInventoryEmpty() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /* ------------------------------------------------------------ */
    /* Ticking & Navigation                                         */
    /* ------------------------------------------------------------ */

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            ItemStack stack = this.inventory.getStackInSlot(0);
            boolean hasPlanner = !stack.isEmpty() && stack.getItem() == ModItems.SITE_PLANNER.get();
            this.entityData.set(HAS_PLANNER, hasPlanner);

            DroneGoalRegistry.StateDefinition def = DroneGoalRegistry.get(this.getState());
            if (def != null && !def.isAvailable(this)) {
                this.setState(ModKeys.STATE_IDLE);
            }

            this.setYRot(this.getYHeadRot());
        } else {
            this.bladeAnimation.startIfStopped(tickCount);
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        for (DroneGoalRegistry.StateDefinition def : DroneGoalRegistry.getDefinitions()) {
            Goal goal = def.factory().apply(this);
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
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level){
            @Override
            public boolean moveTo(@javax.annotation.Nullable Path pathentity, double speed) {
                doStuckDetection(position());
                return super.moveTo(pathentity, speed);
            }
            @Override
            protected void doStuckDetection(Vec3 positionVec3) {
                double dy = Math.abs(positionVec3.y - this.lastStuckCheckPos.y);
                if (dy < 0.45D) {
                    positionVec3 = new Vec3(
                            positionVec3.x,
                            this.lastStuckCheckPos.y,
                            positionVec3.z
                    );
                }
                super.doStuckDetection(positionVec3);
            }
        };
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        nav.setCanFloat(true);
        return nav;
    }

    /* ------------------------------------------------------------ */
    /* Movement & Damage                                            */
    /* ------------------------------------------------------------ */

    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    @Override
    public void updateFluidHeightAndDoFluidPushing() {}

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8F));
            } else {
                this.moveRelative(this.getSpeed(), travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.99F));
            }
        }
        this.calculateEntityAnimation(false);
    }

    /* ------------------------------------------------------------ */
    /* Synced Data & Persistence                                    */
    /* ------------------------------------------------------------ */

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, ModKeys.STATE_IDLE)
                .define(OWNER, Optional.empty())
                .define(HAS_PLANNER, false);
    }

    public String getState() {
        return this.entityData.get(STATE);
    }

    public void setState(String newState) {
        this.entityData.set(STATE, newState);
    }

    public boolean hasSitePlanner() {
        return this.entityData.get(HAS_PLANNER);
    }

    public @Nullable UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public @Nullable Player getOwner() {
        UUID uuid = getOwnerUUID();
        return uuid != null ? level().getPlayerByUUID(uuid) : null;
    }

    public void setOwner(Player player) {
        this.entityData.set(OWNER, Optional.of(player.getUUID()));
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("State", this.getState());
        tag.put("Inventory", this.inventory.serializeNBT(this.registryAccess()));

        if (getOwnerUUID() != null) {
            tag.putUUID("Owner", getOwnerUUID());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("State", 8)) {
            this.setState(tag.getString("State"));
        }
        if (tag.contains("Inventory")) {
            this.inventory.deserializeNBT(this.registryAccess(), tag.getCompound("Inventory"));
        }
        if (tag.hasUUID("Owner")) {
            this.entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        }
    }

    /* ------------------------------------------------------------ */
    /* Interaction & Menu                                           */
    /* ------------------------------------------------------------ */

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide()) {

            if (!player.isSpectator()
                    && player.isShiftKeyDown()
                    && player.getUUID().equals(getOwnerUUID())
                    && this.isInventoryEmpty()) {

                this.discard();
                this.spawnAtLocation(new ItemStack(ModItems.DRONE_ITEM.get()));
                return InteractionResult.SUCCESS;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(this, buf -> buf.writeVarInt(this.getId()));
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(
            int containerId,
            @NotNull Inventory playerInventory,
            @NotNull Player player
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(this.getId());
        return new DroneMenu(containerId, playerInventory, buffer);
    }

    /* ------------------------------------------------------------ */
    /* Disabled Ground Logic                                        */
    /* ------------------------------------------------------------ */

    @Override
    protected void checkFallDamage(double y, boolean onGround,
                                   net.minecraft.world.level.block.state.BlockState state,
                                   BlockPos pos) {}

    @Override
    protected void playStepSound(BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {}
}
