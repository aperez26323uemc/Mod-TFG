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

/**
 * Item used to define a rectangular site selection in the world.
 * <p>
 * The selection is defined by a start and end position stored directly
 * in the item stack data.
 */
public class SitePlanner extends Item {

    public static final String ID = ModKeys.SITE_PLANNER_ITEM_KEY;

    public SitePlanner(Properties properties) {
        super(properties.stacksTo(1));
    }

    /* ------------------------------------------------------------ */
    /* Selection Accessors                                          */
    /* ------------------------------------------------------------ */

    @Nullable
    public static BlockPos getStartPos(ItemStack stack) {
        return stack.get(SiteMarkersRegister.START_POS);
    }

    @Nullable
    public static BlockPos getEndPos(ItemStack stack) {
        return stack.get(SiteMarkersRegister.END_POS);
    }

    /**
     * Returns whether the item has a complete and valid selection.
     */
    public static boolean isConfigured(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof SitePlanner
                && getStartPos(stack) != null
                && getEndPos(stack) != null;
    }

    /* ------------------------------------------------------------ */
    /* Interaction                                                  */
    /* ------------------------------------------------------------ */

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        // Left-click does nothing
        return false;
    }

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
        boolean sneaking = player.isShiftKeyDown();

        // Sneak + right click clears any existing selection
        if (sneaking && start != null) {
            clearSelection(stack);
            player.displayClientMessage(
                    Component.translatable(ModKeys.GUI_SITE_PLANNER_CLEARED),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        if (sneaking) {
            return InteractionResult.PASS;
        }

        // No start set: define start position
        if (start == null) {
            setStartPos(stack, clickedPos);
            player.displayClientMessage(
                    Component.translatable(ModKeys.GUI_SITE_PLANNER_START_SET),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        // Start set, end missing: attempt to complete selection
        if (end == null) {
            SiteSelectionValidator.ValidationResult validation =
                    SiteSelectionValidator.validateSelection(start, clickedPos);

            if (!validation.valid) {
                player.displayClientMessage(validation.errorMessage, true);
                return InteractionResult.FAIL;
            }

            setEndPos(stack, clickedPos);
            player.displayClientMessage(
                    Component.translatable(
                            ModKeys.GUI_SITE_PLANNER_END_SET,
                            validation.dimensions.volume()
                    ),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        // Selection already complete
        return InteractionResult.PASS;
    }

    /* ------------------------------------------------------------ */
    /* Tooltip                                                      */
    /* ------------------------------------------------------------ */

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        BlockPos start = getStartPos(stack);
        BlockPos end = getEndPos(stack);

        if (start == null) {
            tooltip.add(Component
                    .translatable(ModKeys.TOOLTIP_SITE_PLANNER_NO_SELECTION)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(
                            ModKeys.TOOLTIP_SITE_PLANNER_START,
                            start.getX(), start.getY(), start.getZ())
                    .withStyle(ChatFormatting.GREEN));

            if (end == null) {
                tooltip.add(Component
                        .translatable(ModKeys.TOOLTIP_SITE_PLANNER_INCOMPLETE)
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                tooltip.add(Component.translatable(
                                ModKeys.TOOLTIP_SITE_PLANNER_END,
                                end.getX(), end.getY(), end.getZ())
                        .withStyle(ChatFormatting.AQUA));

                SiteSelectionValidator.SelectionDimensions dims =
                        SiteSelectionValidator.calculateDimensions(start, end);

                tooltip.add(Component.translatable(
                                ModKeys.TOOLTIP_SITE_PLANNER_VOLUME,
                                dims.volume())
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        super.appendHoverText(stack, context, tooltip, flag);
    }

    /* ------------------------------------------------------------ */
    /* Internal Helpers                                             */
    /* ------------------------------------------------------------ */

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
