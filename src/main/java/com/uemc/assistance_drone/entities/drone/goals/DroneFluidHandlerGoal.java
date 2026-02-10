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
 * Goal AI que gestiona la detección y eliminación de fluidos (agua, lava) en áreas de construcción.
 * <p>
 * Este goal implementa un sistema de escaneo incremental por chunks para detectar amenazas de fluidos
 * y priorizarlas según su ubicación y peligrosidad. El escaneo se realiza gradualmente para evitar
 * impactar el rendimiento del juego.
 * </p>
 * <p>
 * Características principales:
 * <ul>
 *   <li>Escaneo por secciones de chunks con sistema de pausa/reanudación</li>
 *   <li>Priorización de amenazas según proximidad a bordes y altura</li>
 *   <li>Detección de fuentes infinitas y filtración externa</li>
 *   <li>Sistema de blacklist para evitar posiciones inaccesibles</li>
 * </ul>
 */
public class DroneFluidHandlerGoal extends Goal {

    /* =========================
       CONSTANTES DE CONFIGURACIÓN
       ========================= */

    /** Máximo número de bloques a escanear por tick para evitar lag */
    private static final int MAX_SCAN_PER_TICK = 256;

    /** Máximo número de nodos a trazar al seguir flujo de fluidos */
    private static final int MAX_TRACE_NODES = 128;

    /** Ticks de enfriamiento entre escaneos completos */
    private static final int SCAN_COOLDOWN_TICKS = 10;

    /** Tolerancia para detectar colisiones con el drone */
    private static final double COLLISION_TOLERANCE = 0.1;

    /** Número mínimo de fuentes adyacentes para considerar fuente infinita */
    private static final int INFINITE_SOURCE_THRESHOLD = 2;

    /** Grosor del borde del área para priorización */
    private static final int BORDER_THICKNESS = 2;

    /** Direcciones a explorar al trazar flujo de fluidos (excluye DOWN para eficiencia) */
    private static final List<Direction> TRACE_DIRECTIONS = List.of(
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    );

    /* =========================
       DEPENDENCIAS Y ESTADO
       ========================= */

    private final DroneEntity drone;
    private final Predicate<String> activationCondition;

    /** Posiciones de fluidos que no pudieron ser procesadas */
    private final Set<BlockPos> fluidBlacklist = new HashSet<>();

    /** Cola de prioridad con amenazas detectadas */
    private final PriorityQueue<FluidThreat> fluidQueue = new PriorityQueue<>();

    /** Cache de secciones que pueden ser saltadas (sin fluidos) */
    private final Map<Long, Boolean> sectionSkipCache = new HashMap<>();

    /** Posición objetivo actual del drone */
    private BlockPos targetPos;

    /** Contador de ticks restantes hasta el próximo escaneo */
    private int scanCooldown = SCAN_COOLDOWN_TICKS;

    /* =========================
       ESTADO DE REANUDACIÓN
       ========================= */

    /** Coordenada X de reanudación del escaneo de bloques */
    private int resumeX;

    /** Coordenada Y de reanudación del escaneo de bloques */
    private int resumeY;

    /** Coordenada Z de reanudación del escaneo de bloques */
    private int resumeZ;

    /** Coordenada Y de sección de reanudación */
    private int resumeSecY = Integer.MIN_VALUE;

    /** Coordenada X de sección de reanudación */
    private int resumeSecX;

    /** Coordenada Z de sección de reanudación */
    private int resumeSecZ;

    /** Indica si hay un estado de reanudación pendiente */
    private boolean hasResumeState = false;

    /* =========================
       CONSTRUCCIÓN
       ========================= */

    /**
     * Construye un nuevo goal de manejo de fluidos para el drone.
     *
     * @param drone El drone que ejecutará este goal
     * @param activationCondition Predicado que determina cuándo este goal debe activarse
     */
    public DroneFluidHandlerGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /* =========================
       CICLO DE VIDA DEL GOAL
       ========================= */

    /**
     * Determina si este goal puede iniciar su ejecución.
     * <p>
     * Verifica:
     * <ul>
     *   <li>La condición de activación del drone</li>
     *   <li>Disponibilidad de bloques removedores de fluido</li>
     *   <li>Existencia de amenazas en cola o necesidad de escanear</li>
     * </ul>
     * </p>
     *
     * @return true si el goal puede ejecutarse
     */
    @Override
    public boolean canUse() {
        if (!activationCondition.test(drone.getState())) {
            return false;
        }

        if (drone.getLogic().findSlotWithFluidRemoverBlock() == -1) {
            return false;
        }

        if (drone.getNavigation().isStuck()) {
            return false;
        }

        targetPos = getNextThreatFromQueue();
        if (targetPos != null) {
            return true;
        }

        if (scanCooldown-- > 0) {
            return false;
        }

        scanCooldown = SCAN_COOLDOWN_TICKS;
        scanForFluidThreats();
        targetPos = getNextThreatFromQueue();

        return targetPos != null;
    }

    /**
     * Determina si el goal puede continuar ejecutándose.
     * <p>
     * Similar a {@link #canUse()}, pero también actualiza el objetivo si es necesario.
     * </p>
     *
     * @return true si el goal debe continuar
     */
    @Override
    public boolean canContinueToUse() {
        if (!activationCondition.test(drone.getState())) {
            return false;
        }

        if (drone.getLogic().findSlotWithFluidRemoverBlock() == -1) {
            return false;
        }

        if (drone.getNavigation().isStuck()) {
            return false;
        }

        if (targetPos == null) {
            targetPos = getNextThreatFromQueue();
        }

        if (targetPos == null) {
            scanForFluidThreats();
            targetPos = getNextThreatFromQueue();
        }

        return targetPos != null;
    }

    /**
     * Detiene la ejecución del goal y limpia el estado.
     */
    @Override
    public void stop() {
        targetPos = null;
        drone.getNavigation().stop();
    }

    /**
     * Indica que este goal no puede ser interrumpido una vez iniciado.
     *
     * @return false
     */
    @Override
    public boolean isInterruptable() {
        return false;
    }

    /**
     * Ejecuta la lógica principal del goal en cada tick.
     * <p>
     * Secuencia de ejecución:
     * <ol>
     *   <li>Apuntar hacia el objetivo</li>
     *   <li>Manejar obstrucción del drone</li>
     *   <li>Acercarse al objetivo si está lejos</li>
     *   <li>Ejecutar acción sobre el objetivo</li>
     * </ol>
     * </p>
     */
    @Override
    public void tick() {
        if (targetPos == null) {
            return;
        }

        drone.getLookControl().setLookAt(Vec3.atCenterOf(targetPos));

        if (handleDroneObstruction()) {
            return;
        }

        if (handleTargetApproach()) {
            return;
        }

        executeTargetAction();
    }

    /* =========================
       MANEJO DE EJECUCIÓN
       ========================= */

    /**
     * Maneja el caso donde el drone está bloqueando la posición objetivo.
     * <p>
     * Si el drone interfiere con el objetivo, intenta moverse a una posición adyacente libre.
     * Si no encuentra posición libre, añade el objetivo a la blacklist.
     * </p>
     *
     * @return true si el drone está en el camino y se manejó la situación
     */
    private boolean handleDroneObstruction() {
        if (!isDroneInTheWay(targetPos)) {
            return false;
        }

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
     * Maneja el acercamiento del drone al objetivo.
     * <p>
     * Si el drone está fuera de rango, verifica accesibilidad y se mueve hacia el objetivo.
     * Si el objetivo no es accesible, lo añade a la blacklist.
     * </p>
     *
     * @return true si el drone necesita acercarse más
     */
    private boolean handleTargetApproach() {
        if (drone.getLogic().isInRangeToInteract(targetPos)) {
            return false;
        }

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
     * Ejecuta la acción de colocar un bloque en el objetivo.
     * <p>
     * Detiene el movimiento del drone, coloca el bloque y limpia el objetivo de la blacklist
     * si la colocación fue exitosa.
     * </p>
     */
    private void executeTargetAction() {
        drone.getNavigation().stop();

        if (placeBlock(targetPos)) {
            removeFromBlacklistWithAdjacent(targetPos);
        }

        targetPos = null;
    }

    /* =========================
       GESTIÓN DE PRIORIDADES
       ========================= */

    /**
     * Obtiene la siguiente amenaza de mayor prioridad de la cola.
     * <p>
     * Descarta automáticamente amenazas que están en la blacklist.
     * </p>
     *
     * @return La posición de la siguiente amenaza, o null si no hay amenazas válidas
     */
    private BlockPos getNextThreatFromQueue() {
        while (!fluidQueue.isEmpty()) {
            FluidThreat threat = fluidQueue.poll();
            if (!fluidBlacklist.contains(threat.pos)) {
                return threat.pos;
            }
        }
        return null;
    }

    /**
     * Calcula la prioridad de una amenaza de fluido.
     * <p>
     * Factores de priorización (en orden de importancia):
     * <ul>
     *   <li>Fluidos fuera del área de construcción: +20,000</li>
     *   <li>Fluidos en el borde exacto: +10,000</li>
     *   <li>Fluidos cerca del borde (≤2 bloques): +5,000 - (distancia × 1,000)</li>
     *   <li>Altura del bloque: +Y</li>
     *   <li>Fluidos internos: +100</li>
     * </ul>
     * </p>
     *
     * @param pos Posición de la amenaza
     * @param site Área de construcción (AABB)
     * @return Valor de prioridad (mayor = más urgente)
     */
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

    /**
     * Calcula la distancia mínima de una posición a cualquier borde del área.
     *
     * @param pos Posición a evaluar
     * @param site Área de construcción
     * @return Distancia al borde más cercano en bloques
     */
    private int getDistanceToBorder(BlockPos pos, AABB site) {
        int x = pos.getX();
        int z = pos.getZ();
        return Math.min(
                Math.min(Math.abs(x - (int) site.minX), Math.abs(x - (int) site.maxX)),
                Math.min(Math.abs(z - (int) site.minZ), Math.abs(z - (int) site.maxZ))
        );
    }

    /* =========================
       ESCANEO POR SECCIONES
       ========================= */

    /**
     * Escanea el área de construcción en busca de amenazas de fluidos.
     * <p>
     * Escaneo incremental que puede pausarse y reanudarse para
     * distribuir el trabajo a lo largo de múltiples ticks. El escaneo procede:
     * <ol>
     *   <li>Por secciones verticales (chunks de 16x16x16)</li>
     *   <li>Por secciones horizontales X</li>
     *   <li>Por secciones horizontales Z</li>
     *   <li>Por bloques individuales (Y, X, Z)</li>
     * </ol>
     * </p>
     * <p>
     * El escaneo se pausa automáticamente después de {@link #MAX_SCAN_PER_TICK} bloques
     * y se reanuda en el siguiente tick desde donde se quedó.
     * </p>
     */
    @SuppressWarnings("DataFlowIssue")
    private void scanForFluidThreats() {
        ItemStack planner = drone.getInventory().getStackInSlot(0);
        if (!SitePlanner.isConfigured(planner)) {
            return;
        }

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

    /**
     * Inicializa el estado de reanudación si no existe.
     *
     * @param startSectionY Sección Y inicial
     * @param siteMinX X mínima del área
     * @param siteMinZ Z mínima del área
     */
    private void initializeResumeStateIfNeeded(int startSectionY, int siteMinX, int siteMinZ) {
        if (!hasResumeState) {
            resumeSecY = startSectionY;
            resumeSecX = siteMinX >> 4;
            resumeSecZ = siteMinZ >> 4;
            resumeY = -1; // Señal para reiniciar Y al entrar a nueva sección
        }
    }

    /**
     * Escanea todas las secciones del área.
     *
     * @param ctx Contexto con información del escaneo
     * @return true si el escaneo se completó, false si se pausó
     */
    private boolean scanSections(ScanContext ctx) {
        int scanned = 0;

        while (shouldContinueSectionY(ctx)) {
            if (!scanSectionXZ(ctx, scanned)) {
                return false; // Pausado
            }

            resumeSecX = ctx.siteMinX >> 4;
            resumeSecY += ctx.scanningDown ? -1 : 1;
        }

        return true;
    }

    /**
     * Verifica si debe continuar el escaneo en el eje Y de secciones.
     */
    private boolean shouldContinueSectionY(ScanContext ctx) {
        return ctx.scanningDown
                ? resumeSecY >= ctx.endSectionY
                : resumeSecY <= ctx.endSectionY;
    }

    /**
     * Escanea las secciones en los ejes X y Z.
     *
     * @param ctx Contexto del escaneo
     * @param scannedCount Contador de bloques escaneados (se modifica indirectamente)
     * @return true si completó el escaneo de esta capa Y, false si se pausó
     */
    private boolean scanSectionXZ(ScanContext ctx, int scannedCount) {
        while (resumeSecX <= ctx.siteMaxX >> 4) {
            if (!scanSectionZ(ctx, scannedCount)) {
                return false;
            }

            resumeSecZ = ctx.siteMinZ >> 4;
            resumeSecX++;
        }
        return true;
    }

    /**
     * Escanea las secciones en el eje Z.
     *
     * @param ctx Contexto del escaneo
     * @param scannedCount Contador de bloques escaneados (se modifica indirectamente)
     * @return true si completó el escaneo de esta capa X-Y, false si se pausó
     */
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

    /**
     * Calcula los límites de bloques para la sección actual.
     */
    private SectionBounds calculateSectionBounds(ScanContext ctx) {
        int x0 = Math.max(ctx.siteMinX, resumeSecX << 4);
        int x1 = Math.min(ctx.siteMaxX, (resumeSecX << 4) + 15);
        int z0 = Math.max(ctx.siteMinZ, resumeSecZ << 4);
        int z1 = Math.min(ctx.siteMaxZ, (resumeSecZ << 4) + 15);

        // Rango de la sección actual
        int secMinY = resumeSecY << 4;
        int secMaxY = secMinY + 15;

        // Rango global de escaneo ordenado de menor a mayor
        int globalMinY = Math.min(ctx.startY, ctx.effectiveEndY);
        int globalMaxY = Math.max(ctx.startY, ctx.effectiveEndY);

        // INTERSECCIÓN: Nos quedamos solo con lo que se solapa
        int y0 = Math.max(globalMinY, secMinY); // Mínimo válido
        int y1 = Math.min(globalMaxY, secMaxY); // Máximo válido

        return new SectionBounds(x0, x1, y0, y1, z0, z1);
    }

    /**
     * Escanea todos los bloques dentro de una sección.
     *
     * @param ctx Contexto del escaneo
     * @param bounds Límites de la sección
     * @param scannedCount Contador de bloques (modificado por referencia mediante wrapper)
     * @return true si completó el escaneo de la sección, false si se pausó
     */
    private boolean scanBlocksInSection(ScanContext ctx, SectionBounds bounds, int scannedCount) {
        if (resumeY == -1) {
            initializeBlockIterators(ctx, bounds);
        }

        return scanBlocksYXZ(ctx, bounds, scannedCount);
    }

    /**
     * Inicializa los iteradores de bloques al entrar en una nueva sección.
     */
    private void initializeBlockIterators(ScanContext ctx, SectionBounds bounds) {
        // Si bajamos, empezamos en el techo (y1). Si subimos, en el suelo (y0).
        resumeY = ctx.scanningDown ? bounds.y1 : bounds.y0;
        resumeX = bounds.x0;
        resumeZ = bounds.z0;
    }

    /**
     * Escanea los bloques en los tres ejes anidados (Y, X, Z).
     * <p>
     * scanBlocksYXZ contiene los tres bucles más internos del escaneo.
     * Se pausa automáticamente si se excede MAX_SCAN_PER_TICK.
     * </p>
     *
     * @return true si completó todos los bloques, false si se pausó
     */
    private boolean scanBlocksYXZ(ScanContext ctx, SectionBounds bounds, int scannedCount) {
        while (shouldContinueY(ctx, bounds)) {
            if (!scanBlocksXZ(ctx, bounds, scannedCount)) {
                return false;
            }

            resumeX = bounds.x0;
            resumeY += ctx.scanningDown ? -1 : 1;
        }
        return true;
    }

    /**
     * Verifica si debe continuar el escaneo en el eje Y de bloques.
     */
    private boolean shouldContinueY(ScanContext ctx, SectionBounds bounds) {
        // Si bajamos, paramos al pasar el suelo (y0).
        // Si subimos, paramos al pasar el techo (y1).
        return ctx.scanningDown
                ? resumeY >= bounds.y0
                : resumeY <= bounds.y1;
    }

    /**
     * Escanea los bloques en los ejes X y Z para una Y dada.
     *
     * @return true si completó esta capa Y, false si se pausó
     */
    private boolean scanBlocksXZ(ScanContext ctx, SectionBounds bounds, int scannedCount) {
        while (resumeX <= bounds.x1) {
            if (!scanBlocksZ(ctx, bounds, scannedCount)) {
                return false;
            }

            resumeZ = bounds.z0;
            resumeX++;
        }
        return true;
    }

    /**
     * Escanea los bloques en el eje Z para una Y y X dadas.
     * <p>
     * Este es el bucle más interno. Cada iteración analiza un bloque individual
     * y verifica si se debe pausar el escaneo.
     * </p>
     *
     * @return true si completó esta capa X-Y, false si se pausó
     */
    private boolean scanBlocksZ(ScanContext ctx, SectionBounds bounds, int scannedCount) {
        while (resumeZ <= bounds.z1) {
            scannedCount++;

            if (scannedCount > MAX_SCAN_PER_TICK) {
                return false;
            }

            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(resumeX, resumeY, resumeZ);
            BlockPos threat = analyzeBlockForFluid(cursor, ctx.site);

            if (threat != null) {
                fluidQueue.offer(new FluidThreat(threat, calculatePriority(threat, ctx.site)));
            }

            resumeZ++;
        }
        return true;
    }

    /**
     * Calcula el límite vertical efectivo del escaneo usando raycast.
     * <p>
     * Traza un rayo desde la posición inicial hasta el targetY para detectar
     * obstrucciones que impedirían al drone acceder a bloques más allá.
     * </p>
     *
     * @param start Posición inicial del raycast
     * @param targetY Y objetivo deseado
     * @return Y efectivo donde termina el raycast (o targetY+1 si no hay obstrucción)
     */
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

    /**
     * Determina si una sección de chunk puede ser omitida del escaneo.
     * <p>
     * Una sección se omite si:
     * <ul>
     *   <li>El chunk no está cargado</li>
     *   <li>La sección está fuera de los límites del chunk</li>
     *   <li>La sección contiene solo aire</li>
     *   <li>La sección no contiene ningún bloque con fluidos</li>
     * </ul>
     * </p>
     *
     * @param secX Coordenada X de la sección (en coordenadas de chunk)
     * @param y Coordenada Y de la sección
     * @param secZ Coordenada Z de la sección (en coordenadas de chunk)
     * @return true si la sección puede ser omitida
     */
    private boolean shouldSkipSection(int secX, int y, int secZ) {
        if (!(drone.level().getChunk(secX, secZ) instanceof LevelChunk chunk)) {
            return false;
        }

        int index = chunk.getSectionIndex(y);
        if (index < 0 || index >= chunk.getSectionsCount()) {
            return true;
        }

        LevelChunkSection section = chunk.getSection(index);
        if (section.hasOnlyAir()) {
            return true;
        }

        return !section.getStates().maybeHas(state -> !state.getFluidState().isEmpty());
    }

    /* =========================
       ANÁLISIS DE FLUIDOS
       ========================= */

    /**
     * Analiza un bloque para determinar si representa una amenaza de fluido.
     * <p>
     * Proceso de análisis:
     * <ol>
     *   <li>Verifica si el bloque contiene fluido</li>
     *   <li>Ignora bloques waterlogged</li>
     *   <li>Para fuentes: resuelve fuentes infinitas</li>
     *   <li>Para flujo: detecta filtraciones externas</li>
     * </ol>
     * </p>
     *
     * @param cursor Posición del bloque a analizar (mutable)
     * @param site Área de construcción
     * @return Posición de la amenaza de fluido, o null si no hay amenaza
     */
    private BlockPos analyzeBlockForFluid(BlockPos.MutableBlockPos cursor, AABB site) {
        FluidState fluid = drone.level().getFluidState(cursor);
        if (fluid.isEmpty()) {
            return null;
        }

        BlockState state = drone.level().getBlockState(cursor);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) &&
                state.getValue(BlockStateProperties.WATERLOGGED)) {
            return null;
        }

        BlockPos pos = cursor.immutable();

        if (fluid.isSource()) {
            if (isBlacklistedAndBlocked(pos)) {
                return null;
            }
            return resolveInfiniteSource(pos);
        }

        return detectExternalLeak(pos, site);
    }

    /**
     * Detecta si un fluido interno proviene de una filtración externa.
     * <p>
     * Explora las direcciones adyacentes buscando fluidos fuera del área de construcción.
     * Si encuentra fluido externo, traza su camino hasta la fuente.
     * </p>
     *
     * @param internal Posición del fluido interno
     * @param site Área de construcción
     * @return Posición de la fuente externa, o null si no hay filtración
     */
    private BlockPos detectExternalLeak(BlockPos internal, AABB site) {
        Level level = drone.level();

        for (Direction dir : TRACE_DIRECTIONS) {
            BlockPos next = internal.relative(dir);

            if (site.contains(Vec3.atCenterOf(next))) {
                continue;
            }

            if (level.getFluidState(next).isEmpty()) {
                continue;
            }

            BlockPos resolved = resolveLeakPath(next, site);
            if (resolved != null && !isBlacklistedAndBlocked(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    /**
     * Traza el camino de un fluido externo hasta su fuente.
     * <p>
     * Utiliza búsqueda en anchura (BFS) para seguir el flujo del fluido,
     * deteniéndose al encontrar una fuente reemplazable o al salir del área horizontal.
     * </p>
     *
     * @param start Posición inicial del fluido externo
     * @param site Área de construcción
     * @return Posición de la fuente del fluido, o null si no se encuentra
     */
    private @Nullable BlockPos resolveLeakPath(BlockPos start, AABB site) {
        Level level = drone.level();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        int steps = 0;
        while (!queue.isEmpty() && steps++ < MAX_TRACE_NODES) {
            BlockPos pos = queue.poll();

            if (!isInsideHorizontal(site, pos)) {
                return pos;
            }

            FluidState fluid = level.getFluidState(pos);
            if (fluid.isSource() && level.getBlockState(pos).canBeReplaced()) {
                return pos;
            }

            for (Direction dir : TRACE_DIRECTIONS) {
                BlockPos next = pos.relative(dir);
                if (!visited.add(next)) {
                    continue;
                }

                if (!level.getFluidState(next).isEmpty()) {
                    queue.add(next);
                }
            }
        }
        return null;
    }

    /**
     * Verifica si una posición está dentro de los límites horizontales del área.
     *
     * @param site Área de construcción
     * @param pos Posición a verificar
     * @return true si está dentro horizontalmente (X y Z)
     */
    private boolean isInsideHorizontal(AABB site, BlockPos pos) {
        return pos.getX() >= site.minX && pos.getX() < site.maxX
                && pos.getZ() >= site.minZ && pos.getZ() < site.maxZ;
    }

    /**
     * Resuelve fuentes infinitas de fluidos determinando qué bloque atacar.
     * <p>
     * Las fuentes infinitas (como el agua) se regeneran si tienen 2+ fuentes adyacentes.
     * resolveInfiniteSource identifica fuentes vecinas reemplazables para romper el patrón.
     * </p>
     *
     * @param pos Posición de la fuente de fluido
     * @return Posición óptima a atacar (fuente vecina reemplazable, o la posición original)
     */
    private BlockPos resolveInfiniteSource(BlockPos pos) {
        int sources = 0;
        BlockPos best = null;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(dir);
            if (!drone.level().getFluidState(n).isSource()) {
                continue;
            }

            sources++;
            if (drone.level().getBlockState(n).canBeReplaced()) {
                best = n;
            }
        }

        return (sources >= INFINITE_SOURCE_THRESHOLD && best != null) ? best : pos;
    }

    /* =========================
       UTILIDADES
       ========================= */

    /**
     * Intenta colocar un bloque removedor de fluidos en la posición especificada.
     *
     * @param pos Posición donde colocar el bloque
     * @return true si la colocación fue exitosa
     */
    private boolean placeBlock(BlockPos pos) {
        int slot = drone.getLogic().findSlotWithFluidRemoverBlock();
        if (slot == -1) {
            return false;
        }

        ItemStack stack = drone.getInventory().getStackInSlot(slot);
        return drone.getLogic().placeBlock(pos, stack);
    }

    /**
     * Verifica si el drone está obstruyendo físicamente una posición.
     *
     * @param pos Posición a verificar
     * @return true si las hitboxes del drone y el bloque se intersectan
     */
    private boolean isDroneInTheWay(BlockPos pos) {
        return drone.getBoundingBox().intersects(new AABB(pos).inflate(COLLISION_TOLERANCE));
    }

    /**
     * Verifica si una posición está en la blacklist Y es inaccesible.
     * <p>
     * Si la posición está blacklisteada pero ahora es accesible, la remueve de la blacklist.
     * </p>
     *
     * @param pos Posición a verificar
     * @return true si está blacklisteada e inaccesible
     */
    private boolean isBlacklistedAndBlocked(BlockPos pos) {
        if (!fluidBlacklist.contains(pos)) {
            return false;
        }

        if (!drone.getLogic().isBlockAccessible(pos)) {
            return true;
        }

        fluidBlacklist.remove(pos);
        return false;
    }

    /**
     * Añade una posición y sus adyacentes a la blacklist.
     * <p>
     * Esto previene que el drone intente acceder a posiciones problemáticas
     * y sus vecinos inmediatos.
     * </p>
     *
     * @param pos Posición central a blacklistear
     */
    private void blacklistPositionAndAdjacent(BlockPos pos) {
        fluidBlacklist.add(pos);
        for (Direction dir : TRACE_DIRECTIONS) {
            fluidBlacklist.add(pos.relative(dir));
        }
    }

    /**
     * Remueve una posición y todas sus adyacentes de la blacklist.
     * <p>
     * Se utiliza después de una colocación exitosa de bloque, ya que
     * la situación puede haber cambiado para los bloques vecinos.
     * </p>
     *
     * @param pos Posición central a limpiar
     */
    private void removeFromBlacklistWithAdjacent(BlockPos pos) {
        fluidBlacklist.remove(pos);
        for (Direction dir : Direction.values()) {
            fluidBlacklist.remove(pos.relative(dir));
        }
    }

    /* =========================
       CLASES AUXILIARES
       ========================= */

    /**
     * Representa una amenaza de fluido con su posición y prioridad.
     * <p>
     * Implementa Comparable para su uso en PriorityQueue, donde
     * mayor prioridad = procesar primero.
     * </p>
     */
    private record FluidThreat(BlockPos pos, int priority) implements Comparable<FluidThreat> {
    @Override
        public int compareTo(FluidThreat o) {
            return Integer.compare(o.priority, this.priority);
        }
    }

    /**
     * Contexto de escaneo que agrupa parámetros compartidos entre métodos de escaneo.
     * <p>
     * Reduce el número de parámetros pasados entre métodos y centraliza
     * la configuración del escaneo.
     * </p>
     */
    private record ScanContext(AABB site,
                               boolean scanningDown,
                               int siteMinX, int siteMaxX,
                               int siteMinZ, int siteMaxZ,
                               int startSectionY, int endSectionY,
                               int effectiveEndY, int startY) { }

    /**
     * Límites de bloques dentro de una sección de chunk.
     * <p>
     * Define el rango de coordenadas [min, max] inclusive para cada eje
     * dentro de la intersección entre una sección y el área de construcción.
     * </p>
     */
    private record SectionBounds(int x0, int x1, int y0, int y1, int z0, int z1) { }
}