package com.uemc.assistance_drone.entities.drone.goals;

import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

public class DronePickupGoal extends Goal {
    private final DroneEntity drone;
    private final Predicate<String> activationCondition;

    // Variables de control
    private ItemEntity targetItem;
    private int checkCooldown = 0; // Para el chequeo cada 0.5s
    private static final int CHECK_INTERVAL = 10; // 10 ticks = 0.5 segundos
    private static final double PICKUP_REACH_SQR = 0.8 * 0.8; // 0.8 bloques al cuadrado para comparaciones rápidas

    public DronePickupGoal(DroneEntity drone, Predicate<String> activationCondition) {
        this.drone = drone;
        this.activationCondition = activationCondition;
        // Reclamamos MOVE y LOOK (Exclusividad mientras trabaja)
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 1. Chequeo de estado externo (Inyectado)
        if (!activationCondition.test(drone.getState())) {
            return false;
        }

        // 2. Optimización: Solo chequeamos cada 0.5s si no tenemos objetivo
        if (this.checkCooldown > 0) {
            this.checkCooldown--;
            return false;
        }
        this.checkCooldown = CHECK_INTERVAL;

        // 3. Buscar objetivo
        this.targetItem = findClosestItem();
        return this.targetItem != null;
    }

    @Override
    public boolean canContinueToUse() {
        // Seguimos si el estado es válido, el item existe, no ha muerto y no lo hemos recogido aún
        return activationCondition.test(drone.getState())
                && this.targetItem != null
                && this.targetItem.isAlive()
                && !this.targetItem.getItem().isEmpty(); // Que el stack en el suelo no esté vacío
    }

    @Override
    public void start() {
        // Iniciamos movimiento
        this.drone.getNavigation().moveTo(this.targetItem, 1.0D);
    }

    @Override
    public void stop() {
        // Limpieza al terminar o ser interrumpido
        this.targetItem = null;
        this.drone.getNavigation().stop();
        Vec3 current = drone.getLogic().calculateIdealPosition(drone.getX(), drone.getY(), drone.getZ());
        Vec3 targetPos = new Vec3(current.x, current.y, current.z);
        drone.getLogic().executeMovement(targetPos);
    }

    @Override
    public void tick() {
        if (this.targetItem == null) return;

        // 1. Mirar al ítem
        this.drone.getLookControl().setLookAt(this.targetItem, 30.0F, 30.0F);

        // 2. Navegación constante (por si el ítem se mueve)
        if (this.drone.tickCount % 5 == 0) { // No recálcules el path cada tick, es caro
            this.drone.getNavigation().moveTo(this.targetItem, 1.0D);
        }

        // 3. --- OSCILACIÓN AGRESIVA ---
        double oscillation = Math.sin(this.drone.tickCount * 0.3) * 0.05;

        // Inyectamos la velocidad directamente. Si la oscilación es negativa, empuja abajo.
        this.drone.setDeltaMovement(this.drone.getDeltaMovement().add(0, oscillation, 0));

        // 4. Verificar distancia y Recoger
        double distSqr = this.drone.distanceToSqr(this.targetItem);
        if (distSqr <= PICKUP_REACH_SQR) {
            pickUpItem();
        }
    }

    // --- LÓGICA INTERNA ---

    private void pickUpItem() {
        ItemStack stackOnGround = this.targetItem.getItem();

        // Intentamos meter el item en el inventario del Dron
        // ItemHandlerHelper maneja automáticamente slots, stacks parciales, etc.
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(this.drone.getInventory(), stackOnGround, false);

        if (remainder.isEmpty()) {
            this.targetItem.discard();
            this.targetItem = null;
        } else {
            // Se guardó PARTE (inventario casi lleno): Actualizamos lo que queda en el suelo
            this.targetItem.setItem(remainder);
            this.stop();
        }
    }

    private ItemEntity findClosestItem() {
        // 1. Obtener el Site Planner del slot 0
        ItemStack plannerStack = this.drone.getInventory().getStackInSlot(0);

        // 2. Validar que tenemos área configurada
        if (!SitePlanner.isConfigured(plannerStack)) {
            return null;
        }

        BlockPos start = SitePlanner.getStartPos(plannerStack);
        BlockPos end = SitePlanner.getEndPos(plannerStack);

        if(start == null || end == null) return null;
        // 3. Crear la caja de búsqueda (AABB)
        // AABB toma min y max, así que ordenamos las coordenadas
        AABB searchArea = new AABB(start).minmax(new AABB(end)).expandTowards(1, 1, 1); // Expandimos +1 para cubrir el bloque completo final

        // 4. Obtener entidades en el área
        List<ItemEntity> items = this.drone.level().getEntitiesOfClass(ItemEntity.class, searchArea);

        // 5. Filtrar y Ordenar
        return items.stream()
                .filter(ItemEntity::isAlive) // Solo items vivos
                .filter(item -> !item.getItem().isEmpty()) // Que no sean fantasmas
                // Opcional: Filtrar si el inventario del dron ya está lleno para este item específico
                .min(Comparator.comparingDouble(this.drone::distanceToSqr)) // El más cercano
                .orElse(null);
    }
}