package com.uemc.farmer_drone.cache;

import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

/**
 * Incremental scanner and target cache for farmable blocks inside a Site Planner area.
 *
 * <p>The cache scans a fixed budget of blocks per tick and tracks two target sets:</p>
 * <ul>
 *     <li>empty farmland positions suitable for planting,</li>
 *     <li>mature crop positions ready for harvesting.</li>
 * </ul>
 *
 * <p>Immature crops are added to a growth cooldown map to avoid expensive repeated
 * {@link Level#getBlockState(BlockPos)} checks every tick.</p>
 */
public class FarmAreaCache {

    public static final int SCAN_BUDGET_PER_TICK = 64;
    public static final int IMMATURE_GROWTH_COOLDOWN_TICKS = 300;
    public static final int INACCESSIBLE_COOLDOWN_TICKS = 120;

    private final Set<BlockPos> plantTargets = new LinkedHashSet<>();
    private final Set<BlockPos> harvestTargets = new LinkedHashSet<>();

    private final Map<BlockPos, Long> growthCooldowns = new HashMap<>();
    private final Map<BlockPos, Long> inaccessibleCooldowns = new HashMap<>();

    private BlockPos areaStart;
    private BlockPos areaEnd;

    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    private int cursorX;
    private int cursorY;
    private int cursorZ;

    private boolean initialized;

    /**
     * Updates area bounds and scans a bounded amount of blocks for this tick.
     */
    public void tick(Level level, ItemStack planner, long gameTime) {
        if (!SitePlanner.isConfigured(planner)) {
            clear();
            return;
        }

        BlockPos start = SitePlanner.getStartPos(planner);
        BlockPos end = SitePlanner.getEndPos(planner);
        if (start == null || end == null) {
            clear();
            return;
        }

        if (!initialized || !start.equals(areaStart) || !end.equals(areaEnd)) {
            resetBounds(start, end);
        }

        pruneCooldowns(gameTime);

        for (int i = 0; i < SCAN_BUDGET_PER_TICK; i++) {
            scanCurrentCursor(level, gameTime);
            advanceCursor();
        }
    }

    public boolean hasPlantTargets() {
        return !plantTargets.isEmpty();
    }

    public boolean hasHarvestTargets() {
        return !harvestTargets.isEmpty();
    }

    public Optional<BlockPos> pollNearestHarvestTarget(BlockPos source, long gameTime) {
        return pollNearest(source, harvestTargets, gameTime);
    }

    public Optional<BlockPos> pollNearestPlantTarget(BlockPos source, long gameTime) {
        return pollNearest(source, plantTargets, gameTime);
    }

    public void markInaccessible(BlockPos pos, long gameTime) {
        inaccessibleCooldowns.put(pos.immutable(), gameTime + INACCESSIBLE_COOLDOWN_TICKS);
        plantTargets.remove(pos);
        harvestTargets.remove(pos);
    }

    public void markDirty(BlockPos pos) {
        plantTargets.remove(pos);
        harvestTargets.remove(pos);
        growthCooldowns.remove(pos);
        inaccessibleCooldowns.remove(pos);
    }

    private Optional<BlockPos> pollNearest(BlockPos source, Set<BlockPos> candidates, long gameTime) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        Iterator<BlockPos> iterator = candidates.iterator();
        while (iterator.hasNext()) {
            BlockPos current = iterator.next();
            Long inaccessibleUntil = inaccessibleCooldowns.get(current);
            if (inaccessibleUntil != null && gameTime < inaccessibleUntil) {
                iterator.remove();
                continue;
            }

            double distance = current.distSqr(source);
            if (distance < nearestDistance) {
                nearest = current;
                nearestDistance = distance;
            }
        }

        if (nearest != null) {
            candidates.remove(nearest);
            return Optional.of(nearest);
        }

        return Optional.empty();
    }

    private void scanCurrentCursor(Level level, long gameTime) {
        BlockPos current = new BlockPos(cursorX, cursorY, cursorZ);

        if (isInaccessible(current, gameTime)) {
            return;
        }

        BlockState state = level.getBlockState(current);

        if (state.is(Blocks.FARMLAND)) {
            scanFarmland(level, current);
            return;
        }

        if (isCrop(state)) {
            scanCrop(level, current, state, gameTime);
            return;
        }

        markDirty(current);
    }

    private void scanFarmland(Level level, BlockPos farmlandPos) {
        BlockPos cropPos = farmlandPos.above();
        BlockState cropState = level.getBlockState(cropPos);

        if (cropState.isAir()) {
            plantTargets.add(farmlandPos.immutable());
        } else {
            plantTargets.remove(farmlandPos);
        }
    }

    private void scanCrop(Level level, BlockPos cropPos, BlockState state, long gameTime) {
        Long growthUntil = growthCooldowns.get(cropPos);
        if (growthUntil != null && gameTime < growthUntil) {
            return;
        }

        if (isMatureCrop(level, cropPos, state)) {
            harvestTargets.add(cropPos.immutable());
            growthCooldowns.remove(cropPos);
        } else {
            harvestTargets.remove(cropPos);
            growthCooldowns.put(cropPos.immutable(), gameTime + IMMATURE_GROWTH_COOLDOWN_TICKS);
        }
    }

    private boolean isMatureCrop(Level level, BlockPos cropPos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        }

        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty && "age".equals(integerProperty.getName())) {
                int maxAge = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(0);
                return state.getValue(integerProperty) >= maxAge;
            }
        }

        if (!state.is(BlockTags.CROPS)) {
            return false;
        }

        // Fallback for modded crops without explicit age properties:
        // if bonemeal no longer has effect, assume mature.
        return !level.getBlockState(cropPos).isRandomlyTicking();
    }

    private boolean isCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock
                || state.is(BlockTags.CROPS);
    }

    public static boolean canPlantCropSeedOn(Level level, BlockPos farmlandPos, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        BlockState plantState = block.defaultBlockState();
        if (!(block instanceof CropBlock) && !plantState.is(BlockTags.CROPS)) {
            return false;
        }

        if (!level.getBlockState(farmlandPos).is(Blocks.FARMLAND)) {
            return false;
        }

        BlockPos cropPos = farmlandPos.above();
        if (!level.getBlockState(cropPos).canBeReplaced()) {
            return false;
        }

        return plantState.canSurvive(level, cropPos);
    }

    private void resetBounds(BlockPos start, BlockPos end) {
        this.areaStart = start.immutable();
        this.areaEnd = end.immutable();

        this.minX = Math.min(start.getX(), end.getX());
        this.minY = Math.min(start.getY(), end.getY());
        this.minZ = Math.min(start.getZ(), end.getZ());

        this.maxX = Math.max(start.getX(), end.getX());
        this.maxY = Math.max(start.getY(), end.getY());
        this.maxZ = Math.max(start.getZ(), end.getZ());

        this.cursorX = minX;
        this.cursorY = minY;
        this.cursorZ = minZ;

        this.plantTargets.clear();
        this.harvestTargets.clear();
        this.growthCooldowns.clear();
        this.inaccessibleCooldowns.clear();

        this.initialized = true;
    }

    private void advanceCursor() {
        cursorX++;
        if (cursorX <= maxX) {
            return;
        }

        cursorX = minX;
        cursorZ++;
        if (cursorZ <= maxZ) {
            return;
        }

        cursorZ = minZ;
        cursorY++;
        if (cursorY > maxY) {
            cursorY = minY;
        }
    }

    private void pruneCooldowns(long gameTime) {
        growthCooldowns.entrySet().removeIf(entry -> gameTime >= entry.getValue());
        inaccessibleCooldowns.entrySet().removeIf(entry -> gameTime >= entry.getValue());
    }

    private boolean isInaccessible(BlockPos pos, long gameTime) {
        Long until = inaccessibleCooldowns.get(pos);
        return until != null && gameTime < until;
    }

    private void clear() {
        this.initialized = false;
        this.plantTargets.clear();
        this.harvestTargets.clear();
        this.growthCooldowns.clear();
        this.inaccessibleCooldowns.clear();
    }
}
