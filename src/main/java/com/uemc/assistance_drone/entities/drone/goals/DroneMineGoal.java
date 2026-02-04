package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Predicate;

public class DroneMineGoal extends Goal {

    private final DroneEntity drone;
    private final Predicate<String> activationCondition;
    private static final Logger LOGGER = LoggerFactory.getLogger(DroneMineGoal.class);

    private int checkCooldown = 0;
    private BlockPos currentJobTarget = null;

    // NUEVO: Variable para guardar el obstáculo temporal
    private BlockPos obstacleTarget = null;

    private final Queue<BlockPos> waypoints = new LinkedList<>();
    private final SpiralLayerIterator layerIterator;

    public DroneMineGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        this.layerIterator = new SpiralLayerIterator(this);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!activationCondition.test(drone.getState())) return false;
        ItemStack planner = drone.getInventory().getStackInSlot(0);
        if (!SitePlanner.isConfigured(planner)) return false;
        if (this.checkCooldown-- > 0) return false;

        if (hasMiningTargets(drone.level(), planner)) {
            this.checkCooldown = 20;
            return true;
        } else {
            this.checkCooldown = 40;
            return false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        // Continuamos si hay un obstáculo, un trabajo actual, o quedan bloques en el iterador
        return activationCondition.test(drone.getState())
                && (this.obstacleTarget != null || this.currentJobTarget != null || !this.layerIterator.isFinished());
    }

    @Override
    public void start() {
        this.currentJobTarget = null;
        this.obstacleTarget = null; // Resetear obstáculo
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
        this.drone.getLogic().resetMiningState();
        this.drone.getNavigation().stop();

        Vec3 current = drone.getLogic().calculateIdealPosition(drone.getX(), drone.getY(), drone.getZ());
        drone.getLogic().executeMovement(current);
    }

    @Override
    public boolean isInterruptable() {
        // No interrumpir si estamos minando algo (sea trabajo u obstáculo)
        return this.currentJobTarget == null && this.obstacleTarget == null;
    }

    @Override
    public void tick() {
        Level level = this.drone.level();
        if (level.isClientSide) return;

        BlockPos suffocating = getSuffocatingBlock();

        // 1. DETERMINAR EL OBJETIVO ACTIVO
        // Prioridad: Si hay un obstáculo bloqueando el camino, ese es el objetivo ahora.
        obstacleTarget = (suffocating != null) ? suffocating : obstacleTarget;
        BlockPos activeTarget = (obstacleTarget != null) ? obstacleTarget : currentJobTarget;

        // Si no tenemos ni obstáculo ni trabajo, pedimos uno al iterador
        if (activeTarget == null) {
            BlockPos next = this.layerIterator.next(level);
            if (next == null) return;

            this.currentJobTarget = next;
            activeTarget = next;
            this.waypoints.clear();
        }

        // LOGGER.debug("Target: {} (Is Obstacle: {})", activeTarget, (activeTarget == obstacleTarget));

        // 2. LÓGICA DE INTERACCIÓN (Común para obstáculo y trabajo)
        Vec3 targetVec = Vec3.atCenterOf(activeTarget);
        drone.getLookControl().setLookAt(targetVec);

        boolean inRange = drone.getLogic().isInRangeToInteract(activeTarget);

        if (inRange) {
            drone.getNavigation().stop();
            boolean blockBroken = drone.getLogic().mineBlock(activeTarget);

            if (blockBroken) {
                // GESTIÓN DE FINALIZACIÓN
                if (obstacleTarget != null) {
                    // Si rompimos el obstáculo, lo limpiamos y el tick siguiente retomará el currentJobTarget
                    LOGGER.info("Obstacle cleared at {}", obstacleTarget);
                    obstacleTarget = null;
                } else {
                    // Si rompimos el trabajo oficial
                    currentJobTarget = null;
                }
            }
        } else {
            drone.getLogic().resetMiningState();
            // Solo navegamos hacia el currentJobTarget si no hay obstáculo.
            // Si hay obstáculo y no estamos en rango, intentamos acercarnos a él.
            processNavigation(activeTarget);
        }
    }

    public @Nullable BlockPos getSuffocatingBlock() {
        BlockPos pos = this.drone.blockPosition();
        BlockState state = this.drone.level().getBlockState(pos);

        if (!state.isAir() && state.isSuffocating(this.drone.level(), pos)) {
            return pos;
        }
        return null;
    }

    // --- NAVEGACIÓN LOCAL ---

    private void processNavigation(BlockPos target) {
        // Si estamos persiguiendo un obstáculo, vamos directo a él
        if (target.equals(obstacleTarget)) {
            drone.getLogic().executeMovement(Vec3.atCenterOf(target));
            return;
        }

        if (waypoints.isEmpty()) {
            if (canReach(target)) {
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

        // --- AQUÍ ESTÁ EL CAMBIO CLAVE ---
        // Antes de movernos a la esquina, verificamos si hay algo en medio
        BlockPos obstruction = drone.getLogic().getObstructionBlock(nextCorner);

        if (obstruction != null) {
            // Verificamos si el obstáculo es minable (no es bedrock ni aire)
            BlockState obsState = drone.level().getBlockState(obstruction);
            if (isValidBlock(obsState, drone.level(), obstruction)) {
                LOGGER.info("Path blocked by {} at {}. Switching to mine obstruction.", obsState.getBlock().getName().getString(), obstruction);
                this.obstacleTarget = obstruction; // ¡CAMBIO DE OBJETIVO!
                return; // Dejamos de movernos y en el siguiente tick el dron atacará el obstáculo
            }
        }

        // Si no hay obstáculo, nos movemos normalmente
        drone.getLogic().executeMovement(Vec3.atCenterOf(nextCorner));
    }

    private boolean canReach(BlockPos target) {
        return drone.getLogic().isBlockAccessible(target);
    }

    private void calculateCornerPath(BlockPos start, BlockPos end) {
        waypoints.clear();
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();

        int dx = Math.abs(end.getX() - x);
        int dz = Math.abs(end.getZ() - z);

        if (dx > 0) {
            x = end.getX();
            waypoints.add(new BlockPos(x, y, z));
        }
        if (dz > 0) {
            z = end.getZ();
            waypoints.add(new BlockPos(x, y, z));
        }
        if (y != end.getY()) {
            y = end.getY();
            waypoints.add(new BlockPos(x, y, z));
        }

        if (waypoints.isEmpty() || !waypoints.contains(end)) {
            waypoints.add(end);
        }
    }

    // --- VALIDACIÓN ---

    public boolean isValidBlock(BlockState state, Level level, BlockPos pos) {
        boolean isPureFluid = !state.getFluidState().isEmpty() && !state.hasProperty(BlockStateProperties.WATERLOGGED);
        return !state.isAir() &&
                state.getDestroySpeed(level, pos) >= 0 && // No irrompible (Bedrock)
                !state.is(Blocks.BEDROCK) &&
                !isPureFluid;
    }

    // ... Métodos de hasMiningTargets y SpiralLayerIterator se mantienen igual ...
    private boolean hasMiningTargets(Level level, ItemStack planner) {
        // (Mismo código que proveíste)
        BlockPos start = SitePlanner.getStartPos(planner);
        BlockPos end = SitePlanner.getEndPos(planner);
        int minX = Math.min(start.getX(), end.getX());
        int maxX = Math.max(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int maxY = Math.max(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        int maxZ = Math.max(start.getZ(), end.getZ());

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (isValidBlock(state, level, cursor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static class SpiralLayerIterator {
        // (Mismo código que proveíste, sin cambios necesarios)
        private final Queue<BlockPos> queue = new LinkedList<>();
        private final DroneMineGoal goal;
        private int currentY, endY, startY;
        private int rawStartX, rawStartZ, rawEndX, rawEndZ;
        private boolean finished = false;

        public SpiralLayerIterator(DroneMineGoal goal) { this.goal = goal; }

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

        public BlockPos next(Level level) {
            if (finished) return null;
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                BlockState state = level.getBlockState(pos);
                if (goal.isValidBlock(state, level, pos)) return pos;
            }
            if (hasBlocksLeftInLayer(level, currentY)) {
                generateSpiralForLayer(currentY);
                if (!queue.isEmpty()) return next(level);
            }
            if (currentY == endY) {
                finished = true;
                return null;
            }
            currentY += (endY > startY) ? 1 : -1;
            generateSpiralForLayer(currentY);
            return next(level);
        }

        public boolean isFinished() { return finished; }

        private boolean hasBlocksLeftInLayer(Level level, int y) {
            int minX = Math.min(rawStartX, rawEndX);
            int maxX = Math.max(rawStartX, rawEndX);
            int minZ = Math.min(rawStartZ, rawEndZ);
            int maxZ = Math.max(rawStartZ, rawEndZ);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (goal.isValidBlock(s, level, p)) return true;
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
                    case 0: for (int x = currentMinX; x <= currentMaxX; x++) queue.add(new BlockPos(x, y, currentMinZ)); currentMinZ++; break;
                    case 1: for (int z = currentMinZ; z <= currentMaxZ; z++) queue.add(new BlockPos(currentMaxX, y, z)); currentMaxX--; break;
                    case 2: for (int x = currentMaxX; x >= currentMinX; x--) queue.add(new BlockPos(x, y, currentMaxZ)); currentMaxZ--; break;
                    case 3: for (int z = currentMaxZ; z >= currentMinZ; z--) queue.add(new BlockPos(currentMinX, y, z)); currentMinX++; break;
                }
                direction = (direction + 1) % 4;
            }
        }
    }
}