package com.uemc.assistance_drone.entities.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central logic component for drone AI operations.
 * <p>
 * This class encapsulates all complex decision-making and world interaction logic
 * for drones, including:
 * <ul>
 *   <li>Movement pathfinding and obstacle avoidance</li>
 *   <li>Block mining with tool selection and Haste effect support</li>
 *   <li>Inventory management and item pickup</li>
 *   <li>Block placement for fluid removal</li>
 *   <li>Accessibility caching for performance optimization</li>
 * </ul>
 * </p>
 *
 * <h3>Performance Optimizations</h3>
 * <p>
 * The class employs several caching strategies to reduce computational overhead:
 * <ul>
 *   <li>Block accessibility cache with position-based expiration</li>
 *   <li>Mining calculation cache for hardness and tool speed</li>
 *   <li>Haste amplifier tracking to detect buff changes</li>
 * </ul>
 * </p>
 *
 * @see DroneEntity
 */
public class DroneAiLogic {

    /** Maximum squared distance for direct line-of-sight movement (blocks²) */
    private static final double MOVEMENT_THRESHOLD_SQR = 1.2;

    /** Maximum squared distance for block interaction (blocks²) */
    private static final double INTERACT_RANGE_SQR = 4.0;

    /** Minimum altitude offset above ground level (blocks) */
    private static final double MIN_ALTITUDE_OFFSET = 1.5;

    private final DroneEntity drone;

    private BlockPos currentMiningPos = null;
    private BlockState currentMiningState = null;
    private float currentDestroyProgress = 0.0F;
    private int miningSoundCooldown = 0;

    private float cachedToolSpeed = 0.0F;
    private float cachedBlockHardness = 0.0F;
    private ItemStack cachedBestTool = ItemStack.EMPTY;
    private int lastHasteAmplifier = -1;

    private final Map<BlockPos, CachedReachability> accessibilityCache = new HashMap<>();
    private BlockPos lastDronePos = null;

    /**
     * Cached accessibility result with expiration.
     *
     * @param expiryTick game tick when this cache entry expires
     * @param reachable whether the position is accessible
     */
    private record CachedReachability(long expiryTick, boolean reachable) {}

    /**
     * Constructs a new AI logic component for the specified drone.
     *
     * @param drone the drone entity this logic will control
     */
    public DroneAiLogic(DroneEntity drone) {
        this.drone = drone;
    }

    // ============================================================================================
    // MOVEMENT LOGIC
    // ============================================================================================

    /**
     * Executes movement toward a target position with line-of-sight optimization.
     * <p>
     * If the target is within {@value MOVEMENT_THRESHOLD_SQR} blocks² and visible,
     * uses direct movement. Otherwise, engages pathfinding navigation.
     * </p>
     *
     * @param targetPos the position to move towards
     */
    public void executeMovement(Vec3 targetPos) {
        if (targetPos == null) return;

        updateAccessibilityCacheIfMoved();

        double distSqr = drone.position().distanceToSqr(targetPos);

        if (distSqr > MOVEMENT_THRESHOLD_SQR && !canSeeTarget(targetPos)) {
            drone.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
        } else {
            drone.getNavigation().stop();
            drone.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, 1.0);
        }
    }

    /**
     * Clears the accessibility cache when the drone moves significantly.
     */
    private void updateAccessibilityCacheIfMoved() {
        BlockPos currentPos = drone.blockPosition();
        if (lastDronePos == null || !lastDronePos.equals(currentPos)) {
            if (lastDronePos != null && lastDronePos.distManhattan(currentPos) > 1) {
                accessibilityCache.clear();
            }
            lastDronePos = currentPos;
        }
    }

    /**
     * Performs raycasting to determine if target position is visible.
     *
     * @param target the position to check visibility for
     * @return {@code true} if there is a clear line of sight to the target
     */
    private boolean canSeeTarget(Vec3 target) {
        BlockHitResult result = drone.level().clip(new ClipContext(
                drone.position(),
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                drone
        ));
        return result.getType() == HitResult.Type.MISS
                || result.getLocation().distanceToSqr(target) < 1.0;
    }

    /**
     * Calculates a safe position offset from the owner.
     * <p>
     * Computes a position at the specified distance from the owner in the
     * direction of the drone-owner vector, ensuring safe altitude.
     * </p>
     *
     * @param owner the owner entity to offset from
     * @param distance the desired distance from owner (blocks)
     * @return safe position vector at owner's eye level
     */
    public Vec3 getSafetyTarget(Player owner, double distance) {
        Vec3 directionVector = drone.position().subtract(owner.position());
        if (directionVector.lengthSqr() < 0.01) {
            directionVector = new Vec3(1, 0, 0);
        }
        directionVector = directionVector.normalize();

        Vec3 safetyPoint = owner.position().add(directionVector.scale(distance));
        return calculateIdealPosition(safetyPoint.x, owner.getEyeY(), safetyPoint.z);
    }

    /**
     * Calculates an ideal hover position ensuring minimum altitude above ground.
     * <p>
     * If the desired Y coordinate is too close to the ground (within
     * {@value MIN_ALTITUDE_OFFSET} blocks), it is raised to maintain safe clearance.
     * </p>
     *
     * @param x the desired X coordinate
     * @param y the desired Y coordinate
     * @param z the desired Z coordinate
     * @return position vector with safe altitude
     */
    public Vec3 calculateIdealPosition(double x, double y, double z) {
        double floorY = getFloorY(x, y, z);
        if (y - floorY < MIN_ALTITUDE_OFFSET) {
            y = floorY + MIN_ALTITUDE_OFFSET;
        }
        return new Vec3(x, y, z);
    }

    /**
     * Finds the Y coordinate of the ground below a position using raycasting.
     *
     * @param x the X coordinate
     * @param y the starting Y coordinate for the raycast
     * @param z the Z coordinate
     * @return the Y coordinate of the first solid block below, or world minimum if none found
     */
    private double getFloorY(double x, double y, double z) {
        Vec3 start = new Vec3(x, y, z);
        Vec3 end = new Vec3(x, drone.level().getMinBuildHeight() - 1.0, z);

        BlockHitResult result = drone.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                drone
        ));

        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getLocation().y;
        }
        return drone.level().getMinBuildHeight();
    }

    // ============================================================================================
    // ACCESSIBILITY & INTERACTION
    // ============================================================================================

    /**
     * Checks if a block is within interaction range and has clear line of sight.
     *
     * @param pos the block position to check
     * @return {@code true} if the drone can interact with the block
     */
    public boolean isInRangeToInteract(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        if (drone.distanceToSqr(center) > INTERACT_RANGE_SQR) {
            return false;
        }

        BlockHitResult result = drone.level().clip(new ClipContext(
                drone.getEyePosition(), center,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                drone
        ));

        return result.getType() == HitResult.Type.MISS
                || result.getBlockPos().equals(pos)
                || isBlockAccessible(pos);
    }

    /**
     * Determines if a block position is accessible to the drone.
     * <p>
     * Uses a time-based cache to reduce expensive pathfinding calculations.
     * Adjacent blocks (Manhattan distance ≤ 1) are always considered accessible.
     * </p>
     *
     * @param pos the block position to check
     * @return {@code true} if the drone can reach the position
     */
    public boolean isBlockAccessible(BlockPos pos) {
        if (drone.blockPosition().distManhattan(pos) <= 1) {
            return true;
        }

        long currentTick = drone.level().getGameTime();
        CachedReachability cached = accessibilityCache.get(pos);

        if (cached != null && currentTick < cached.expiryTick) {
            return cached.reachable;
        }

        boolean reachable = performAccessibilityCheck(pos);

        accessibilityCache.remove(pos);
        return reachable;
    }

    /**
     * Performs the actual accessibility check using raycasting and pathfinding.
     *
     * @param pos the position to check
     * @return {@code true} if accessible
     */
    private boolean performAccessibilityCheck(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        BlockHitResult result = drone.level().clip(new ClipContext(
                drone.blockPosition().getCenter(), center,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                drone
        ));

        if (result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(pos)) {
            return true;
        }

        Path path = drone.getNavigation().createPath(pos, 1);
        return path != null && path.canReach();
    }

    // ============================================================================================
    // MINING LOGIC
    // ============================================================================================

    /**
     * Progressively mines a block, applying tool speed and Haste effects.
     * <p>
     * The mining system supports:
     * <ul>
     *   <li>Automatic tool selection (pickaxe, axe, shovel, hoe)</li>
     *   <li>Haste effect amplification from beacons</li>
     *   <li>Block drop collection into drone inventory</li>
     *   <li>Visual progress updates and mining sounds</li>
     * </ul>
     * </p>
     *
     * @param pos the position of the block to mine
     * @return {@code true} if the block was completely mined this tick
     */
    public boolean mineBlock(BlockPos pos) {
        Level level = drone.level();
        BlockState state = level.getBlockState(pos);

        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            resetMiningState();
            return true;
        }

        updateMiningCacheIfNeeded(pos, state);

        float damage = cachedToolSpeed / cachedBlockHardness / 30.0F;
        currentDestroyProgress += damage;

        updateMiningVisuals(level, pos, damage);
        playMiningSounds(level, pos, state);

        if (currentDestroyProgress >= 1.0F) {
            breakAndDrop(level, pos, state, cachedBestTool);
            resetMiningState();
            return true;
        }

        return false;
    }

    /**
     * Updates the mining calculation cache when the target or buffs change.
     */
    private void updateMiningCacheIfNeeded(BlockPos pos, BlockState state) {
        int currentHaste = getHasteAmplifier();
        boolean buffsChanged = (currentHaste != this.lastHasteAmplifier);
        boolean targetChanged = currentMiningPos == null
                || !currentMiningPos.equals(pos)
                || !state.is(currentMiningState.getBlock());

        if (targetChanged || buffsChanged) {
            if (targetChanged) {
                resetMiningState();
            }

            currentMiningPos = pos;
            currentMiningState = state;
            lastHasteAmplifier = currentHaste;

            recalculateMiningSpeed(state, currentHaste);
        }
    }

    /**
     * Recalculates mining speed based on tool and buffs.
     */
    private void recalculateMiningSpeed(BlockState state, int hasteAmplifier) {
        cachedBestTool = getBestTool(state);
        cachedBlockHardness = state.getDestroySpeed(drone.level(), currentMiningPos);
        cachedToolSpeed = cachedBestTool.getDestroySpeed(state);

        if (hasteAmplifier >= 0) {
            cachedToolSpeed *= 1.0F + (hasteAmplifier + 1) * 0.2F;
        }
        if (cachedToolSpeed < 1.0f) {
            cachedToolSpeed = 1.0f;
        }
    }

    /**
     * Updates visual mining progress on the client.
     */
    private void updateMiningVisuals(Level level, BlockPos pos, float damage) {
        int progressInt = (int) (currentDestroyProgress * 10.0F);
        int prevProgressInt = (int) ((currentDestroyProgress - damage) * 10.0F);

        if (progressInt != prevProgressInt) {
            level.destroyBlockProgress(drone.getId(), pos, progressInt);
        }
    }

    /**
     * Plays mining sounds periodically.
     */
    private void playMiningSounds(Level level, BlockPos pos, BlockState state) {
        if (miningSoundCooldown++ % 4 == 0) {
            var soundType = state.getSoundType(level, pos, drone);
            level.playSound(null, pos, soundType.getHitSound(), SoundSource.BLOCKS,
                    (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
        }
    }

    /**
     * Gets the current Haste effect amplifier.
     *
     * @return amplifier level, or -1 if no Haste effect active
     */
    @SuppressWarnings("DataFlowIssue")
    private int getHasteAmplifier() {
        if (drone.hasEffect(MobEffects.DIG_SPEED)) {
            return drone.getEffect(MobEffects.DIG_SPEED).getAmplifier();
        }
        return -1;
    }

    /**
     * Resets all mining state and clears visual progress.
     */
    public void resetMiningState() {
        if (currentMiningPos != null) {
            drone.level().destroyBlockProgress(drone.getId(), currentMiningPos, -1);
        }
        this.currentDestroyProgress = 0.0F;
        this.miningSoundCooldown = 0;
        this.currentMiningPos = null;
        this.currentMiningState = null;
    }

    /**
     * Breaks a block and collects drops into the drone's inventory.
     * <p>
     * Drops that don't fit in the inventory are spawned in the world.
     * </p>
     */
    private void breakAndDrop(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        if (level instanceof ServerLevel serverLevel) {
            LootParams.Builder lootParams = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, tool)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, drone);

            List<ItemStack> drops = state.getDrops(lootParams);
            for (ItemStack drop : drops) {
                ItemStack remaining = itemStore(drop);
                if (!remaining.isEmpty()) {
                    Block.popResource(level, pos, remaining);
                }
            }

            level.destroyBlock(pos, false);
            level.levelEvent(2001, pos, Block.getId(state));
        }
    }

    /**
     * Selects the most effective tool for mining a block.
     * <p>
     * Compares iron-tier tools (pickaxe, axe, shovel, hoe) and returns
     * the one with the highest destroy speed for the given block state.
     * </p>
     *
     * @param state the block state to mine
     * @return the most effective tool
     */
    private ItemStack getBestTool(BlockState state) {
        ItemStack pick = Items.IRON_PICKAXE.getDefaultInstance();
        ItemStack axe = Items.IRON_AXE.getDefaultInstance();
        ItemStack shovel = Items.IRON_SHOVEL.getDefaultInstance();
        ItemStack hoe = Items.IRON_HOE.getDefaultInstance();

        float p = pick.getDestroySpeed(state);
        float a = axe.getDestroySpeed(state);
        float s = shovel.getDestroySpeed(state);
        float h = hoe.getDestroySpeed(state);

        if (p >= a && p >= s && p >= h) return pick;
        if (a >= p && a >= s && a >= h) return axe;
        if (s >= p && s >= a && s >= h) return shovel;
        return hoe;
    }

    // ============================================================================================
    // INVENTORY MANAGEMENT & HELPERS
    // ============================================================================================

    /**
     * Checks if the drone's inventory has space for an item stack.
     *
     * @param item the item to check
     * @return {@code true} if the item can fit (fully or partially)
     */
    public boolean hasInventorySpaceFor(ItemStack item) {
        if (item.isEmpty()) return true;
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                drone.getInventory(), item.copy(), true);
        return remainder.getCount() < item.getCount();
    }

    /**
     * Checks if the drone has any available inventory space.
     *
     * @return {@code true} if there is at least one empty or non-full slot
     */
    public boolean hasAnyInventorySpace() {
        var inventory = this.drone.getInventory();

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stackInSlot = inventory.getStackInSlot(slot);

            if (stackInSlot.isEmpty() || stackInSlot.getCount() < stackInSlot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a slot containing a block suitable for removing fluids.
     * <p>
     * Searches for a {@link BlockItem} that produces a full collision block,
     * suitable for filling water/lava source blocks.
     * </p>
     *
     * @return slot index, or -1 if no suitable block found
     */
    public int findSlotWithFluidRemoverBlock() {
        for (int i = 0; i < drone.getInventory().getSlots(); i++) {
            ItemStack stack = drone.getInventory().getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi) {
                BlockState defaultState = bi.getBlock().defaultBlockState();
                if (defaultState.isCollisionShapeFullBlock(drone.level(), drone.blockPosition())) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Stores an item in the drone's inventory.
     *
     * @param item the item to store
     * @return remaining items that didn't fit
     */
    public ItemStack itemStore(ItemStack item) {
        return ItemHandlerHelper.insertItemStacked(drone.getInventory(), item, false);
    }

    /**
     * Picks up all nearby item entities within range.
     *
     * @return {@code true} if any items were picked up
     */
    public boolean itemPickUp() {
        AABB pickupArea = drone.getBoundingBox().inflate(1.0, 0.5, 1.0);
        List<ItemEntity> items = drone.level().getEntitiesOfClass(ItemEntity.class, pickupArea);
        boolean pickedSomething = false;

        for (ItemEntity itemEntity : items) {
            if (itemEntity.isAlive() && !itemEntity.getItem().isEmpty()) {
                ItemStack stack = itemEntity.getItem();
                ItemStack remainder = itemStore(stack);

                if (remainder.isEmpty()) {
                    itemEntity.discard();
                    pickedSomething = true;
                } else {
                    itemEntity.setItem(remainder);
                    if (remainder.getCount() < stack.getCount()) {
                        pickedSomething = true;
                    }
                }
            }
        }
        return pickedSomething;
    }

    /**
     * Attempts to place a block at the specified position.
     *
     * @param pos the position to place the block
     * @param stack the item stack containing the block
     * @return {@code true} if placement was successful
     */
    public boolean placeBlock(BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Level level = drone.level();
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }

        BlockPlaceContext context = new BlockPlaceContext(
                level, null, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );

        InteractionResult result = blockItem.place(context);
        return result.consumesAction();
    }

    /**
     * Finds the first obstructing block between the drone and a target.
     *
     * @param targetPos the target position to raycast towards
     * @return position of the blocking block, or null if path is clear
     */
    public BlockPos getObstructionBlock(BlockPos targetPos) {
        Vec3 start = drone.getEyePosition();
        Vec3 end = Vec3.atCenterOf(targetPos);

        if (start.distanceToSqr(end) < 1.0) {
            return null;
        }

        BlockHitResult result = drone.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                drone
        ));

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = result.getBlockPos();
            if (!hitPos.equals(targetPos)) {
                return hitPos;
            }
        }
        return null;
    }

    /**
     * Validates if a block is a viable mining target.
     * <p>
     * A block is valid if it:
     * <ul>
     *   <li>Is not air</li>
     *   <li>Is not a fluid (unless waterlogged)</li>
     *   <li>Has non-negative hardness (destructible)</li>
     *   <li>Is not reinforced deepslate</li>
     * </ul>
     * </p>
     *
     * @param pos the block position to validate
     * @return {@code true} if the block can be mined
     */
    @SuppressWarnings("RedundantIfStatement")
    public boolean isValidMiningTarget(BlockPos pos) {
        BlockState state = drone.level().getBlockState(pos);

        if (state.isAir()) {
            return false;
        }

        if (!state.getFluidState().isEmpty()
                && !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return false;
        }

        float hardness = state.getDestroySpeed(drone.level(), pos);
        if (hardness < 0) {
            return false;
        }

        if (state.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE)) {
            return false;
        }

        return true;
    }

    /**
     * Checks if the drone is currently inside a suffocating block.
     *
     * @return position of the suffocating block, or null if safe
     */
    public BlockPos getSuffocatingBlock() {
        BlockPos pos = drone.blockPosition();
        BlockState state = drone.level().getBlockState(pos);

        if (!state.isAir() && state.isSuffocating(drone.level(), pos)) {
            return pos;
        }
        return null;
    }
}