package com.uemc.pickup_dumper;

import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Amortized cache of physical inventory blocks located on the exterior shell
 * of a {@link SitePlanner}-defined area.
 *
 * <h2>Validity criteria</h2>
 * A block position is considered a valid dump target if and only if:
 * <ol>
 *   <li>Its {@code BlockState} belongs to the tag
 *       {@code #pickup_dumper:valid_dump_containers} (whitelist approach).</li>
 *   <li>It exposes an {@link IItemHandler} with at least one slot on any face.</li>
 * </ol>
 *
 * <h2>Performance model</h2>
 * The perimeter scan is distributed across multiple ticks via an internal
 * cursor, consuming at most {@value #SCAN_BLOCKS_PER_TICK} positions per tick.
 * A per-tick guard ensures the cursor never advances more than once per game
 * tick regardless of how many call-sites invoke {@link #tick}.
 * Once a full scan completes, results are reused for {@value #RESCAN_INTERVAL_TICKS}
 * ticks before the next scan is scheduled.
 *
 * <h2>Thread safety</h2>
 * This class is designed for single-threaded server-tick use only.
 */
public final class PerimeterContainerCache {

    private static final int  SCAN_BLOCKS_PER_TICK   = 64;
    private static final long RESCAN_INTERVAL_TICKS   = 300L;

    private final List<BlockPos> validContainers    = new ArrayList<>();
    private List<BlockPos>       pendingPositions   = null;
    private int                  scanCursor         = 0;
    private boolean              scanComplete       = true;
    private long                 nextRescanAt       = 0L;
    private long                 lastTickProcessed  = Long.MIN_VALUE;
    private BlockPos             lastKnownStart     = null;
    private BlockPos             lastKnownEnd       = null;

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Advances the scan state machine by at most {@value #SCAN_BLOCKS_PER_TICK}
     * positions. Must be called once per game tick from the owning goal.
     *
     * <p>An internal guard prevents redundant processing if this method is
     * called multiple times within the same game tick.
     *
     * @param level   the server-side level
     * @param planner the configured {@link SitePlanner} item stack
     */
    public void tick(Level level, ItemStack planner) {
        if (level.isClientSide() || !SitePlanner.isConfigured(planner)) return;

        long now = level.getGameTime();
        if (now == lastTickProcessed) return;
        lastTickProcessed = now;

        BlockPos start = SitePlanner.getStartPos(planner);
        BlockPos end   = SitePlanner.getEndPos(planner);

        if (!Objects.equals(start, lastKnownStart) || !Objects.equals(end, lastKnownEnd)) {
            invalidate(start, end);
        }

        if (scanComplete && now >= nextRescanAt) {
            scheduleScan(start, end);
        }

        if (!scanComplete && pendingPositions != null) {
            advanceScan(level, now);
        }
    }

    /**
     * Returns {@code true} if the cache contains at least one known container.
     */
    public boolean hasValidContainers() {
        return !validContainers.isEmpty();
    }

    /**
     * Returns a copy of the container list sorted by ascending Manhattan
     * distance from {@code origin}.
     *
     * @param origin reference position for distance sorting
     */
    public List<BlockPos> getSortedContainers(BlockPos origin) {
        List<BlockPos> sorted = new ArrayList<>(validContainers);
        sorted.sort(Comparator.comparingInt(p -> p.distManhattan(origin)));
        return sorted;
    }

    /**
     * Removes {@code pos} from the cache, e.g. when the block is destroyed or
     * has no remaining capacity for any item the drone carries.
     */
    public void evict(BlockPos pos) {
        validContainers.remove(pos);
    }

    /**
     * Invalidates all cached data and resets scan state. Should be called when
     * the owning goal stops to ensure stale data is not reused across activations.
     */
    public void reset() {
        validContainers.clear();
        pendingPositions  = null;
        scanComplete      = false;
        nextRescanAt      = 0L;
        lastKnownStart    = null;
        lastKnownEnd      = null;
    }

    /**
     * Queries the first available {@link IItemHandler} for {@code pos} by
     * probing all six faces.
     *
     * @param level the server-side level
     * @param pos   the block position to query
     * @return a valid handler, or {@code null} if the block exposes none
     */
    public static IItemHandler getHandler(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (h != null && h.getSlots() > 0) return h;
        }
        return null;
    }

    // ----------------------------------------------------------------
    // Internal scan logic
    // ----------------------------------------------------------------

    private void invalidate(BlockPos start, BlockPos end) {
        lastKnownStart = start;
        lastKnownEnd   = end;
        validContainers.clear();
        pendingPositions = null;
        scanComplete     = false;
        nextRescanAt     = 0L;
        scheduleScan(start, end);
    }

    private void scheduleScan(BlockPos start, BlockPos end) {
        validContainers.clear();
        pendingPositions = buildPerimeter(start, end);
        scanCursor   = 0;
        scanComplete = false;
    }

    private void advanceScan(Level level, long now) {
        int limit = Math.min(scanCursor + SCAN_BLOCKS_PER_TICK, pendingPositions.size());

        for (int i = scanCursor; i < limit; i++) {
            BlockPos pos = pendingPositions.get(i);
            if (!level.isLoaded(pos)) continue;
            if (isValidContainer(level, pos)) validContainers.add(pos);
        }

        scanCursor = limit;

        if (scanCursor >= pendingPositions.size()) {
            scanComplete     = true;
            nextRescanAt     = now + RESCAN_INTERVAL_TICKS;
            pendingPositions = null;
        }
    }

    /**
     * Evaluates whether a block qualifies as a valid dump container.
     *
     * <p>{@code BlockState.is(TagKey)} is O(1): Minecraft pre-computes tag
     * membership into integer bitsets when the world loads. This makes the tag
     * check cheaper than any string-based comparison.
     */
    private static boolean isValidContainer(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModTags.VALID_DUMP_CONTAINERS)) return false;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;

        return getHandler(level, pos) != null;
    }

    /**
     * Builds the list of positions forming the exterior shell of the selection
     * (outer AABB inflated by 1, minus the inner AABB).
     *
     * <p>For the maximum selection size of 48×48×48 this produces at most
     * ~14 400 positions, requiring ~225 ticks to scan completely at
     * {@value #SCAN_BLOCKS_PER_TICK} positions/tick (~11 s at 20 TPS).
     */
    private static List<BlockPos> buildPerimeter(BlockPos start, BlockPos end) {
        int inMinX = Math.min(start.getX(), end.getX());
        int inMaxX = Math.max(start.getX(), end.getX());
        int inMinY = Math.min(start.getY(), end.getY());
        int inMaxY = Math.max(start.getY(), end.getY());
        int inMinZ = Math.min(start.getZ(), end.getZ());
        int inMaxZ = Math.max(start.getZ(), end.getZ());

        List<BlockPos> positions = new ArrayList<>();

        for (int x = inMinX - 1; x <= inMaxX + 1; x++) {
            for (int y = inMinY - 1; y <= inMaxY + 1; y++) {
                for (int z = inMinZ - 1; z <= inMaxZ + 1; z++) {
                    if (x >= inMinX && x <= inMaxX
                     && y >= inMinY && y <= inMaxY
                     && z >= inMinZ && z <= inMaxZ) continue;
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }

        return positions;
    }
}
