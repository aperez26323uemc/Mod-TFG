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

public class DroneMineGoal extends Goal {

    private final DroneEntity drone;
    private final Predicate<String> activationCondition;

    // State
    private int checkCooldown = 0;
    private BlockPos currentJobTarget = null;
    private BlockPos obstacleTarget = null;

    // Navigation / Strategy
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

        // Optimizamos el cooldown dependiendo de si encontramos trabajo o no
        if (hasMiningTargets(planner)) {
            this.checkCooldown = 20; // Re-check normal
            return true;
        } else {
            this.checkCooldown = 60; // Relax si no hay nada (ahorra CPU)
            return false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return activationCondition.test(drone.getState())
                && (this.obstacleTarget != null || this.currentJobTarget != null || !this.layerIterator.isFinished());
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
        this.drone.getLogic().resetMiningState();
        this.drone.getNavigation().stop();

        // Flotar de forma segura al terminar
        Vec3 current = drone.getLogic().calculateIdealPosition(drone.getX(), drone.getY(), drone.getZ());
        drone.getLogic().executeMovement(current);
    }

    @Override
    public boolean isInterruptable() {
        return true;
    }

    @Override
    public void tick() {
        Level level = this.drone.level();
        if (level.isClientSide) return;

        // La lógica de aplicar buffs ahora es automática vía Mixin, aquí no hacemos nada.

        // 1. SEGURIDAD: ¿Nos estamos asfixiando? (Prioridad Absoluta)
        BlockPos suffocating = drone.getLogic().getSuffocatingBlock(); // DELEGADO A LOGIC
        if (suffocating != null) {
            obstacleTarget = suffocating;
        }

        // 2. DETERMINAR TARGET ACTIVO
        BlockPos activeTarget = (obstacleTarget != null) ? obstacleTarget : currentJobTarget;

        // Si no hay target, pedimos el siguiente al iterador
        if (activeTarget == null) {
            BlockPos next = this.layerIterator.next();
            if (next == null) return; // Trabajo terminado o nada encontrado

            this.currentJobTarget = next;
            activeTarget = next;
            this.waypoints.clear();
        }

        // 3. EJECUTAR ACCIÓN
        Vec3 targetVec = Vec3.atCenterOf(activeTarget);
        drone.getLookControl().setLookAt(targetVec);

        if (drone.getLogic().isInRangeToInteract(activeTarget)) {
            // FASE DE MINADO
            drone.getNavigation().stop();
            boolean blockBroken = drone.getLogic().mineBlock(activeTarget);

            if (blockBroken) {
                if (obstacleTarget != null) {
                    obstacleTarget = null;
                } else {
                    currentJobTarget = null;
                }
            }
        } else {
            // FASE DE APROXIMACIÓN
            drone.getLogic().resetMiningState();
            processNavigation(activeTarget);
        }
    }

    // --- NAVEGACIÓN ESTRATÉGICA ---

    private void processNavigation(BlockPos target) {
        // A los obstáculos (emergencia) vamos directo
        if (target.equals(obstacleTarget)) {
            drone.getLogic().executeMovement(Vec3.atCenterOf(target));
            return;
        }

        // Generar waypoints si no hay (navegación tipo "Manhattan" por esquinas)
        if (waypoints.isEmpty()) {
            if (drone.getLogic().isBlockAccessible(target)) {
                drone.getLogic().executeMovement(Vec3.atCenterOf(target));
            } else {
                calculateCornerPath(drone.blockPosition(), target);
            }
            return;
        }

        // Seguir waypoints
        BlockPos nextCorner = waypoints.peek();

        // ¿Llegamos al waypoint?
        if (drone.blockPosition().distManhattan(nextCorner) <= 1) {
            waypoints.poll();
            return;
        }

        // DETECCIÓN DE OBSTÁCULOS EN RUTA
        BlockPos obstruction = drone.getLogic().getObstructionBlock(nextCorner);

        if (obstruction != null) {
            // Si hay obstrucción, preguntamos a la lógica si vale la pena minarla
            if (drone.getLogic().isValidMiningTarget(obstruction)) {
                this.obstacleTarget = obstruction;
                return;
            }
        }

        // Camino libre -> Moverse
        drone.getLogic().executeMovement(Vec3.atCenterOf(nextCorner));
    }

    /**
     * Calcula una ruta simple en "L" (ejes cartesianos) para evitar atascos simples.
     * Mantenemos esto aquí porque es una estrategia específica de minería, no de movimiento general.
     */
    private void calculateCornerPath(BlockPos start, BlockPos end) {
        waypoints.clear();
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();

        // Prioridad: Eje X -> Eje Z -> Eje Y (para alinearse primero horizontalmente)
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

        // Asegurar que el destino final está en la lista
        if (waypoints.isEmpty() || !waypoints.contains(end)) {
            waypoints.add(end);
        }
    }

    private boolean hasMiningTargets(ItemStack planner) {
        BlockPos start = SitePlanner.getStartPos(planner);
        BlockPos end = SitePlanner.getEndPos(planner);

        // AABB para iterar
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = Math.min(start.getX(), end.getX());
        int maxX = Math.max(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int maxY = Math.max(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        int maxZ = Math.max(start.getZ(), end.getZ());

        // Escaneo rápido de arriba a abajo
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    // Usamos la validación centralizada
                    if (drone.getLogic().isValidMiningTarget(cursor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // --- ITERADOR (Limpio, usa Logic para validar) ---
    private static class SpiralLayerIterator {
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

        public BlockPos next() {
            if (finished) return null;
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                // DELEGADO: Usamos la lógica del dron para validar
                if (goal.drone.getLogic().isValidMiningTarget(pos)) return pos;
            }
            if (hasBlocksLeftInLayer(currentY)) {
                generateSpiralForLayer(currentY);
                if (!queue.isEmpty()) return next();
            }
            if (currentY == endY) {
                finished = true;
                return null;
            }
            currentY += (endY > startY) ? 1 : -1;
            generateSpiralForLayer(currentY);
            return next();
        }

        public boolean isFinished() { return finished; }

        private boolean hasBlocksLeftInLayer(int y) {
            int minX = Math.min(rawStartX, rawEndX);
            int maxX = Math.max(rawStartX, rawEndX);
            int minZ = Math.min(rawStartZ, rawEndZ);
            int maxZ = Math.max(rawStartZ, rawEndZ);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (goal.drone.getLogic().isValidMiningTarget(p)) return true;
                }
            }
            return false;
        }

        private void generateSpiralForLayer(int y) {
            // (Tu lógica de espiral original se mantiene intacta aquí)
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