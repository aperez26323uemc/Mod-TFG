package com.uemc.assistance_drone.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Centralized validator for site selections.
 * <p>
 * Provides shared validation logic for both interaction and rendering code,
 * avoiding duplication across client and server components.
 */
public class SiteSelectionValidator {

    /* ------------------------------------------------------------ */
    /* Limits                                                       */
    /* ------------------------------------------------------------ */

    public static final int MAX_SIZE = 48;
    public static final int MIN_VOLUME = 8;

    /* ------------------------------------------------------------ */
    /* Models                                                       */
    /* ------------------------------------------------------------ */

    public record SelectionDimensions(int dx, int dy, int dz, long volume) {}

    public static class ValidationResult {
        public final boolean valid;
        public final Component errorMessage;
        public final SelectionDimensions dimensions;

        private ValidationResult(
                boolean valid,
                Component errorMessage,
                SelectionDimensions dimensions
        ) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.dimensions = dimensions;
        }

        public static ValidationResult success(SelectionDimensions dimensions) {
            return new ValidationResult(true, null, dimensions);
        }

        public static ValidationResult error(Component message, SelectionDimensions dimensions) {
            return new ValidationResult(false, message, dimensions);
        }
    }

    /* ------------------------------------------------------------ */
    /* Validation                                                   */
    /* ------------------------------------------------------------ */

    public static SelectionDimensions calculateDimensions(BlockPos start, BlockPos end) {
        int dx = Math.abs(start.getX() - end.getX()) + 1;
        int dy = Math.abs(start.getY() - end.getY()) + 1;
        int dz = Math.abs(start.getZ() - end.getZ()) + 1;
        long volume = (long) dx * dy * dz;

        return new SelectionDimensions(dx, dy, dz, volume);
    }

    public static ValidationResult validateSelection(BlockPos start, BlockPos end) {
        SelectionDimensions dims = calculateDimensions(start, end);

        if (!isValidSize(dims)) {
            return ValidationResult.error(
                    Component.translatable(
                            ModKeys.GUI_SITE_PLANNER_ERROR_MAX_SIZE,
                            MAX_SIZE,
                            dims.dx, dims.dy, dims.dz
                    ).withStyle(ChatFormatting.RED),
                    dims
            );
        }

        if (!isValidVolume(dims)) {
            return ValidationResult.error(
                    Component.translatable(
                            ModKeys.GUI_SITE_PLANNER_ERROR_VOLUME_SMALL,
                            MIN_VOLUME,
                            dims.volume
                    ).withStyle(ChatFormatting.RED),
                    dims
            );
        }

        return ValidationResult.success(dims);
    }

    /* ------------------------------------------------------------ */
    /* Helpers                                                      */
    /* ------------------------------------------------------------ */

    public static boolean isValidSize(SelectionDimensions dims) {
        return dims.dx <= MAX_SIZE
                && dims.dy <= MAX_SIZE
                && dims.dz <= MAX_SIZE;
    }

    public static boolean isValidVolume(SelectionDimensions dims) {
        return dims.volume >= MIN_VOLUME;
    }
}
