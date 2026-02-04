package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;

public class DroneFluidHandlerGoal extends Goal { /* ========================== CONFIG ========================== */
    private static final int MAX_SCAN_PER_TICK = 400;
    private static final int MAX_TRACE_NODES = 256; /* ========================== TYPES ========================== */
    private static final Logger LOGGER = LoggerFactory.getLogger(DroneFluidHandlerGoal.class);
    private final DroneEntity drone;
    private final Predicate<String> activationCondition;
    /**
     * Blacklist dinámica de fluidos inaccesibles
     */
    private final Set<BlockPos> fluidBlacklist = new HashSet<>();
    private FluidTarget target;
    private int scanCooldown = 10;
    private int lastScanY = Integer.MIN_VALUE;
    public DroneFluidHandlerGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    } /* ========================== GOAL LIFECYCLE ========================== */

    @Override
    public boolean canUse() {
        if (!activationCondition.test(drone.getState())) return false;
        if (drone.getLogic().findSlotWithFluidRemoverBlock() == -1) return false;
        if (scanCooldown-- > 0) return false;
        scanCooldown = 10;
        target = scanForFluidThreat();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return activationCondition.test(drone.getState()) && target != null && drone.getLogic().findSlotWithFluidRemoverBlock() != -1 && !drone.getNavigation().isStuck();
    }

    @Override
    public void stop() {
        target = null;
        lastScanY = Integer.MIN_VALUE;
        drone.getNavigation().stop();
        LOGGER.debug("FLUID BLACKLIST: {}", fluidBlacklist);
        LOGGER.debug("FLUID STOP");
    } /* ========================== MAIN TICK ========================== */

    @Override
    public void tick() {
        if (target == null) return;
        drone.getLookControl().setLookAt(target.pos().getX() + 0.5, target.pos().getY(), target.pos().getZ() + 0.5);
        if (isDroneInTheWay(target.pos())) {
            for (Direction dir : Direction.values()) {
                BlockPos pos = target.pos().relative(dir);
                BlockState state = this.drone.level().getBlockState(pos);
                if(state.getCollisionShape(drone.level(), pos).isEmpty()) {
                    drone.getLogic().executeMovement(pos.getCenter());
                    return;
                }
            }
            fluidBlacklist.add(target.pos());
            return;
        }
        LOGGER.debug("FLUID TARGET POSITION: {}", target.pos());
        if (!drone.getLogic().isInRangeToInteract(target.pos())) {
            if (!drone.getLogic().isBlockAccessible(target.pos())) {
                fluidBlacklist.add(target.pos());
                for (Direction dir : Direction.values()) {
                    fluidBlacklist.add(target.pos().relative(dir));
                }
                target = null;
                drone.getNavigation().stop();
                return;
            }
            Vec3 targetvec = Vec3.atCenterOf(target.pos());
            LOGGER.debug("FLUID TARGET: {}", targetvec);
            LOGGER.debug("FLUID TARGET POS: {}", target.pos());
            drone.getLogic().executeMovement(targetvec);
            return;
        }
        drone.getNavigation().stop();
        boolean success = false;
        switch (target.action()) {
            case PLACE_BLOCK -> success = placeBlock(target.pos());
            case REMOVE_INTERNAL_SOURCE -> success = removeInternalSource(target.pos());
        }
        if (success) {
            fluidBlacklist.remove(target.pos());
            for (Direction dir : Direction.values()) {
                fluidBlacklist.remove(target.pos().relative(dir));
            }
        }
        target = null;
    } /* ========================== SCAN LOGIC ========================== */

    private FluidTarget scanForFluidThreat() {
        ItemStack planner = drone.getInventory().getStackInSlot(0);
        if (!SitePlanner.isConfigured(planner)) return null;
        BlockPos a = SitePlanner.getStartPos(planner);
        BlockPos b = SitePlanner.getEndPos(planner);
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        AABB site = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        if (lastScanY == Integer.MIN_VALUE || lastScanY < minY || lastScanY > maxY) {
            lastScanY = maxY;
        }
        int scanned = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = lastScanY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (++scanned > MAX_SCAN_PER_TICK) {
                        lastScanY = y;
                        return null;
                    }
                    FluidState fluid = drone.level().getFluidState(cursor);
                    if (fluid.isEmpty()) continue;
                    BlockState state = drone.level().getBlockState(cursor);
                    if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
                        continue;
                    }
                    if (fluid.isSource()) {
                        if (fluidBlacklist.contains(cursor) && isSourceStillBlocked(cursor.immutable())) {
                            continue;
                        }
                        fluidBlacklist.remove(cursor);
                        BlockPos resolved = resolveInfiniteSource(cursor.immutable());
                        if (resolved.equals(cursor)) {
                            return new FluidTarget(cursor.immutable(), Action.REMOVE_INTERNAL_SOURCE);
                        }
                        return new FluidTarget(resolved, Action.PLACE_BLOCK);
                    }
                    FluidTarget leak = detectExternalLeak(cursor.immutable(), site);
                    if (leak != null) return leak;
                }
            }
        }
        lastScanY = Integer.MIN_VALUE;
        return null;
    } /* ========================== BLACKLIST RESOLUTION ========================== */

    private boolean isSourceStillBlocked(BlockPos source) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(source);
        visited.add(source);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (drone.getLogic().isBlockAccessible(current)) {
                return false;
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (!visited.add(next)) continue;
                FluidState fluid = drone.level().getFluidState(next);
                if (!fluid.isEmpty()) {
                    queue.add(next);
                }
            }
        }
        return true;
    } /* ========================== LEAK DETECTION ========================== */

    private FluidTarget detectExternalLeak(BlockPos internal, AABB site) {
        Level level = drone.level();
        BlockPos up = internal.above();
        if (!site.contains(Vec3.atCenterOf(up)) && !level.getFluidState(up).isEmpty()) {
            return traceExternalFluid(up, site);
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = internal.relative(dir);
            if (!site.contains(Vec3.atCenterOf(side)) && !level.getFluidState(side).isEmpty() && level.getBlockState(side).canBeReplaced()) {
                return new FluidTarget(side, Action.PLACE_BLOCK);
            }
        }
        return null;
    }

    private FluidTarget traceExternalFluid(BlockPos start, AABB site) {
        Level level = drone.level();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        int steps = 0;
        while (!queue.isEmpty() && steps++ < MAX_TRACE_NODES) {
            BlockPos pos = queue.poll();
            FluidState fluid = level.getFluidState(pos);
            if (fluid.isEmpty()) continue;
            if (fluid.isSource() && level.getBlockState(pos).canBeReplaced()) {
                return new FluidTarget(pos, Action.PLACE_BLOCK);
            }
            for (Direction dir : List.of(Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                BlockPos next = pos.relative(dir);
                if (visited.contains(next)) continue;
                if (site.contains(Vec3.atCenterOf(next))) continue;
                if (!level.getFluidState(next).isEmpty()) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return null;
    } /* ========================== SOURCE LOGIC ========================== */

    private BlockPos resolveInfiniteSource(BlockPos pos) {
        int sources = 0;
        BlockPos best = null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(dir);
            if (drone.level().getFluidState(n).isSource()) {
                sources++;
                if (drone.level().getBlockState(n).canBeReplaced()) {
                    best = n;
                }
            }
        }
        return (sources >= 2 && best != null) ? best : pos;
    }

    private boolean removeInternalSource(BlockPos pos) {
        return placeBlock(pos);
    }

    private boolean placeBlock(BlockPos pos) {
        int slot = drone.getLogic().findSlotWithFluidRemoverBlock();
        if (slot == -1) return false;
        Level level = drone.level();
        BlockState state = level.getBlockState(pos);
        if (!state.canBeReplaced() && !(state.getBlock() instanceof LiquidBlock)) return false;
        ItemStack stack = drone.getInventory().getStackInSlot(slot);
        return drone.getLogic().placeBlock(pos, stack);
    }

    private boolean isDroneInTheWay(BlockPos pos) {
        return drone.getBoundingBox().intersects(new AABB(pos).inflate(0.1));
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    private enum Action {PLACE_BLOCK, REMOVE_INTERNAL_SOURCE}

    private record FluidTarget(BlockPos pos, Action action) {
    }
}