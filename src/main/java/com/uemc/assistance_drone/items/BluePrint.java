package com.uemc.assistance_drone.items;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.Objects;

// BlockSelectorItem.java
public class BluePrint extends Item {
    public static final String ID = "blue_print";

    public BluePrint(Properties properties) {
        super(properties);
    }

    /**
     * Right-click behaviour
     */
    @Override
    public boolean canAttackBlock(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer) {
        hit(pPlayer);
        return false;
    }
    private void hit(Player pPlayer) {
        BlockHitResult hit = (BlockHitResult) pPlayer.pick(5.0, 0, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            ItemStack stack = pPlayer.getItemInHand(InteractionHand.MAIN_HAND);
            setEndPos(stack, hit.getBlockPos());
            pPlayer.displayClientMessage(Component.literal("End position set!"), true);
        }
    }

    /**
     * Left-click behaviour
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            ItemStack stack = context.getItemInHand();
            setStartPos(stack, context.getClickedPos());
            Objects.requireNonNull(context.getPlayer()).displayClientMessage(Component.literal("Start position set!"), true);
        }
        return InteractionResult.SUCCESS;
    }

    private void setStartPos(ItemStack stack, BlockPos pos) {
        stack.set(ModItems.START_POS, pos);
    }

    private void setEndPos(ItemStack stack, BlockPos pos) {
        stack.set(ModItems.END_POS, pos);
    }

    @Nullable
    public static BlockPos getStartPos(ItemStack stack) {
        return stack.get(ModItems.START_POS);
    }

    @Nullable
    public static BlockPos getEndPos(ItemStack stack) {
        return stack.get(ModItems.END_POS);
    }
}