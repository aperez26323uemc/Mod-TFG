package com.uemc.assistance_drone.entities.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
 * Encapsulates all non-trivial decision-making and world interaction logic,
 * including movement, mining, inventory handling, block placement and
 * performance-oriented caching.
 */
public class DroneAiLogic {

    /* ------------------------------------------------------------ */
    /* Constants                                                    */
    /* ------------------------------------------------------------ */

    private static final double MOVEMENT_THRESHOLD_SQR = 1.2;
    private static final double INTERACT_RANGE_SQR = 4.0;
    private static final double MIN_ALTITUDE_OFFSET = 1.5;

    /* ------------------------------------------------------------ */
    /* State                                                        */
    /* ------------------------------------------------------------ */

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

    private record CachedReachability(long expiryTick, boolean reachable) {}

    public DroneAiLogic(DroneEntity drone) {
        this.drone = drone;
    }

    /* ------------------------------------------------------------ */
    /* Movement                                                     */
    /* ------------------------------------------------------------ */

    /**
     * Moves the drone towards a target position.
     * <p>
     * Uses direct movement when the target is close and visible,
     * otherwise falls back to pathfinding.
     */
    public void executeMovement(Vec3 targetPos) {
        if (targetPos == null) return;

        updateAccessibilityCacheIfMoved();

        double distSqr = drone.position().distanceToSqr(targetPos);

        if (distSqr > MOVEMENT_THRESHOLD_SQR && !canSeeTarget(targetPos)) {
            drone.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
        } else {
            drone.getNavigation().stop();
            drone.getMoveControl().setWantedPosition(
                    targetPos.x, targetPos.y, targetPos.z, 1.0
            );
        }
    }

    private void updateAccessibilityCacheIfMoved() {
        BlockPos currentPos = drone.blockPosition();
        if (lastDronePos == null || !lastDronePos.equals(currentPos)) {
            if (lastDronePos != null && lastDronePos.distManhattan(currentPos) > 1) {
                accessibilityCache.clear();
            }
            lastDronePos = currentPos;
        }
    }

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
     * Computes a safe offset position relative to the owner.
     */
    public Vec3 getSafetyTarget(Player owner, double distance) {
        Vec3 direction = drone.position().subtract(owner.position());
        if (direction.lengthSqr() < 0.01) {
            direction = new Vec3(1, 0, 0);
        }

        direction = direction.normalize();
        Vec3 point = owner.position().add(direction.scale(distance));

        return calculateIdealPosition(point.x, owner.getEyeY(), point.z);
    }

    /**
     * Ensures a minimum vertical clearance above ground.
     */
    public Vec3 calculateIdealPosition(double x, double y, double z) {
        double floorY = getFloorY(x, y, z);
        if (y - floorY < MIN_ALTITUDE_OFFSET) {
            y = floorY + MIN_ALTITUDE_OFFSET;
        }
        return new Vec3(x, y, z);
    }

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

    /* ------------------------------------------------------------ */
    /* Accessibility                                                */
    /* ------------------------------------------------------------ */

    public boolean isInRangeToInteract(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        if (drone.distanceToSqr(center) > INTERACT_RANGE_SQR) {
            return false;
        }

        BlockHitResult result = drone.level().clip(new ClipContext(
                drone.getEyePosition(),
                center,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                drone
        ));

        return result.getType() == HitResult.Type.MISS
                || result.getBlockPos().equals(pos)
                || isBlockAccessible(pos);
    }

    /**
     * Determines whether a block can be reached by the drone.
     * <p>
     * Uses a time-based cache to avoid repeated pathfinding queries.
     */
    public boolean isBlockAccessible(BlockPos pos) {
        if (drone.blockPosition().distManhattan(pos) <= 1) {
            return true;
        }

        long tick = drone.level().getGameTime();
        CachedReachability cached = accessibilityCache.get(pos);

        if (cached != null && tick < cached.expiryTick) {
            return cached.reachable;
        }

        boolean reachable = performAccessibilityCheck(pos);
        accessibilityCache.remove(pos);

        return reachable;
    }

    private boolean performAccessibilityCheck(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);

        BlockHitResult result = drone.level().clip(new ClipContext(
                drone.blockPosition().getCenter(),
                center,
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

    /* ------------------------------------------------------------ */
    /* Mining                                                       */
    /* ------------------------------------------------------------ */

    /**
     * Mines a block progressively, applying tool efficiency and Haste effects.
     *
     * @return {@code true} if the block was fully mined this tick
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

    private void updateMiningCacheIfNeeded(BlockPos pos, BlockState state) {
        int haste = getHasteAmplifier();
        boolean buffsChanged = haste != lastHasteAmplifier;
        boolean targetChanged = currentMiningPos == null
                || !currentMiningPos.equals(pos)
                || !state.is(currentMiningState.getBlock());

        if (targetChanged || buffsChanged) {
            if (targetChanged) {
                resetMiningState();
            }

            currentMiningPos = pos;
            currentMiningState = state;
            lastHasteAmplifier = haste;

            recalculateMiningSpeed(state, haste);
        }
    }

    private void recalculateMiningSpeed(BlockState state, int hasteAmplifier) {
        cachedBestTool = getBestTool(state);
        cachedBlockHardness = state.getDestroySpeed(drone.level(), currentMiningPos);
        cachedToolSpeed = cachedBestTool.getDestroySpeed(state);

        if (hasteAmplifier >= 0) {
            cachedToolSpeed *= 1.0F + (hasteAmplifier + 1) * 0.2F;
        }

        if (cachedToolSpeed < 1.0F) {
            cachedToolSpeed = 1.0F;
        }
    }

    private void updateMiningVisuals(Level level, BlockPos pos, float damage) {
        int progress = (int) (currentDestroyProgress * 10.0F);
        int previous = (int) ((currentDestroyProgress - damage) * 10.0F);

        if (progress != previous) {
            level.destroyBlockProgress(drone.getId(), pos, progress);
        }
    }

    // Throttled mining hit sounds
    private void playMiningSounds(Level level, BlockPos pos, BlockState state) {
        if (miningSoundCooldown++ % 4 == 0) {
            var sound = state.getSoundType(level, pos, drone);
            level.playSound(null, pos, sound.getHitSound(),
                    SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 8.0F,
                    sound.getPitch() * 0.5F
            );
        }
    }

    private int getHasteAmplifier() {
        if (drone.hasEffect(MobEffects.DIG_SPEED)) {
            return drone.getEffect(MobEffects.DIG_SPEED).getAmplifier();
        }
        return -1;
    }

    public void resetMiningState() {
        if (currentMiningPos != null) {
            drone.level().destroyBlockProgress(drone.getId(), currentMiningPos, -1);
        }
        currentDestroyProgress = 0.0F;
        miningSoundCooldown = 0;
        currentMiningPos = null;
        currentMiningState = null;
    }

    private void breakAndDrop(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        if (level instanceof ServerLevel serverLevel) {
            LootParams.Builder params = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, tool)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, drone);

            List<ItemStack> drops = state.getDrops(params);
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

    /* ------------------------------------------------------------ */
    /* Inventory                                                    */
    /* ------------------------------------------------------------ */

    public boolean hasInventorySpaceFor(ItemStack item) {
        if (item.isEmpty()) return true;
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                drone.getInventory(), item.copy(), true
        );
        return remainder.getCount() < item.getCount();
    }

    public boolean hasAnyInventorySpace() {
        var inventory = drone.getInventory();

        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) return true;
            int effectiveLimit = Math.min(inventory.getSlotLimit(i), stack.getMaxStackSize());
            if (stack.getCount() < effectiveLimit) return true;
        }
        return false;
    }

    public int findSlotWithFluidRemoverBlock() {
        for (int i = 0; i < drone.getInventory().getSlots(); i++) {
            ItemStack stack = drone.getInventory().getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi) {
                BlockState state = bi.getBlock().defaultBlockState();
                if (state.isCollisionShapeFullBlock(drone.level(), drone.blockPosition())) {
                    return i;
                }
            }
        }
        return -1;
    }

    public ItemStack itemStore(ItemStack item) {
        return ItemHandlerHelper.insertItemStacked(drone.getInventory(), item, false);
    }

    public boolean itemPickUp() {
        AABB area = drone.getBoundingBox().inflate(1.0, 0.5, 1.0);
        List<ItemEntity> items = drone.level().getEntitiesOfClass(ItemEntity.class, area);
        boolean picked = false;

        for (ItemEntity entity : items) {
            if (!entity.isAlive() || entity.getItem().isEmpty()) continue;

            ItemStack stack = entity.getItem();
            ItemStack remainder = itemStore(stack);

            if (remainder.isEmpty()) {
                entity.discard();
                picked = true;
            } else {
                entity.setItem(remainder);
                if (remainder.getCount() < stack.getCount()) {
                    picked = true;
                }
            }
        }
        return picked;
    }

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

        return blockItem.place(context).consumesAction();
    }

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

        if (result.getType() == HitResult.Type.BLOCK && !result.getBlockPos().equals(targetPos)) {
            return result.getBlockPos();
        }

        return null;
    }

    /**
     * Validates whether a block can be mined by the drone.
     */
    public boolean isValidMiningTarget(BlockPos pos) {
        BlockState state = drone.level().getBlockState(pos);

        if (state.isAir()) return false;

        if (!state.getFluidState().isEmpty()
                && !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return false;
        }

        if (state.getDestroySpeed(drone.level(), pos) < 0) return false;

        return !state.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE);
    }

    public BlockPos getSuffocatingBlock() {
        BlockPos pos = drone.blockPosition();
        BlockState state = drone.level().getBlockState(pos);

        if (!state.isAir() && state.isSuffocating(drone.level(), pos)) {
            return pos;
        }
        return null;
    }
}
