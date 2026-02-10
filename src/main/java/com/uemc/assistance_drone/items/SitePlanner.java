package com.uemc.assistance_drone.items;

import com.uemc.assistance_drone.util.ModKeys;
import com.uemc.assistance_drone.util.SiteMarkersRegister;
import com.uemc.assistance_drone.util.SiteSelectionValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public class SitePlanner extends Item {
    public static final String ID = ModKeys.SITE_PLANNER_ITEM_KEY;

    public SitePlanner(Properties properties) {
        // No stackable
        super(properties.stacksTo(1));
    }

    // --- Getters Públicos ---

    @Nullable
    public static BlockPos getStartPos(ItemStack stack) {
        return stack.get(SiteMarkersRegister.START_POS);
    }

    @Nullable
    public static BlockPos getEndPos(ItemStack stack) {
        return stack.get(SiteMarkersRegister.END_POS);
    }

    /**
     * Verifica si el item tiene una configuración válida (Inicio y Fin definidos).
     */
    public static boolean isConfigured(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof SitePlanner
                && getStartPos(stack) != null
                && getEndPos(stack) != null;
    }

    // --- Interacciones ---

    /**
     * Click Izquierdo: Sin acción
     */
    @Override
    public boolean canAttackBlock(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer) {
        // Click izquierdo -> nada
        return false;
    }

    /**
     * Click Derecho: Gestiona selección según estado actual
     * <p>
     * Controles:
     * - Click derecho sin selección → empieza selección
     * - Click derecho con selección a medias → intenta terminar selección
     * - Click derecho con selección completa → nada
     * - Shift + Click derecho con selección completa o a medias → limpia selección
     * - Resto de shift + click → nada
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos start = getStartPos(stack);
        BlockPos end = getEndPos(stack);
        boolean isSneaking = player.isShiftKeyDown();

        // Shift + Click derecho con selección completa o a medias → limpia selección
        if (isSneaking && start != null) {
            clearSelection(stack);
            player.displayClientMessage(
                    Component.translatable(ModKeys.GUI_SITE_PLANNER_CLEARED),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        // Resto de shift + click → nada
        if (isSneaking) {
            return InteractionResult.PASS;
        }

        // Click derecho sin selección → empieza selección
        if (start == null) {
            setStartPos(stack, clickedPos);
            player.displayClientMessage(
                    Component.translatable(ModKeys.GUI_SITE_PLANNER_START_SET),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        // Click derecho con selección a medias → intenta terminar selección
        if (end == null) {
            SiteSelectionValidator.ValidationResult validation =
                    SiteSelectionValidator.validateSelection(start, clickedPos);

            if (!validation.valid) {
                // Error de validación
                player.displayClientMessage(validation.errorMessage, true);
                return InteractionResult.FAIL;
            }

            // Válido - guardar
            setEndPos(stack, clickedPos);
            player.displayClientMessage(
                    Component.translatable(
                            ModKeys.GUI_SITE_PLANNER_END_SET, validation.dimensions.volume()
                    ),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        // Click derecho con selección completa → nada
        return InteractionResult.PASS;
    }

    /**
     * Tooltip Dinámico
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        BlockPos start = getStartPos(stack);
        BlockPos end = getEndPos(stack);

        if (start == null) {
            // No hay selección
            tooltipComponents.add(Component.translatable(ModKeys.TOOLTIP_SITE_PLANNER_NO_SELECTION)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            // Mostrar inicio
            tooltipComponents.add(Component.translatable(
                            ModKeys.TOOLTIP_SITE_PLANNER_START,
                            start.getX(), start.getY(), start.getZ())
                    .withStyle(ChatFormatting.GREEN));

            if (end == null) {
                // Falta el final
                tooltipComponents.add(Component.translatable(ModKeys.TOOLTIP_SITE_PLANNER_INCOMPLETE)
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                // Selección completa
                tooltipComponents.add(Component.translatable(
                                ModKeys.TOOLTIP_SITE_PLANNER_END,
                                end.getX(), end.getY(), end.getZ())
                        .withStyle(ChatFormatting.AQUA));

                SiteSelectionValidator.SelectionDimensions dims =
                        SiteSelectionValidator.calculateDimensions(start, end);
                tooltipComponents.add(Component.translatable(
                                ModKeys.TOOLTIP_SITE_PLANNER_VOLUME, dims.volume())
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    // --- Setters Privados ---

    private void setStartPos(ItemStack stack, BlockPos pos) {
        stack.set(SiteMarkersRegister.START_POS, pos);
    }

    private void setEndPos(ItemStack stack, BlockPos pos) {
        stack.set(SiteMarkersRegister.END_POS, pos);
    }

    private void clearSelection(ItemStack stack) {
        stack.remove(SiteMarkersRegister.START_POS);
        stack.remove(SiteMarkersRegister.END_POS);
    }
}