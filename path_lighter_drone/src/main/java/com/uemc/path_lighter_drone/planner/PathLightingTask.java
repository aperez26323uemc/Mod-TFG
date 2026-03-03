package com.uemc.path_lighter_drone.planner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Immutable sequence of resolved placement nodes.
 * Each node encodes where the torch ends up, which block it rests on,
 * and which face of that support block was "clicked".
 */
public record PathLightingTask(List<Node> placements) {

    public PathLightingTask {
        placements = List.copyOf(placements);
    }

    public boolean isEmpty() {
        return placements.isEmpty();
    }

    /**
     * @param placement  final position where the light block is placed
     * @param support    adjacent solid block that the light block rests against
     * @param face       face of {@code support} that faces {@code placement}
     *                   (i.e. the face the drone "clicks" on)
     */
    public record Node(BlockPos placement, BlockPos support, Direction face) {}
}