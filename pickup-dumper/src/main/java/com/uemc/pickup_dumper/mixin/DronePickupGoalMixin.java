package com.uemc.pickup_dumper.mixin;

import com.uemc.pickup_dumper.PerimeterContainerCache;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.entities.drone.goals.DronePickupGoal;
import com.uemc.assistance_drone.items.SitePlanner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Mixin on {@link DronePickupGoal} that adds autonomous inventory dumping to
 * adjacent storage containers when the drone's inventory is full.
 *
 * <h2>Dump-mode lifecycle</h2>
 * <ol>
 *   <li>The drone's storage slots are all occupied.</li>
 *   <li>{@code canUse} detects the condition and sets {@code ad$dumpMode = true}
 *       if the perimeter cache knows at least one reachable container.</li>
 *   <li>Each {@code tick} navigates to the nearest accessible container and
 *       calls {@link #ad$depositItems()}, preserving {@value #AD$MIN_OCCUPIED_SLOTS}
 *       occupied slots to retain blocks needed by other goals.</li>
 *   <li>When the drone has freed enough slots, dump mode exits and the goal
 *       resumes normal pickup behaviour.</li>
 * </ol>
 *
 * <h2>Item-loss safety</h2>
 * Item transfer follows a strict simulate → extract → insert → recover pattern.
 * If the container rejects items during the real insertion (e.g. due to a
 * concurrent modification by a player or another goal between the simulation
 * and the actual call), any leftover is first attempted to be returned to the
 * drone. If the drone is also full at that exact moment, the items are spawned
 * as {@code ItemEntity} via  rather than silently
 * deleted.
 *
 * <h2>Performance contract</h2>
 * All perimeter scanning is delegated to {@link PerimeterContainerCache}, which
 * processes at most 64 block positions per game tick via an amortised cursor.
 * No heavy loops execute inside the injected methods themselves.
 */
@Mixin(value = DronePickupGoal.class, remap = false)
public abstract class DronePickupGoalMixin {

    /**
     * Number of storage slots (indices 1–12) that must remain occupied after
     * each dump cycle. This preserves blocks required by other goals such as
     * {@code DroneFluidHandlerGoal}.
     */
    @Unique
    private static final int AD$MIN_OCCUPIED_SLOTS = 4;

    @Shadow private DroneEntity drone;
    @Shadow private Predicate<String> activationCondition;

    @Unique private boolean               ad$dumpMode             = false;
    @Unique private BlockPos              ad$dumpTarget           = null;
    @Unique private boolean               ad$containerFull        = false;
    @Unique private final PerimeterContainerCache ad$cache = new PerimeterContainerCache();

    // ----------------------------------------------------------------
    // canUse
    // ----------------------------------------------------------------

    /**
     * Intercepts goal activation to trigger dump mode when the drone's
     * inventory is full and at least one container is known to the cache.
     *
     * <p>If dump mode is already active, the method short-circuits with
     * {@code true} to keep the goal alive, or resets and falls through to
     * the original logic if dumping is no longer needed.
     */
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void ad$onCanUse(CallbackInfoReturnable<Boolean> cir) {
        if (drone == null || drone.level().isClientSide()) return;
        if (!activationCondition.test(drone.getState())) return;

        ad$advanceCache();

        if (ad$dumpMode) {
            if (ad$dumpingRequired()) {
                cir.setReturnValue(true);
            } else {
                ad$exitDumpMode();
            }
            return;
        }

        if (!drone.getLogic().hasAnyInventorySpace() && ad$cache.hasValidContainers()) {
            ad$dumpMode   = true;
            ad$dumpTarget = null;
            cir.setReturnValue(true);
        }
    }

    // ----------------------------------------------------------------
    // canContinueToUse
    // ----------------------------------------------------------------

    /**
     * Keeps the goal active while dump mode is in progress, bypassing the
     * original check (which would return {@code false} because the inventory
     * is full). Exits cleanly if the drone state changed or dumping finished.
     */
    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void ad$onCanContinueToUse(CallbackInfoReturnable<Boolean> cir) {
        if (!ad$dumpMode) return;

        boolean stateInvalid = !activationCondition.test(drone.getState());
        boolean stuck        = drone.getNavigation().isStuck();

        if (stateInvalid || stuck || !ad$dumpingRequired()) {
            ad$exitDumpMode();
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(true);
    }

    // ----------------------------------------------------------------
    // tick
    // ----------------------------------------------------------------

    /**
     * Replaces the standard pickup tick while dump mode is active.
     * Navigates to the nearest accessible container and deposits items,
     * then cancels the original tick body to prevent mixed behaviour.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ad$onTick(CallbackInfo ci) {
        if (!ad$dumpMode) return;
        if (drone.level().isClientSide()) { ci.cancel(); return; }

        ad$advanceCache();

        if (ad$dumpTarget == null || !ad$isTargetAlive()) {
            ad$dumpTarget    = ad$nearestContainer();
            ad$containerFull = false;
        }

        if (ad$dumpTarget == null) {
            ci.cancel();
            return;
        }

        drone.getLookControl().setLookAt(ad$dumpTarget.getCenter());

        if (drone.getLogic().isInRangeToInteract(ad$dumpTarget)) {
            drone.getNavigation().stop();
            ad$depositItems();
        } else {
            drone.getLogic().executeMovement(ad$dumpTarget.getCenter());
        }

        ci.cancel();
    }

    // ----------------------------------------------------------------
    // stop
    // ----------------------------------------------------------------

    /**
     * Ensures dump-mode state is fully cleaned up whenever the goal stops,
     * regardless of the reason (state change, interruption, server shutdown).
     */
    @Inject(method = "stop", at = @At("TAIL"))
    private void ad$onStop(CallbackInfo ci) {
        ad$exitDumpMode();
    }

    // ----------------------------------------------------------------
    // Item deposit — race-condition-safe
    // ----------------------------------------------------------------

    /**
     * Transfers items from the drone's storage slots (indices 1–12) into the
     * current dump target container, preserving at least
     * {@value #AD$MIN_OCCUPIED_SLOTS} occupied slots.
     *
     * <h3>Transfer protocol (atomic-safe)</h3>
     * <ol>
     *   <li><b>Simulate</b> — dry-run insertion to determine how many items
     *       the container can accept without touching any inventory.</li>
     *   <li><b>Extract</b> — remove exactly that many items from the drone.</li>
     *   <li><b>Insert</b> — perform the real insertion into the container.</li>
     *   <li><b>Recover</b> — if the container returned a non-empty remainder
     *       (possible if its state changed between steps 1 and 3), attempt to
     *       return the items to the drone. If the drone is also full, the items
     *       are spawned as an {@code ItemEntity} at the drone's position.
     *       This ensures zero item loss under any
     *       concurrency scenario.</li>
     * </ol>
     */
    @Unique
    private void ad$depositItems() {
        if (ad$dumpTarget == null) return;

        IItemHandler container = PerimeterContainerCache.getHandler(drone.level(), ad$dumpTarget);
        if (container == null) {
            ad$cache.evict(ad$dumpTarget);
            ad$dumpTarget = null;
            return;
        }

        ItemStackHandler droneInv  = drone.getInventory();
        int              totalSlots = droneInv.getSlots();
        int              occupied   = ad$countOccupiedSlots();

        for (int i = totalSlots - 1; i >= 1; i--) {
            if (occupied <= AD$MIN_OCCUPIED_SLOTS) break;

            ItemStack stack = droneInv.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // Step 1 — simulate
            ItemStack simRemainder = ItemHandlerHelper.insertItemStacked(container, stack.copy(), true);
            int canInsert = stack.getCount() - simRemainder.getCount();

            if (canInsert <= 0) {
                ad$containerFull = true;
                break;
            }

            // Step 2 — extract from drone
            ItemStack extracted = droneInv.extractItem(i, canInsert, false);

            // Step 3 — real insertion into container
            ItemStack rejected = ItemHandlerHelper.insertItemStacked(container, extracted, false);

            // Step 4 — recover any rejected items
            if (!rejected.isEmpty()) {
                ItemStack stillRejected = ItemHandlerHelper.insertItemStacked(droneInv, rejected, false);
                if (!stillRejected.isEmpty()) {
                    // Drone also full: spawn as item entity — never lose items
                    Block.popResource(drone.level(), drone.blockPosition(), stillRejected);
                }
                ad$containerFull = true;
            }

            if (droneInv.getStackInSlot(i).isEmpty()) occupied--;
        }

        if (ad$containerFull) {
            ad$dumpTarget    = null;
            ad$containerFull = false;
        }

        if (!ad$dumpingRequired()) {
            ad$exitDumpMode();
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /** Forwards a cache tick, relying on the cache's own per-tick guard. */
    @Unique
    private void ad$advanceCache() {
        if (drone == null || drone.level().isClientSide()) return;
        ItemStack planner = drone.getInventory().getStackInSlot(0);
        if (SitePlanner.isConfigured(planner)) {
            ad$cache.tick(drone.level(), planner);
        }
    }

    /**
     * Returns {@code true} if there are more occupied storage slots than the
     * protected minimum and the cache has containers available.
     */
    @Unique
    private boolean ad$dumpingRequired() {
        return ad$cache.hasValidContainers() && ad$countOccupiedSlots() > AD$MIN_OCCUPIED_SLOTS;
    }

    /** Counts occupied storage slots (indices 1 through {@code slots - 1}). */
    @Unique
    private int ad$countOccupiedSlots() {
        ItemStackHandler inv   = drone.getInventory();
        int              count = 0;
        for (int i = 1; i < inv.getSlots(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) count++;
        }
        return count;
    }

    /** Returns {@code true} if the current target's {@code BlockEntity} still exists. */
    @Unique
    private boolean ad$isTargetAlive() {
        return ad$dumpTarget != null && drone.level().getBlockEntity(ad$dumpTarget) != null;
    }

    /**
     * Queries the cache for the nearest container that the drone's navigation
     * logic considers accessible, evicting dead entries along the way.
     */
    @Unique
    private BlockPos ad$nearestContainer() {
        List<BlockPos> candidates = ad$cache.getSortedContainers(drone.blockPosition());
        for (BlockPos pos : candidates) {
            if (drone.level().getBlockEntity(pos) == null) {
                ad$cache.evict(pos);
                continue;
            }
            if (drone.getLogic().isBlockAccessible(pos)) return pos;
        }
        return null;
    }

    /** Resets all dump-mode fields to their initial state. */
    @Unique
    private void ad$exitDumpMode() {
        ad$dumpMode      = false;
        ad$dumpTarget    = null;
        ad$containerFull = false;
    }
}
