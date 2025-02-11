// DronePathFinder.java
package com.uemc.assistance_drone.entities.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class DronePathFinder {
    private static final int MAX_DISTANCE = 64;
    private final Level level;
    private final BlockPos start;
    private final BlockPos target;

    public DronePathFinder(Level level, BlockPos start, BlockPos target) {
        this.level = level;
        this.start = start;
        this.target = target;
    }

    public List<BlockPos> findPath() {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<BlockPos, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start);
        startNode.g = 0;
        startNode.h = heuristic(start, target);
        startNode.f = startNode.h;
        openSet.add(startNode);
        allNodes.put(start, startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if(!isPassable(target)){
                return Collections.emptyList();
            }

            // Skip if we've found a better path for this position already
            if (current.g > allNodes.get(current.pos).g) continue;

            if (current.pos.distSqr(target) <= 1) {
                return reconstructPath(current);
            }

            if (current.pos.distSqr(start) > MAX_DISTANCE * MAX_DISTANCE) {
                return Collections.emptyList();
            }

            List<BlockPos> neighbors = getNeighbors(current.pos);

            // Only add neighbors from above/below if those positions are passable
            BlockPos above = current.pos.above();
            if (isPassable(above)) {
                neighbors.add(above);
                neighbors.addAll(getNeighbors(above));
            }
            BlockPos below = current.pos.below();
            if (isPassable(below)) {
                neighbors.add(below);
                neighbors.addAll(getNeighbors(below));
            }

            for (BlockPos neighbor : neighbors) {
                Node neighborNode = allNodes.getOrDefault(neighbor, new Node(neighbor));
                double tentativeG = current.g + movementCost(current.pos, neighbor);

                if (tentativeG < neighborNode.g) {
                    neighborNode.parent = current;
                    neighborNode.g = tentativeG;
                    neighborNode.h = heuristic(neighbor, target);
                    neighborNode.f = neighborNode.g + neighborNode.h;

                    // Always add to openSet to allow priority update
                    openSet.add(neighborNode);
                    allNodes.put(neighbor, neighborNode);
                }
            }
        }
        return Collections.emptyList();
    }

    // Use actual movement cost (squared distance)
    private double movementCost(BlockPos a, BlockPos b) {
        return 1; // Todos los movimientos cuestan igual
    }

    // Admissible heuristic (Euclidean distance)
    private double heuristic(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }

    private List<BlockPos> reconstructPath(Node endNode) {
        List<BlockPos> path = new ArrayList<>();
        Node current = endNode;
        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();

        // Pre-calculate all cardinal positions.
        BlockPos n = pos.north();
        BlockPos s = pos.south();
        BlockPos e = pos.east();
        BlockPos w = pos.west();
        BlockPos ne = pos.north().east();
        BlockPos se = pos.south().east();
        BlockPos nw = pos.north().west();
        BlockPos sw = pos.south().west();

        boolean auxN = isPassable(n);
        boolean auxS = isPassable(s);
        boolean auxE = isPassable(e);
        boolean auxW = isPassable(w);
        // Add cardinal neighbors if passable.
        if (auxN) {
            neighbors.add(n);
            if(auxE && isPassable(ne)) {
                neighbors.add(ne);
            }
            if(auxW && isPassable(nw)) {
                neighbors.add(nw);
            }
        }
        if (auxS) {
            neighbors.add(s);
            if(auxE && isPassable(se)) {
                neighbors.add(se);
            }
            if(auxW && isPassable(sw)) {
                neighbors.add(sw);
            }
        }
        if (auxE)   neighbors.add(e);
        if (auxW)   neighbors.add(w);

        return neighbors;
    }

    private boolean isPassable(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir();
    }

    private static class Node implements Comparable<Node> {
        public BlockPos pos;
        public Node parent;
        public double g = Double.MAX_VALUE;
        public double h;
        public double f;

        public Node(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.f, other.f);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return pos.equals(node.pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode();
        }
    }
}