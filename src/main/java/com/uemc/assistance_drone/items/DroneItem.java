package com.uemc.assistance_drone.items;

import com.uemc.assistance_drone.entities.ModEntities;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DroneItem extends Item {

    public static final String ID = "drone_item";
    private static final double SPAWN_RANGE = 5.0;

    public DroneItem(Properties pProperties) {
        super(pProperties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hitResult = player.pick(SPAWN_RANGE, 0, false);

        // Solo spawnear si se apunta a un bloque
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;

            // Calcular posición de spawn evitando sofocación
            Vec3 spawnPos = calculateSpawnPosition(blockHit);

            DroneEntity drone = new DroneEntity(ModEntities.DRONE_ENTITY_TYPE.get(), level);
            drone.setOwner(player);
            drone.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

            // Verificar colisiones antes de spawnear
            if (level.noCollision(drone)) {
                level.addFreshEntity(drone);

                stack.shrink(1);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
        }

        return InteractionResultHolder.fail(stack);
    }

    private Vec3 calculateSpawnPosition(BlockHitResult hitResult) {
        Vec3 hitLocation = hitResult.getLocation();
        Vec3 normalOffset = hitLocation.normalize().scale(0.5);
        return hitLocation.add(normalOffset);
    }
}