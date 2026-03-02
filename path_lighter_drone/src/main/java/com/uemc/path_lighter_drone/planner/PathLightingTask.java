package com.uemc.path_lighter_drone.planner;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Immutable sequence of positions where light blocks should be placed.
 */
public record PathLightingTask(List<BlockPos> placements) {

    public PathLightingTask {
        placements = List.copyOf(placements);
    }

    public boolean isEmpty() {
        return placements.isEmpty();
    }
}
