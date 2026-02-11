package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Predicate;

/**
 * Goal responsible for systematic block mining inside a configured Site Planner area.
 *
 * <p>
 * Mining is performed layer by layer using a spiral traversal strategy.
 * The goal distinguishes between:
 * </p>
 *
 * <ul>
 *     <li><b>Primary job targets</b> — blocks inside the configured area.</li>
 *     <li><b>Obstacle targets</b> — temporary blocks obstructing navigation.</li>
 * </ul>
 *
 * <p>
 * Navigation uses a lightweight Manhattan-style waypoint strategy to avoid
 * trivial pathfinding stalls while keeping movement predictable.
 * </p>
 *
 * @see DroneEntity
 * @see SitePlanner
 */
public class DroneMineGoal extends Goal {

    private final DroneEntity drone;
    private final Predicate<String> activationCondition;

    /* ----------------------
       Runtime state
       ---------------------- */

    private int checkCooldown = 0;

    private BlockPos currentJobTarget = null;
    private BlockPos obstacleTarget = null;

    /* Navigation strategy */
    private final Queue<BlockPos> waypoints = new LinkedList<>();
    private final SpiralLayerIterator layerIterator;

    public DroneMineGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        this.layerIterator = new SpiralLayerIterator(this);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /* ----------------------
       Goal lifecycle
       ---------------------- */

    @Override
    public boolean canUse() {
        if (!activationCondition.test(drone.getState())) return false;

        ItemStack planner = drone.getInventory().getStackInSlot(0);
        if (!SitePlanner.isConfigured(planner)) return false;

        if (this.checkCooldown-- > 0) return false;

        if (hasMiningTargets(planner)) {
            this.checkCooldown = 20;
            return true;
        } else {
            this.checkCooldown = 60;
            return false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return activationCondition.test(drone.getState())
                && (this.obstacleTarget != null
                || this.currentJobTarget != null
                || !this.layerIterator.isFinished());
    }

    @Override
    public void start() {
        this.currentJobTarget = null;
        this.obstacleTarget = null;
        this.waypoints.clear();

        ItemStack planner = drone.getInventory().getStackInSlot(0);
        BlockPos start = SitePlanner.getStartPos(planner);
        BlockPos end = SitePlanner.getEndPos(planner);

        if (start != null && end != null) {
            this.layerIterator.reset(start, end);
        }
    }

    @Override
    public void stop() {
        this.currentJobTarget = null;
        this.obstacleTarget = null;
        this.waypoints.clear();

        drone.getLogic().resetMiningState();
        drone.getNavigation().stop();

        Vec3 safe = drone.getLogic().calculateIdealPosition(
                drone.getX(), drone.getY(), drone.getZ());
        drone.getLogic().executeMovement(safe);
    }

    @Override
    public boolean isInterruptable() {
        return true;
    }

    /* ----------------------
       Main tick
       ---------------------- */

    @Override
    public void tick() {
        Level level = this.drone.level();
        if (level.isClientSide) return;

        BlockPos suffocating = drone.getLogic().getSuffocatingBlock();
        if (suffocating != null) {
            obstacleTarget = suffocating;
        }

        BlockPos activeTarget =
                (obstacleTarget != null) ? obstacleTarget : currentJobTarget;

        if (activeTarget == null) {
            BlockPos next = this.layerIterator.next();
            if (next == null) return;

            this.currentJobTarget = next;
            activeTarget = next;
            this.waypoints.clear();
        }

        Vec3 targetVec = Vec3.atCenterOf(activeTarget);
        drone.getLookControl().setLookAt(targetVec);

        if (drone.getLogic().isInRangeToInteract(activeTarget)) {
            drone.getNavigation().stop();

            boolean broken = drone.getLogic().mineBlock(activeTarget);

            if (broken) {
                if (obstacleTarget != null) {
                    obstacleTarget = null;
                } else {
                    currentJobTarget = null;
                }
            }
        } else {
            drone.getLogic().resetMiningState();
            processNavigation(activeTarget);
        }
    }

    /* ----------------------
       Navigation strategy
       ---------------------- */

    private void processNavigation(BlockPos target) {
        if (target.equals(obstacleTarget)) {
            drone.getLogic().executeMovement(Vec3.atCenterOf(target));
            return;
        }

        if (waypoints.isEmpty()) {
            if (drone.getLogic().isBlockAccessible(target)) {
                drone.getLogic().executeMovement(Vec3.atCenterOf(target));
            } else {
                calculateCornerPath(drone.blockPosition(), target);
            }
            return;
        }

        BlockPos nextCorner = waypoints.peek();

        if (drone.blockPosition().distManhattan(nextCorner) <= 1) {
            waypoints.poll();
            return;
        }

        BlockPos obstruction =
                drone.getLogic().getObstructionBlock(nextCorner);

        if (obstruction != null
                && drone.getLogic().isValidMiningTarget(obstruction)) {
            this.obstacleTarget = obstruction;
            return;
        }

        drone.getLogic().executeMovement(Vec3.atCenterOf(nextCorner));
    }

    /**
     * Computes a simple L-shaped Manhattan path:
     * X → Z → Y alignment.
     * This avoids trivial navigation stalls without invoking heavy pathfinding.
     */
    private void calculateCornerPath(BlockPos start, BlockPos end) {
        waypoints.clear();

        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();

        if (x != end.getX()) {
            x = end.getX();
            waypoints.add(new BlockPos(x, y, z));
        }
        if (z != end.getZ()) {
            z = end.getZ();
            waypoints.add(new BlockPos(x, y, z));
        }
        if (y != end.getY()) {
            waypoints.add(new BlockPos(x, end.getY(), z));
        }

        if (waypoints.isEmpty() || !waypoints.contains(end)) {
            waypoints.add(end);
        }
    }

    /* ----------------------
       Target detection
       ---------------------- */

    private boolean hasMiningTargets(ItemStack planner) {
        BlockPos start = SitePlanner.getStartPos(planner);
        BlockPos end = SitePlanner.getEndPos(planner);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int minX = Math.min(start.getX(), end.getX());
        int maxX = Math.max(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int maxY = Math.max(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        int maxZ = Math.max(start.getZ(), end.getZ());

        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (drone.getLogic().isValidMiningTarget(cursor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /* ----------------------
       Spiral layer iterator
       ---------------------- */

    /**
     * Iterates blocks layer by layer using a rectangular spiral.
     * The iterator:
     * <ul>
     *     <li>Processes one Y layer at a time.</li>
     *     <li>Traverses each layer in spiral order.</li>
     *     <li>Delegates block validity checks to DroneAiLogic.</li>
     * </ul>
     */
    private static class SpiralLayerIterator {

        private final Queue<BlockPos> queue = new LinkedList<>();
        private final DroneMineGoal goal;

        private int currentY, endY, startY;
        private int rawStartX, rawStartZ, rawEndX, rawEndZ;
        private boolean finished = false;

        public SpiralLayerIterator(DroneMineGoal goal) {
            this.goal = goal;
        }

        public void reset(BlockPos start, BlockPos end) {
            this.startY = start.getY();
            this.endY = end.getY();
            this.currentY = startY;
            this.rawStartX = start.getX();
            this.rawStartZ = start.getZ();
            this.rawEndX = end.getX();
            this.rawEndZ = end.getZ();
            this.finished = false;
            this.queue.clear();
            generateSpiralForLayer(currentY);
        }

        public BlockPos next() {
            if (finished) return null;

            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                if (goal.drone.getLogic().isValidMiningTarget(pos)) {
                    return pos;
                }
            }

            if (hasBlocksLeftInLayer(currentY)) {
                generateSpiralForLayer(currentY);
                return next();
            }

            if (currentY == endY) {
                finished = true;
                return null;
            }

            currentY += (endY > startY) ? 1 : -1;
            generateSpiralForLayer(currentY);
            return next();
        }

        public boolean isFinished() {
            return finished;
        }

        private boolean hasBlocksLeftInLayer(int y) {
            int minX = Math.min(rawStartX, rawEndX);
            int maxX = Math.max(rawStartX, rawEndX);
            int minZ = Math.min(rawStartZ, rawEndZ);
            int maxZ = Math.max(rawStartZ, rawEndZ);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (goal.drone.getLogic().isValidMiningTarget(p)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void generateSpiralForLayer(int y) {
            queue.clear();

            int currentMinX = Math.min(rawStartX, rawEndX);
            int currentMaxX = Math.max(rawStartX, rawEndX);
            int currentMinZ = Math.min(rawStartZ, rawEndZ);
            int currentMaxZ = Math.max(rawStartZ, rawEndZ);

            int direction;
            if (rawStartX == currentMinX && rawStartZ == currentMinZ) direction = 0;
            else if (rawStartX == currentMaxX && rawStartZ == currentMinZ) direction = 1;
            else if (rawStartX == currentMaxX && rawStartZ == currentMaxZ) direction = 2;
            else direction = 3;

            while (currentMinX <= currentMaxX && currentMinZ <= currentMaxZ) {
                switch (direction) {
                    case 0 -> {
                        for (int x = currentMinX; x <= currentMaxX; x++)
                            queue.add(new BlockPos(x, y, currentMinZ));
                        currentMinZ++;
                    }
                    case 1 -> {
                        for (int z = currentMinZ; z <= currentMaxZ; z++)
                            queue.add(new BlockPos(currentMaxX, y, z));
                        currentMaxX--;
                    }
                    case 2 -> {
                        for (int x = currentMaxX; x >= currentMinX; x--)
                            queue.add(new BlockPos(x, y, currentMaxZ));
                        currentMaxZ--;
                    }
                    case 3 -> {
                        for (int z = currentMaxZ; z >= currentMinZ; z--)
                            queue.add(new BlockPos(currentMinX, y, z));
                        currentMinX++;
                    }
                }
                direction = (direction + 1) % 4;
            }
        }
    }
}
