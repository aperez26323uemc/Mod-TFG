package com.uemc.assistance_drone.entities.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DroneAiLogic {

    private static final Logger LOGGER = LoggerFactory.getLogger(DroneAiLogic.class);

    private final DroneEntity drone;

    private static final ItemStack TOOL_PICKAXE = new ItemStack(Items.IRON_PICKAXE);
    private static final ItemStack TOOL_AXE = new ItemStack(Items.IRON_AXE);
    private static final ItemStack TOOL_SHOVEL = new ItemStack(Items.IRON_SHOVEL);
    private static final ItemStack TOOL_HOE = new ItemStack(Items.IRON_HOE);

    private float destroyProgress = 0.0F;
    private int soundCooldown = 0;
    private BlockPos currentMiningPos = null;

    public DroneAiLogic(DroneEntity drone) {
        this.drone = drone;
        LOGGER.debug("DroneAiLogic created for drone {}", drone.getId());
    }

    // --- MOVIMIENTO ---

    public void executeMovement(Vec3 targetPos) {
        LOGGER.debug("executeMovement(targetPos={})", targetPos);

        if (targetPos == null) return;
        double distSqr = drone.position().distanceToSqr(targetPos);


        if (distSqr > 1.2) {
            if (canSeeTarget(targetPos)) {
                drone.getNavigation().stop();
                drone.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, 1.0);
                LOGGER.debug("executeMovement with movecontrol");
            } else {
                drone.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
                LOGGER.debug("executeMovement with pathfinding");
            }
        } else {
            drone.getNavigation().stop();
            drone.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, 1.0);
            LOGGER.debug("executeMovement with movecontrol 2");
        }
    }

    public Vec3 getSafetyTarget(Player owner, double distance) {
        LOGGER.debug("getSafetyTarget(owner={}, distance={})", owner.getName().getString(), distance);

        Vec3 dir = drone.position().subtract(owner.position());
        if (dir.lengthSqr() < 0.01) dir = new Vec3(1, 0, 0);
        dir = dir.normalize();
        Vec3 safetyPoint = owner.position().add(dir.scale(distance));
        Vec3 result = calculateIdealPosition(safetyPoint.x, owner.getEyeY(), safetyPoint.z);

        LOGGER.debug("getSafetyTarget result={}", result);
        return result;
    }

    public Vec3 calculateIdealPosition(double x, double y, double z) {
        LOGGER.debug("calculateIdealPosition(x={}, y={}, z={})", x, y, z);

        double floorY = getFloorY(x, y, z);
        if (y - floorY < 1.5) {
            y = floorY + 1.5;
        }

        Vec3 result = new Vec3(x, y, z);
        LOGGER.debug("calculateIdealPosition result={}", result);
        return result;
    }

    // --- ACCESIBILIDAD ---

    public boolean isInRangeToInteract(BlockPos pos) {
        LOGGER.debug("isInRangeToInteract(pos={})", pos);

        Vec3 center = Vec3.atCenterOf(pos);
        if (drone.distanceToSqr(center) > 4) return false;

        BlockHitResult result = drone.level().clip(new ClipContext(
                drone.getEyePosition(), center,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone
        ));

        boolean can = result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(pos)
                || (drone.distanceToSqr(center) > 1 && isBlockAccessible(pos));

        LOGGER.debug("isInRangeToInteract result={}", can);
        return can;
    }

    public boolean isBlockAccessible(BlockPos pos) {
        LOGGER.debug("isBlockAccessible(pos={})", pos);

        if (drone.blockPosition().distManhattan(pos) <= 1) return true;

        Vec3 center = Vec3.atCenterOf(pos);
        BlockHitResult result = drone.level().clip(new ClipContext(
                drone.blockPosition().getCenter(), center,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone
        ));

        if (result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(pos)) return true;

        Path path = drone.getNavigation().createPath(pos, 1);
        boolean can = path != null && path.canReach();

        LOGGER.debug("isBlockAccessible result={}", can);
        return can;
    }

    // --- INVENTARIO ---

    public boolean hasInventorySpaceFor(ItemStack item) {
        LOGGER.debug("hasInventorySpaceFor(item={})", item);

        if (item.isEmpty()) return true;
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(drone.getInventory(), item.copy(), true);
        boolean can = remainder.getCount() < item.getCount();

        LOGGER.debug("hasInventorySpaceFor result={}", can);
        return can;
    }

    public int findBlockSlot() {
        for (int i = 0; i < drone.getInventory().getSlots(); i++) {
            ItemStack stack = drone.getInventory().getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().isSolid()) {
                return i;
            }
        }
        return -1;
    }

    public ItemStack itemStore(ItemStack item) {
        LOGGER.debug("itemStore(item={})", item);
        ItemStack result = ItemHandlerHelper.insertItemStacked(drone.getInventory(), item, false);
        LOGGER.debug("itemStore remainder={}", result);
        return result;
    }

    public boolean itemPickUp() {
        LOGGER.debug("itemPickUp()");

        AABB pickupArea = drone.getBoundingBox().inflate(1.0, 0.5, 1.0);
        List<ItemEntity> items = drone.level().getEntitiesOfClass(ItemEntity.class, pickupArea);

        boolean pickedSomething = false;

        for (ItemEntity itemEntity : items) {
            if (itemEntity.isAlive() && !itemEntity.getItem().isEmpty()) {
                ItemStack stack = itemEntity.getItem();
                ItemStack remainder = itemStore(stack);

                if (remainder.isEmpty()) {
                    itemEntity.discard();
                    pickedSomething = true;
                } else {
                    itemEntity.setItem(remainder);
                    if (remainder.getCount() == stack.getCount()) {
                        LOGGER.debug("Inventory full during pickup");
                        return pickedSomething;
                    }
                }
            }
        }
        LOGGER.debug("itemPickUp result={}", pickedSomething);
        return pickedSomething;
    }

    // --- MINERÍA ---

    public boolean mineBlock(BlockPos pos) {
        LOGGER.debug("mineBlock(pos={})", pos);

        Level level = drone.level();

        if (currentMiningPos == null || !currentMiningPos.equals(pos)) {
            LOGGER.debug("currentMiningPos={}", currentMiningPos);
            resetMiningState();
            currentMiningPos = pos;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            resetMiningState();
            return true;
        }

        ItemStack tool = getBestTool(state);
        float hardness = state.getDestroySpeed(level, pos);
        float speed = tool.getDestroySpeed(state);
        if (speed < 1.0f) speed = 1.0f;

        if (drone.hasEffect(MobEffects.DIG_SPEED)) {
            int amplifier = drone.getEffect(MobEffects.DIG_SPEED).getAmplifier();
            speed *= 1.0F + (amplifier + 1) * 0.2F;
        }

        float damage = speed / hardness / 30.0F;
        this.destroyProgress += damage;

        int i = (int) (this.destroyProgress * 10.0F);
        level.destroyBlockProgress(drone.getId(), pos, i);

        if (this.soundCooldown++ % 4 == 0) {
            level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS,
                    (state.getSoundType().getVolume() + 1.0F) / 8.0F,
                    state.getSoundType().getPitch() * 0.5F);
        }

        if (this.destroyProgress >= 1.0F) {
            breakAndDrop(level, pos, state, tool);
            resetMiningState();
            LOGGER.debug("mineBlock finished=true");
            return true;
        }

        return false;
    }

    public void resetMiningState() {
        LOGGER.debug("resetMiningState()");
        if (currentMiningPos != null) {
            drone.level().destroyBlockProgress(drone.getId(), currentMiningPos, -1);
        }
        this.destroyProgress = 0.0F;
        this.soundCooldown = 0;
        this.currentMiningPos = null;
    }

    public void applyMiningSpeedBuffs(Level level) {
        LOGGER.debug("applyMiningSpeedBuffs()");

        if (drone.tickCount % 40 != 0) return;
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, drone.getBoundingBox().inflate(50));
        for (ServerPlayer player : players) {
            if (player.hasEffect(MobEffects.DIG_SPEED)) {
                drone.addEffect(new MobEffectInstance(
                        MobEffects.DIG_SPEED,
                        100,
                        player.getEffect(MobEffects.DIG_SPEED).getAmplifier(),
                        true,
                        false));
                break;
            }
        }
    }

    // --- CONSTRUCCIÓN ---

    public boolean placeBlock(BlockPos pos, ItemStack stack) {
        LOGGER.debug("placeBlock(pos={}, stack={})", pos, stack);

        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem))
            return false;

        Level level = drone.level();

        if (!level.getBlockState(pos).canBeReplaced())
            return false;

        BlockPlaceContext context = new BlockPlaceContext(
                level,
                null,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(
                        Vec3.atCenterOf(pos),
                        Direction.UP,
                        pos,
                        false
                )
        );

        InteractionResult result = blockItem.place(context);
        boolean success = result.consumesAction();

        LOGGER.debug("placeBlock result={}", success);
        return success;
    }

    /**
     * Lanza un rayo desde el dron hacia el objetivo. Si choca con un bloque antes de llegar,
     * devuelve la posición de ese bloque obstáculo.
     */
    public BlockPos getObstructionBlock(BlockPos targetPos) {
        Vec3 start = drone.getEyePosition();
        Vec3 end = Vec3.atCenterOf(targetPos);

        BlockHitResult result = drone.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER, // Buscamos sólidos
                ClipContext.Fluid.NONE,     // Ignoramos agua
                drone
        ));

        // Si chocamos con un bloque
        if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = result.getBlockPos();
            // Si el bloque con el que chocamos NO es el objetivo al que queríamos ir, es un obstáculo
            if (!hitPos.equals(targetPos)) {
                LOGGER.debug("Obstruction detected at {}", hitPos);
                return hitPos;
            }
        }
        return null;
    }

    // --- HELPERS PRIVADOS ---

    private void breakAndDrop(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        LOGGER.debug("breakAndDrop(pos={}, state={}, tool={})", pos, state.getBlock(), tool);

        if (level instanceof ServerLevel serverLevel) {
            LootParams.Builder lootParams = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, tool)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, drone);

            List<ItemStack> drops = state.getDrops(lootParams);
            for (ItemStack drop : drops) {
                ItemStack remaining = itemStore(drop);
                if (!remaining.isEmpty()) {
                    Block.popResource(level, pos, remaining);
                }
            }

            level.destroyBlock(pos, false);
            level.levelEvent(2001, pos, Block.getId(state));
        }
    }

    private ItemStack getBestTool(BlockState state) {
        LOGGER.debug("getBestTool(state={})", state.getBlock());

        float p = TOOL_PICKAXE.getDestroySpeed(state);
        float s = TOOL_SHOVEL.getDestroySpeed(state);
        float a = TOOL_AXE.getDestroySpeed(state);
        float h = TOOL_HOE.getDestroySpeed(state);
        float max = Math.max(p, Math.max(s, Math.max(a, h)));

        ItemStack result;
        if (max == p) result = TOOL_PICKAXE;
        else if (max == s) result = TOOL_SHOVEL;
        else if (max == a) result = TOOL_AXE;
        else result = TOOL_HOE;

        LOGGER.debug("getBestTool result={}", result.getItem());
        return result;
    }

    private double getFloorY(double x, double y, double z) {
        // 1. Punto de inicio: La posición exacta (x, y, z)
        Vec3 start = new Vec3(x, y, z);

        // 2. Punto final: El fondo absoluto del mundo.
        // Usamos getMinBuildHeight() para que funcione tanto en el Overworld (-64) como en el Nether (0).
        // Le restamos 1.0 extra para asegurar que detecte el bloque final si está justo en el límite.
        Vec3 end = new Vec3(x, drone.level().getMinBuildHeight() - 1.0, z);

        // 3. Lanzamos el rayo (Raycast)
        BlockHitResult result = drone.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER, // Queremos bloques sólidos (Colliders)
                ClipContext.Fluid.NONE,     // Ignorar agua (puedes cambiarlo si quieres que flote en agua)
                drone                       // Entidad para ignorar su propia hitbox
        ));

        // 4. Si chocamos con algo, devolvemos esa altura exacta
        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getLocation().y;
        }

        // 5. Fallback de seguridad: Si hay vacío (Void), devolvemos la altura mínima
        // Esto evita el "-500" y hace que el dron baje hasta el límite pero no intente subir al infinito.
        return drone.level().getMinBuildHeight();
    }

    private boolean canSeeTarget(Vec3 target) {
        LOGGER.debug("canSeeTarget(target={})", target); // Opcional

        Vec3 start = new Vec3(drone.getX(), drone.getY(), drone.getZ());

        BlockPos targetPos = BlockPos.containing(target);

        BlockPos startpos = BlockPos.containing(start);
        // CORRECCIÓN 1: Usar Fluid.NONE para ignorar el agua/lava
        BlockHitResult result = drone.level().clip(new ClipContext(
                startpos.getCenter(), targetPos.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone
        ));


        // Permitimos que sea MISS (no chocó con nada antes de llegar)
        // O que chocara, pero que el bloque con el que chocó sea exactamente nuestro objetivo.
        boolean can = result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(targetPos);

        LOGGER.debug("canSeeTarget result={}", can);
        return can;
    }
}
