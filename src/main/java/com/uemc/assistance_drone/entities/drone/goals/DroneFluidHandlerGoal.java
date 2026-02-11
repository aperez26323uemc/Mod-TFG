package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * Goal that detects and removes fluid threats (water/lava) inside a configured build area.
 *
 * <p>
 * The implementation performs incremental, sectioned scanning of the build area to
 * locate fluid sources and external leaks. Threats are prioritized by a simple
 * scoring heuristic and queued for the drone to process. Scanning is throttled to
 * reduce tick cost and supports pause/resume across ticks.
 * </p>
 *
 * <p>
 * The Javadoc here aims to be concise and focused on intent rather than step-by-step
 * implementation details.
 * </p>
 *
 * @see DroneEntity
 * @see SitePlanner
 */
public class DroneFluidHandlerGoal extends Goal {

    /* ----------------------
       Configuration
       ---------------------- */

    private static final int MAX_SCAN_PER_TICK = 256;
    private static final int MAX_TRACE_NODES = 128;
    private static final int SCAN_COOLDOWN_TICKS = 10;
    private static final double COLLISION_TOLERANCE = 0.1;
    private static final int INFINITE_SOURCE_THRESHOLD = 2;
    private static final int BORDER_THICKNESS = 2;

    private static final List<Direction> TRACE_DIRECTIONS = List.of(
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    );

    /* ----------------------
       State
       ---------------------- */

    private final DroneEntity drone;
    private final Predicate<String> activationCondition;

    private final Set<BlockPos> fluidBlacklist = new HashSet<>();
    private final PriorityQueue<FluidThreat> fluidQueue = new PriorityQueue<>();
    private final Map<Long, Boolean> sectionSkipCache = new HashMap<>();

    private BlockPos targetPos;
    private int scanCooldown = SCAN_COOLDOWN_TICKS;

    /* Resume state when scanning incrementally */
    private int resumeX;
    private int resumeY;
    private int resumeZ;
    private int resumeSecY = Integer.MIN_VALUE;
    private int resumeSecX;
    private int resumeSecZ;
    private boolean hasResumeState = false;

    public DroneFluidHandlerGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /* ----------------------
       Goal lifecycle
       ---------------------- */

    @Override
    public boolean canUse() {
        if (!activationCondition.test(drone.getState())) return false;
        if (drone.getLogic().findSlotWithFluidRemoverBlock() == -1) return false;
        if (drone.getNavigation().isStuck()) return false;

        targetPos = getNextThreatFromQueue();
        if (targetPos != null) return true;

        if (scanCooldown-- > 0) return false;
        scanCooldown = SCAN_COOLDOWN_TICKS;

        scanForFluidThreats();
        targetPos = getNextThreatFromQueue();
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!activationCondition.test(drone.getState())) return false;
        if (drone.getLogic().findSlotWithFluidRemoverBlock() == -1) return false;
        if (drone.getNavigation().isStuck()) return false;

        if (targetPos == null) targetPos = getNextThreatFromQueue();
        if (targetPos == null) {
            scanForFluidThreats();
            targetPos = getNextThreatFromQueue();
        }
        return targetPos != null;
    }

    @Override
    public void stop() {
        targetPos = null;
        drone.getNavigation().stop();
    }

    @Override
    public boolean isInterruptable() {
        return Objects.equals(targetPos, drone.blockPosition());
    }

    @Override
    public void tick() {
        if (targetPos == null) return;

        drone.getLookControl().setLookAt(Vec3.atCenterOf(targetPos));

        if (handleDroneObstruction()) return;
        if (handleTargetApproach()) return;

        executeTargetAction();
    }

    /* ----------------------
       Execution helpers
       ---------------------- */

    /**
     * If the drone is physically occupying the target position, try to move it aside
     * to a free adjacent block. If no adjacent free block is found, blacklist the target.
     */
    private boolean handleDroneObstruction() {
        if (!isDroneInTheWay(targetPos)) return false;

        for (Direction dir : Direction.values()) {
            BlockPos adj = targetPos.relative(dir);
            if (drone.level().getBlockState(adj).getCollisionShape(drone.level(), adj).isEmpty()) {
                drone.getLogic().executeMovement(adj.getCenter());
                return true;
            }
        }

        fluidBlacklist.add(targetPos);
        targetPos = null;
        return true;
    }

    /**
     * Ensure the target is reachable and within interaction range; otherwise move toward it
     * or blacklist it if inaccessible.
     */
    private boolean handleTargetApproach() {
        if (drone.getLogic().isInRangeToInteract(targetPos)) return false;

        if (!drone.getLogic().isBlockAccessible(targetPos)) {
            blacklistPositionAndAdjacent(targetPos);
            targetPos = null;
            drone.getNavigation().stop();
            return true;
        }

        drone.getLogic().executeMovement(Vec3.atCenterOf(targetPos));
        return true;
    }

    /**
     * Stop navigation and attempt to place a remover block at the target.
     * If placement succeeds, clear related blacklist entries.
     */
    private void executeTargetAction() {
        drone.getNavigation().stop();

        if (placeBlock(targetPos)) {
            removeFromBlacklistWithAdjacent(targetPos);
        }

        targetPos = null;
    }

    /* ----------------------
       Priority queue
       ---------------------- */

    private BlockPos getNextThreatFromQueue() {
        while (!fluidQueue.isEmpty()) {
            FluidThreat threat = fluidQueue.poll();
            if (!fluidBlacklist.contains(threat.pos)) return threat.pos;
        }
        return null;
    }

    private int calculatePriority(BlockPos pos, AABB site) {
        int priority = 0;

        int dist = getDistanceToBorder(pos, site);
        if (dist == 0) {
            priority += 10_000;
        } else if (dist <= BORDER_THICKNESS) {
            priority += 5_000 - dist * 1_000;
        } else {
            priority += 100;
        }

        priority += pos.getY();

        if (!site.contains(Vec3.atCenterOf(pos))) {
            priority += 20_000;
        }

        return priority;
    }

    private int getDistanceToBorder(BlockPos pos, AABB site) {
        int x = pos.getX();
        int z = pos.getZ();
        return Math.min(
                Math.min(Math.abs(x - (int) site.minX), Math.abs(x - (int) site.maxX)),
                Math.min(Math.abs(z - (int) site.minZ), Math.abs(z - (int) site.maxZ))
        );
    }

    /* ----------------------
       Incremental scanning
       ---------------------- */

    /**
     * Scan the configured site area incrementally, adding detected threats to the queue.
     * The method can pause early to remain within a CPU budget per tick; resume state is preserved.
     */
    @SuppressWarnings("DataFlowIssue")
    private void scanForFluidThreats() {
        ItemStack planner = drone.getInventory().getStackInSlot(0);
        if (!SitePlanner.isConfigured(planner)) return;

        BlockPos start = SitePlanner.getStartPos(planner);
        BlockPos end = SitePlanner.getEndPos(planner);

        int effectiveEndY = getVerticalScanLimit(start, end.getY());
        boolean scanningDown = effectiveEndY < start.getY();

        int siteMinX = Math.min(start.getX(), end.getX());
        int siteMaxX = Math.max(start.getX(), end.getX());
        int siteMinZ = Math.min(start.getZ(), end.getZ());
        int siteMaxZ = Math.max(start.getZ(), end.getZ());

        AABB site = new AABB(
                siteMinX,
                Math.min(start.getY(), effectiveEndY),
                siteMinZ,
                siteMaxX + 1,
                Math.max(start.getY(), effectiveEndY) + 1,
                siteMaxZ + 1
        );

        int startSectionY = start.getY() >> 4;
        int endSectionY = effectiveEndY >> 4;

        initializeResumeStateIfNeeded(startSectionY, siteMinX, siteMinZ);

        ScanContext context = new ScanContext(
                site, scanningDown, siteMinX, siteMaxX, siteMinZ, siteMaxZ,
                startSectionY, endSectionY, effectiveEndY, start.getY()
        );

        boolean completed = scanSections(context);

        if (completed) {
            hasResumeState = false;
            sectionSkipCache.clear();
        }
    }

    private void initializeResumeStateIfNeeded(int startSectionY, int siteMinX, int siteMinZ) {
        if (!hasResumeState) {
            resumeSecY = startSectionY;
            resumeSecX = siteMinX >> 4;
            resumeSecZ = siteMinZ >> 4;
            resumeY = -1;
            hasResumeState = true;
        }
    }

    private boolean scanSections(ScanContext ctx) {
        int scanned = 0;

        while (shouldContinueSectionY(ctx)) {
            if (!scanSectionXZ(ctx, scanned)) {
                return false;
            }
            resumeSecX = ctx.siteMinX >> 4;
            resumeSecY += ctx.scanningDown ? -1 : 1;
        }
        return true;
    }

    private boolean shouldContinueSectionY(ScanContext ctx) {
        return ctx.scanningDown ? resumeSecY >= ctx.endSectionY : resumeSecY <= ctx.endSectionY;
    }

    private boolean scanSectionXZ(ScanContext ctx, int scannedCount) {
        while (resumeSecX <= ctx.siteMaxX >> 4) {
            if (!scanSectionZ(ctx, scannedCount)) return false;
            resumeSecZ = ctx.siteMinZ >> 4;
            resumeSecX++;
        }
        return true;
    }

    private boolean scanSectionZ(ScanContext ctx, int scannedCount) {
        while (resumeSecZ <= ctx.siteMaxZ >> 4) {
            long key = SectionPos.asLong(resumeSecX, resumeSecY, resumeSecZ);
            Boolean skip = sectionSkipCache.computeIfAbsent(key,
                    k -> shouldSkipSection(resumeSecX, resumeSecY << 4, resumeSecZ));

            if (!skip) {
                SectionBounds bounds = calculateSectionBounds(ctx);
                if (!scanBlocksInSection(ctx, bounds, scannedCount)) {
                    hasResumeState = true;
                    return false;
                }
            }

            resumeSecZ++;
            resumeY = -1;
        }
        return true;
    }

    private SectionBounds calculateSectionBounds(ScanContext ctx) {
        int x0 = Math.max(ctx.siteMinX, resumeSecX << 4);
        int x1 = Math.min(ctx.siteMaxX, (resumeSecX << 4) + 15);
        int z0 = Math.max(ctx.siteMinZ, resumeSecZ << 4);
        int z1 = Math.min(ctx.siteMaxZ, (resumeSecZ << 4) + 15);

        int secMinY = resumeSecY << 4;
        int secMaxY = secMinY + 15;

        int globalMinY = Math.min(ctx.startY, ctx.effectiveEndY);
        int globalMaxY = Math.max(ctx.startY, ctx.effectiveEndY);

        int y0 = Math.max(globalMinY, secMinY);
        int y1 = Math.min(globalMaxY, secMaxY);

        return new SectionBounds(x0, x1, y0, y1, z0, z1);
    }

    private boolean scanBlocksInSection(ScanContext ctx, SectionBounds bounds, int scannedCount) {
        if (resumeY == -1) {
            initializeBlockIterators(ctx, bounds);
        }
        return scanBlocksYXZ(ctx, bounds, scannedCount);
    }

    private void initializeBlockIterators(ScanContext ctx, SectionBounds bounds) {
        resumeY = ctx.scanningDown ? bounds.y1 : bounds.y0;
        resumeX = bounds.x0;
        resumeZ = bounds.z0;
    }

    private boolean scanBlocksYXZ(ScanContext ctx, SectionBounds bounds, int scannedCount) {
        while (shouldContinueY(ctx, bounds)) {
            if (!scanBlocksXZ(ctx, bounds, scannedCount)) return false;
            resumeX = bounds.x0;
            resumeY += ctx.scanningDown ? -1 : 1;
        }
        return true;
    }

    private boolean shouldContinueY(ScanContext ctx, SectionBounds bounds) {
        return ctx.scanningDown ? resumeY >= bounds.y0 : resumeY <= bounds.y1;
    }

    private boolean scanBlocksXZ(ScanContext ctx, SectionBounds bounds, int scannedCount) {
        while (resumeX <= bounds.x1) {
            if (!scanBlocksZ(ctx, bounds, scannedCount)) return false;
            resumeZ = bounds.z0;
            resumeX++;
        }
        return true;
    }

    private boolean scanBlocksZ(ScanContext ctx, SectionBounds bounds, int scannedCount) {
        while (resumeZ <= bounds.z1) {
            scannedCount++;
            if (scannedCount > MAX_SCAN_PER_TICK) return false;

            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(resumeX, resumeY, resumeZ);
            BlockPos threat = analyzeBlockForFluid(cursor, ctx.site);

            if (threat != null) {
                fluidQueue.offer(new FluidThreat(threat, calculatePriority(threat, ctx.site)));
            }
            resumeZ++;
        }
        return true;
    }

    private int getVerticalScanLimit(BlockPos start, int targetY) {
        Vec3 from = Vec3.atCenterOf(start);
        Vec3 to = new Vec3(from.x, targetY, from.z);

        BlockHitResult hit = drone.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, drone
        ));

        if (hit.getType() != HitResult.Type.MISS) {
            return hit.getBlockPos().getY();
        }
        return targetY + 1;
    }

    private boolean shouldSkipSection(int secX, int y, int secZ) {
        Level level = drone.level();
        LevelChunk chunk = level.getChunk(secX, secZ);
        if (chunk == null) return true;

        int index = chunk.getSectionIndex(y);
        if (index < 0 || index >= chunk.getSectionsCount()) return true;

        LevelChunkSection section = chunk.getSection(index);
        if (section.hasOnlyAir()) return true;

        return !section.getStates().maybeHas(state -> !state.getFluidState().isEmpty());
    }

    /* ----------------------
       Fluid analysis
       ---------------------- */

    private BlockPos analyzeBlockForFluid(BlockPos.MutableBlockPos cursor, AABB site) {
        FluidState fluid = drone.level().getFluidState(cursor);
        if (fluid.isEmpty()) return null;

        BlockState state = drone.level().getBlockState(cursor);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) &&
                state.getValue(BlockStateProperties.WATERLOGGED)) {
            return null;
        }

        BlockPos pos = cursor.immutable();

        if (fluid.isSource()) {
            if (isBlacklistedAndBlocked(pos)) return null;
            return resolveInfiniteSource(pos);
        }

        return detectExternalLeak(pos, site);
    }

    private BlockPos detectExternalLeak(BlockPos internal, AABB site) {
        Level level = drone.level();

        for (Direction dir : TRACE_DIRECTIONS) {
            BlockPos next = internal.relative(dir);

            if (site.contains(Vec3.atCenterOf(next))) continue;
            if (level.getFluidState(next).isEmpty()) continue;

            BlockPos resolved = resolveLeakPath(next, site);
            if (resolved != null && !isBlacklistedAndBlocked(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    @Nullable
    private BlockPos resolveLeakPath(BlockPos start, AABB site) {
        Level level = drone.level();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        int steps = 0;
        while (!queue.isEmpty() && steps++ < MAX_TRACE_NODES) {
            BlockPos pos = queue.poll();

            if (!isInsideHorizontal(site, pos)) return pos;

            FluidState fluid = level.getFluidState(pos);
            if (fluid.isSource() && level.getBlockState(pos).canBeReplaced()) {
                return pos;
            }

            for (Direction dir : TRACE_DIRECTIONS) {
                BlockPos next = pos.relative(dir);
                if (!visited.add(next)) continue;
                if (!level.getFluidState(next).isEmpty()) queue.add(next);
            }
        }
        return null;
    }

    private boolean isInsideHorizontal(AABB site, BlockPos pos) {
        return pos.getX() >= site.minX && pos.getX() < site.maxX
                && pos.getZ() >= site.minZ && pos.getZ() < site.maxZ;
    }

    private BlockPos resolveInfiniteSource(BlockPos pos) {
        int sources = 0;
        BlockPos best = null;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(dir);
            if (!drone.level().getFluidState(n).isSource()) continue;

            sources++;
            if (drone.level().getBlockState(n).canBeReplaced()) best = n;
        }

        return (sources >= INFINITE_SOURCE_THRESHOLD && best != null) ? best : pos;
    }

    /* ----------------------
       Utilities
       ---------------------- */

    private boolean placeBlock(BlockPos pos) {
        int slot = drone.getLogic().findSlotWithFluidRemoverBlock();
        if (slot == -1) return false;

        ItemStack stack = drone.getInventory().getStackInSlot(slot);
        return drone.getLogic().placeBlock(pos, stack);
    }

    private boolean isDroneInTheWay(BlockPos pos) {
        return drone.getBoundingBox().intersects(new AABB(pos).inflate(COLLISION_TOLERANCE));
    }

    private boolean isBlacklistedAndBlocked(BlockPos pos) {
        if (!fluidBlacklist.contains(pos)) return false;
        if (!drone.getLogic().isBlockAccessible(pos)) return true;
        fluidBlacklist.remove(pos);
        return false;
    }

    private void blacklistPositionAndAdjacent(BlockPos pos) {
        fluidBlacklist.add(pos);
        for (Direction dir : TRACE_DIRECTIONS) fluidBlacklist.add(pos.relative(dir));
    }

    private void removeFromBlacklistWithAdjacent(BlockPos pos) {
        fluidBlacklist.remove(pos);
        for (Direction dir : Direction.values()) fluidBlacklist.remove(pos.relative(dir));
    }

    /* ----------------------
       Aux classes / records
       ---------------------- */

    private record FluidThreat(BlockPos pos, int priority) implements Comparable<FluidThreat> {
        @Override
        public int compareTo(FluidThreat o) {
            return Integer.compare(o.priority, this.priority);
        }
    }

    private record ScanContext(AABB site,
                               boolean scanningDown,
                               int siteMinX, int siteMaxX,
                               int siteMinZ, int siteMaxZ,
                               int startSectionY, int endSectionY,
                               int effectiveEndY, int startY) { }

    private record SectionBounds(int x0, int x1, int y0, int y1, int z0, int z1) { }
}
