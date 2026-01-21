package com.uemc.assistance_drone.items;

import com.uemc.assistance_drone.util.ModKeys;
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
        super(properties);
    }

    // --- Helper para calcular volumen ---
    private static long calculateVolume(BlockPos pos1, BlockPos pos2) {
        int dx = Math.abs(pos1.getX() - pos2.getX()) + 1;
        int dy = Math.abs(pos1.getY() - pos2.getY()) + 1;
        int dz = Math.abs(pos1.getZ() - pos2.getZ()) + 1;
        return (long) dx * dy * dz;
    }

    @Nullable
    public static BlockPos getStartPos(ItemStack stack) {
        return stack.get(ModItems.START_POS);
    }

    @Nullable
    public static BlockPos getEndPos(ItemStack stack) {
        return stack.get(ModItems.END_POS);
    }

    /**
     * Comportamiento Click Izquierdo (Limpiar selección)
     * Retorna false para evitar que el jugador rompa el bloque al golpear.
     */
    @Override
    public boolean canAttackBlock(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer) {
        if (!pLevel.isClientSide) {
            ItemStack stack = pPlayer.getMainHandItem();

            // Solo borramos y mandamos mensaje si realmente había algo guardado
            if (getStartPos(stack) != null) {
                clearSelection(stack);
                pPlayer.displayClientMessage(Component.translatable(ModKeys.GUI_SITE_PLANNER_CLEARED), true);
            }
        }
        return false;
    }

    // --- Métodos Helper para Data Components ---

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            BlockPos clickedPos = context.getClickedPos();
            Player player = context.getPlayer();

            BlockPos start = getStartPos(stack);
            BlockPos end = getEndPos(stack);

            if (start == null) {
                // 1. Marcar Inicio
                setStartPos(stack, clickedPos);
                if (player != null)
                    player.displayClientMessage(Component.translatable(ModKeys.GUI_SITE_PLANNER_START_SET), true);
            } else if (end == null) {
                // 2. Marcar Final

                // Calculamos el volumen antes de guardar
                long volume = calculateVolume(start, clickedPos);

                if (volume < 8) {
                    // ERROR: Volumen insuficiente
                    if (player != null) {
                        player.displayClientMessage(
                                Component.translatable(ModKeys.GUI_SITE_PLANNER_ERROR_VOLUME, volume), true
                        );
                    }
                    // Retornamos FAIL para indicar que no se hizo la acción, o SUCCESS para consumir el click pero no hacer nada
                    return InteractionResult.FAIL;
                }

                setEndPos(stack, clickedPos);
                if (player != null) {
                    player.displayClientMessage(
                            Component.translatable(ModKeys.GUI_SITE_PLANNER_END_SET, volume), true
                    );
                }
            } else {
                // 3. Reiniciar
                setStartPos(stack, clickedPos);
                removeEndPos(stack);
                if (player != null)
                    player.displayClientMessage(Component.translatable(ModKeys.GUI_SITE_PLANNER_NEW_START_SET), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Tooltip Dinámico: Muestra coordenadas y volumen con colores.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        BlockPos start = getStartPos(stack);
        BlockPos end = getEndPos(stack);

        if (start == null) {
            // CASO 1: No hay nada seleccionado (Gris)
            tooltipComponents.add(Component.translatable(ModKeys.TOOLTIP_SITE_PLANNER_NO_SELECTION)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            // CASO 2: Hay inicio seleccionado (Verde)
            tooltipComponents.add(Component.translatable(ModKeys.TOOLTIP_SITE_PLANNER_START,
                            start.getX(), start.getY(), start.getZ())
                    .withStyle(ChatFormatting.GREEN));

            if (end == null) {
                // CASO 2b: Falta el final (Amarillo claro / Pista)
                tooltipComponents.add(Component.translatable(ModKeys.TOOLTIP_SITE_PLANNER_INCOMPLETE)
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                // CASO 3: Selección completa

                // End Position (Azul Aqua)
                tooltipComponents.add(Component.translatable(ModKeys.TOOLTIP_SITE_PLANNER_END,
                                end.getX(), end.getY(), end.getZ())
                        .withStyle(ChatFormatting.AQUA));

                // Separador o espacio visual (opcional)

                // Volumen (Oro)
                long vol = calculateVolume(start, end);
                tooltipComponents.add(Component.translatable(ModKeys.TOOLTIP_SITE_PLANNER_VOLUME, vol)
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
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

    private void setStartPos(ItemStack stack, BlockPos pos) {
        stack.set(ModItems.START_POS, pos);
    }

    private void setEndPos(ItemStack stack, BlockPos pos) {
        stack.set(ModItems.END_POS, pos);
    }

    private void removeEndPos(ItemStack stack) {
        stack.remove(ModItems.END_POS);
    }

    private void clearSelection(ItemStack stack) {
        stack.remove(ModItems.START_POS);
        stack.remove(ModItems.END_POS);
    }
}