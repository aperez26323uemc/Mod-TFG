package com.uemc.path_lighter_drone.planner;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.path_lighter_drone.ModKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds immutable path-lighting tasks from player look vector and world conditions.
 */
public final class PathLighterPlanner {

    private PathLighterPlanner() {
    }

    public static Optional<PathLightingTask> buildTask(Level level, Player player, DroneEntity drone) {
        if (!hasSuitableLightBlock(drone)) {
            return Optional.empty();
        }

        List<BlockPos> projectedPath = buildProjectedPath(level, player);
        if (projectedPath.isEmpty()) {
            return Optional.empty();
        }

        List<BlockPos> nodes = selectNodes(projectedPath);
        if (nodes.isEmpty()) {
            return Optional.empty();
        }

        List<BlockPos> placements = evaluatePlacements(level, nodes, drone);
        if (placements.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new PathLightingTask(placements));
    }

    public static boolean hasSuitableLightBlock(DroneEntity drone) {
        for (int slot = 0; slot < drone.getInventory().getSlots(); slot++) {
            ItemStack stack = drone.getInventory().getStackInSlot(slot);
            if (isSuitableLightBlock(stack, drone.level(), drone.blockPosition())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSuitableLightBlock(ItemStack stack, Level level, BlockPos contextPos) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        BlockState state = blockItem.getBlock().defaultBlockState();
        return state.getLightEmission(level, contextPos) >= ModKeys.SUITABLE_LIGHT_EMISSION;
    }

    private static List<BlockPos> buildProjectedPath(Level level, Player player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        double endDistance = clampToHorizontalPrism(player.position(), start, look);
        if (endDistance <= 0.0D) {
            return List.of();
        }

        Vec3 maxEnd = start.add(look.scale(endDistance));
        BlockHitResult hit = level.clip(new ClipContext(start, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        Vec3 finalPoint = hit.getType() == HitResult.Type.MISS ? maxEnd : hit.getLocation();

        BlockPos from = BlockPos.containing(start.x, player.getY(), start.z);
        BlockPos to = BlockPos.containing(finalPoint.x, player.getY(), finalPoint.z);

        List<BlockPos> grid = bresenham2d(from.getX(), from.getZ(), to.getX(), to.getZ());
        List<BlockPos> projected = new ArrayList<>(grid.size());

        Integer previousGround = null;
        double totalDist2D = Math.hypot(finalPoint.x - start.x, finalPoint.z - start.z);

        for (BlockPos xz : grid) {
            double currentDist2D = Math.hypot((xz.getX() + 0.5D) - start.x, (xz.getZ() + 0.5D) - start.z);
            double t = totalDist2D > 0 ? Math.max(0.0D, Math.min(1.0D, currentDist2D / totalDist2D)) : 0.0D;

            double rayY = start.y + t * (finalPoint.y - start.y);
            int startScanY = (int) Math.floor(rayY);

            Integer y = findGroundY(level, xz.getX(), startScanY, xz.getZ());

            if (y == null) { // void
                break;
            }

            if (previousGround != null && (y - previousGround) >= ModKeys.WALL_HEIGHT_BLOCKS) {
                break;
            }

            BlockPos ground = new BlockPos(xz.getX(), y, xz.getZ());
            projected.add(ground);
            previousGround = y;
        }

        return projected;
    }

    private static Integer findGroundY(Level level, int x, int startY, int z) {
        int minY = level.getMinBuildHeight();

        for (int y = startY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);

            if (!state.getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
                return y;
            }
        }
        return null;
    }

    private static double clampToHorizontalPrism(Vec3 playerPos, Vec3 start, Vec3 dir) {
        double tx = Double.POSITIVE_INFINITY;
        double tz = Double.POSITIVE_INFINITY;
        double radius = ModKeys.PATH_RADIUS_BLOCKS;

        if (Math.abs(dir.x) > 1.0E-6D) {
            double boundX = playerPos.x + Math.copySign(radius, dir.x);
            tx = (boundX - start.x) / dir.x;
        }

        if (Math.abs(dir.z) > 1.0E-6D) {
            double boundZ = playerPos.z + Math.copySign(radius, dir.z);
            tz = (boundZ - start.z) / dir.z;
        }

        double t = Math.min(tx, tz);
        if (Double.isInfinite(t) || t < 0.0D) {
            return 0.0D;
        }
        return t;
    }

    /**
     * Selects target nodes along the projected path using linear interpolation.
     * <p>
     * Ensures that light sources are distributed evenly across the entire path
     * without exceeding the maximum allowed spacing defined by {@code ModKeys.NODE_SPACING}.
     *
     * @param path the full list of block positions representing the projected path
     * @return a list of evenly spaced target nodes for light placement
     */
    private static List<BlockPos> selectNodes(List<BlockPos> path) {
        if (path.size() <= ModKeys.FIRST_NODE_DISTANCE) {
            return List.of();
        }

        int startIndex = ModKeys.FIRST_NODE_DISTANCE;
        int endIndex = path.size() - 1;
        int distance = endIndex - startIndex;

        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> ordered = new HashSet<>();

        if (distance <= ModKeys.NODE_SPACING / 2) {
            addNode(path.get(startIndex + distance / 2), ordered, result);
            return result;
        }

        int numSegments = (int) Math.ceil((double) distance / ModKeys.NODE_SPACING);
        double step = (double) distance / numSegments;

        for (int i = 0; i <= numSegments; i++) {
            int index = startIndex + (int) Math.round(i * step);
            addNode(path.get(Math.min(index, endIndex)), ordered, result);
        }

        return result;
    }

    private static void addNode(BlockPos node, Set<BlockPos> seen, List<BlockPos> out) {
        if (seen.add(node)) {
            out.add(node);
        }
    }

    private static List<BlockPos> evaluatePlacements(Level level, List<BlockPos> nodes, DroneEntity drone) {
        List<BlockPos> placements = new ArrayList<>();
        for (BlockPos node : nodes) {
            BlockPos air = node.above();
            if (!needsLight(level, air)) {
                continue;
            }

            Optional<BlockPos> valid = findValidPlacement(level, air, drone);
            valid.ifPresent(placements::add);
        }
        return placements;
    }

    private static boolean needsLight(Level level, BlockPos pos) {
        if (!level.getFluidState(pos.below()).isEmpty() || !level.getFluidState(pos).isEmpty()) {
            return false;
        }
        return level.getMaxLocalRawBrightness(pos) < ModKeys.NODE_LIGHT_THRESHOLD;
    }

    private static Optional<BlockPos> findValidPlacement(Level level, BlockPos center, DroneEntity drone) {
        if (canAnySuitableBlockBePlaced(level, center, drone)) {
            return Optional.of(center);
        }

        BlockPos bestCandidate = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        int radius = ModKeys.NODE_DEVIATION_RADIUS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared >= bestDistanceSquared) {
                    continue;
                }

                BlockPos candidate = center.offset(dx, 0, dz);
                if (canAnySuitableBlockBePlaced(level, candidate, drone)) {
                    bestCandidate = candidate;
                    bestDistanceSquared = distanceSquared;
                }
            }
        }

        return Optional.ofNullable(bestCandidate);
    }

    private static boolean canAnySuitableBlockBePlaced(Level level, BlockPos pos, DroneEntity drone) {
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }

        for (int slot = 0; slot < drone.getInventory().getSlots(); slot++) {
            ItemStack stack = drone.getInventory().getStackInSlot(slot);
            if (!isSuitableLightBlock(stack, level, pos)) {
                continue;
            }

            if (canPlaceAt(level, pos, stack)) {
                return true;
            }
        }

        return false;
    }

    private static boolean canPlaceAt(Level level, BlockPos pos, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Player fakePlayer = level instanceof net.minecraft.server.level.ServerLevel serverLevel
                ? net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(serverLevel)
                : null;

        BlockPos clickedPos = pos.below();
        var context = new net.minecraft.world.item.context.BlockPlaceContext(
                level, fakePlayer, net.minecraft.world.InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, clickedPos, false)
        );
        return blockItem.getBlock().getStateForPlacement(context) != null;
    }

    private static List<BlockPos> bresenham2d(int x0, int z0, int x1, int z1) {
        List<BlockPos> points = new ArrayList<>();

        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        int x = x0;
        int z = z0;

        while (true) {
            points.add(new BlockPos(x, 0, z));
            if (x == x1 && z == z1) {
                break;
            }

            int e2 = err * 2;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }

        return points;
    }
}
