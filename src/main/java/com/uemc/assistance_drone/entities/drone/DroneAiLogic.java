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
 * Handles the AI logic for the DroneEntity.
 */
public class DroneAiLogic {

    // --- CONFIGURATION CONSTANTS ---
    private static final double MOVEMENT_THRESHOLD_SQR = 1.2;
    private static final double INTERACT_RANGE_SQR = 4.0;

    private final DroneEntity drone;

    // --- MINING STATE ---
    private BlockPos currentMiningPos = null;
    private BlockState currentMiningState = null;
    private float currentDestroyProgress = 0.0F;
    private int miningSoundCooldown = 0;

    // Mining Calc Cache
    private float cachedToolSpeed = 0.0F;
    private float cachedBlockHardness = 0.0F;
    private ItemStack cachedBestTool = ItemStack.EMPTY;
    private int lastHasteAmplifier = -1;

    // --- INTERNAL ACCESSIBILITY CACHE ---
    private final Map<BlockPos, CachedReachability> accessibilityCache = new HashMap<>();
    private BlockPos lastDronePos = null;

    private record CachedReachability(long expiryTick, boolean reachable) {}

    public DroneAiLogic(DroneEntity drone) {
        this.drone = drone;
    }

    // ============================================================================================
    // MOVEMENT LOGIC
    // ============================================================================================

    public void executeMovement(Vec3 targetPos) {
        if (targetPos == null) return;

        BlockPos currentPos = drone.blockPosition();
        if (lastDronePos == null || !lastDronePos.equals(currentPos)) {
            if (lastDronePos != null && lastDronePos.distManhattan(currentPos) > 1) {
                accessibilityCache.clear();
            }
            lastDronePos = currentPos;
        }

        double distSqr = drone.position().distanceToSqr(targetPos);

        if (distSqr > MOVEMENT_THRESHOLD_SQR && !canSeeTarget(targetPos)) {
            drone.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
        } else {
            drone.getNavigation().stop();
            drone.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, 1.0);
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
        return result.getType() == HitResult.Type.MISS || result.getLocation().distanceToSqr(target) < 1.0;
    }

    public Vec3 getSafetyTarget(Player owner, double distance) {
        Vec3 dir = drone.position().subtract(owner.position());
        if (dir.lengthSqr() < 0.01) dir = new Vec3(1, 0, 0);
        dir = dir.normalize();
        Vec3 safetyPoint = owner.position().add(dir.scale(distance));
        return calculateIdealPosition(safetyPoint.x, owner.getEyeY(), safetyPoint.z);
    }

    public Vec3 calculateIdealPosition(double x, double y, double z) {
        double floorY = getFloorY(x, y, z);
        if (y - floorY < 1.5) {
            y = floorY + 1.5;
        }
        return new Vec3(x, y, z);
    }

    // ============================================================================================
    // ACCESSIBILITY & INTERACTION
    // ============================================================================================

    public boolean isInRangeToInteract(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        if (drone.distanceToSqr(center) > INTERACT_RANGE_SQR) return false;

        BlockHitResult result = drone.level().clip(new ClipContext(
                drone.getEyePosition(), center,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone
        ));

        if (result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(pos)) {
            return true;
        }
        return isBlockAccessible(pos);
    }

    public boolean isBlockAccessible(BlockPos pos) {
        if (drone.blockPosition().distManhattan(pos) <= 1) return true;

        long currentTick = drone.level().getGameTime();
        if (accessibilityCache.containsKey(pos)) {
            CachedReachability entry = accessibilityCache.get(pos);
            if (currentTick < entry.expiryTick) {
                return entry.reachable;
            } else {
                accessibilityCache.remove(pos);
            }
        }

        Vec3 center = Vec3.atCenterOf(pos);
        BlockHitResult result = drone.level().clip(new ClipContext(
                drone.blockPosition().getCenter(), center,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone
        ));

        boolean reachable;
        if (result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(pos)) {
            reachable = true;
        } else {
            Path path = drone.getNavigation().createPath(pos, 1);
            reachable = (path != null && path.canReach());
        }

        return reachable;
    }

    // ============================================================================================
    // MINING LOGIC
    // ============================================================================================

    public boolean mineBlock(BlockPos pos) {
        Level level = drone.level();
        BlockState state = level.getBlockState(pos);

        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            resetMiningState();
            return true;
        }

        // Detectar cambios en el efecto Haste (ahora aplicado por el Mixin del Beacon)
        int currentHaste = getHasteAmplifier();
        boolean buffsChanged = (currentHaste != this.lastHasteAmplifier);

        boolean isSameBlockType = currentMiningState != null && state.is(currentMiningState.getBlock());

        if (currentMiningPos == null || !currentMiningPos.equals(pos) || !isSameBlockType || buffsChanged) {
            if (currentMiningPos != null && (!currentMiningPos.equals(pos) || !isSameBlockType)) {
                resetMiningState();
            }

            currentMiningPos = pos;
            currentMiningState = state;
            this.lastHasteAmplifier = currentHaste;

            this.cachedBestTool = getBestTool(state);
            this.cachedBlockHardness = state.getDestroySpeed(level, pos);
            this.cachedToolSpeed = cachedBestTool.getDestroySpeed(state);

            if (currentHaste >= 0) {
                this.cachedToolSpeed *= 1.0F + (currentHaste + 1) * 0.2F;
            }
            if (this.cachedToolSpeed < 1.0f) this.cachedToolSpeed = 1.0f;
        }

        float damage = this.cachedToolSpeed / this.cachedBlockHardness / 30.0F;
        this.currentDestroyProgress += damage;

        int progressInt = (int) (this.currentDestroyProgress * 10.0F);
        int prevProgressInt = (int) ((this.currentDestroyProgress - damage) * 10.0F);

        if (progressInt != prevProgressInt) {
            level.destroyBlockProgress(drone.getId(), pos, progressInt);
        }

        if (this.miningSoundCooldown++ % 4 == 0) {
            var soundType = state.getSoundType(level, pos, drone);
            level.playSound(null, pos, soundType.getHitSound(), SoundSource.BLOCKS,
                    (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
        }

        if (this.currentDestroyProgress >= 1.0F) {
            breakAndDrop(level, pos, state, this.cachedBestTool);
            resetMiningState();
            return true;
        }

        return false;
    }

    @SuppressWarnings("DataFlowIssue")
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
        this.currentDestroyProgress = 0.0F;
        this.miningSoundCooldown = 0;
        this.currentMiningPos = null;
        this.currentMiningState = null;
    }

    // ============================================================================================
    // INVENTORY MANAGEMENT & HELPERS
    // ============================================================================================

    public boolean hasInventorySpaceFor(ItemStack item) {
        if (item.isEmpty()) return true;
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(drone.getInventory(), item.copy(), true);
        return remainder.getCount() < item.getCount();
    }

    public boolean hasAnyInventorySpace() {
        var inventory = this.drone.getInventory(); // o como lo accedas

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stackInSlot = inventory.getStackInSlot(slot);

            // Slot vacío → espacio seguro
            if (stackInSlot.isEmpty()) {
                return true;
            }

            // Slot con items pero no lleno
            if (stackInSlot.getCount() < stackInSlot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

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

    public ItemStack itemStore(ItemStack item) {
        return ItemHandlerHelper.insertItemStacked(drone.getInventory(), item, false);
    }

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
                    if (remainder.getCount() == stack.getCount()) {
                        pickedSomething = true;
                    }
                }
            }
        }
        return pickedSomething;
    }

    public boolean placeBlock(BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Level level = drone.level();
        if (!level.getBlockState(pos).canBeReplaced()) return false;

        BlockPlaceContext context = new BlockPlaceContext(
                level, null, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );

        InteractionResult result = blockItem.place(context);
        return result.consumesAction();
    }

    public BlockPos getObstructionBlock(BlockPos targetPos) {
        Vec3 start = drone.getEyePosition();
        Vec3 end = Vec3.atCenterOf(targetPos);
        if (start.distanceToSqr(end) < 1.0) return null;

        BlockHitResult result = drone.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone
        ));

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = result.getBlockPos();
            if (!hitPos.equals(targetPos)) {
                return hitPos;
            }
        }
        return null;
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean isValidMiningTarget(BlockPos pos) {
        BlockState state = drone.level().getBlockState(pos);

        // 1. Descartar Aire
        if (state.isAir()) return false;

        // 2. Descartar Fluidos (CRÍTICO: El agua tiene dureza 100.0f)
        // Usamos !isEmpty() para detectar cualquier fluido (agua, lava, modded)
        if (!state.getFluidState().isEmpty() && !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return false;
        }

        // 3. Descartar Indestructibles (Bedrock, Barreras, Portales del End -> Dureza -1.0)
        float hardness = state.getDestroySpeed(drone.level(), pos);
        if (hardness < 0) return false;
        if (state.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE)) return false;

        return true;
    }

    public BlockPos getSuffocatingBlock() {
        BlockPos pos = drone.blockPosition();
        BlockState state = drone.level().getBlockState(pos);
        if (!state.isAir() && state.isSuffocating(drone.level(), pos)) {
            return pos;
        }
        return null;
    }

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

    private double getFloorY(double x, double y, double z) {
        Vec3 start = new Vec3(x, y, z);
        Vec3 end = new Vec3(x, drone.level().getMinBuildHeight() - 1.0, z);
        BlockHitResult result = drone.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone
        ));
        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getLocation().y;
        }
        return drone.level().getMinBuildHeight();
    }
}