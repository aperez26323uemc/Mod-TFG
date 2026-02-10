package com.uemc.assistance_drone.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Validador centralizado para selecciones de sitio.
 * Evita duplicación de lógica entre SitePlanner y SiteBoxPreviewEvent.
 */
public class SiteSelectionValidator {

    // Límites de tamaño del site
    public static final int MAX_SIZE = 48;
    public static final int MIN_VOLUME = 8;

    /**
         * Información de dimensiones de una selección.
         */
        public record SelectionDimensions(int dx, int dy, int dz, long volume) {
    }

    /**
     * Resultado de validación de dimensiones.
     */
    public static class ValidationResult {
        public final boolean valid;
        public final Component errorMessage;
        public final SelectionDimensions dimensions;

        private ValidationResult(boolean valid, Component errorMessage, SelectionDimensions dimensions) {
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

    /**
     * Calcula las dimensiones entre dos posiciones.
     */
    public static SelectionDimensions calculateDimensions(BlockPos start, BlockPos end) {
        int dx = Math.abs(start.getX() - end.getX()) + 1;
        int dy = Math.abs(start.getY() - end.getY()) + 1;
        int dz = Math.abs(start.getZ() - end.getZ()) + 1;
        long volume = (long) dx * dy * dz;
        return new SelectionDimensions(dx, dy, dz, volume);
    }

    /**
     * Valida dimensiones y volumen de la selección.
     */
    public static ValidationResult validateSelection(BlockPos start, BlockPos end) {
        SelectionDimensions dims = calculateDimensions(start, end);

        // Validación 1: Tamaño máximo
        if (dims.dx > MAX_SIZE || dims.dy > MAX_SIZE || dims.dz > MAX_SIZE) {
            return ValidationResult.error(
                    Component.translatable(
                            ModKeys.GUI_SITE_PLANNER_ERROR_MAX_SIZE,
                            MAX_SIZE,
                            dims.dx, dims.dy, dims.dz
                    ).withStyle(ChatFormatting.RED),
                    dims
            );
        }

        // Validación 2: Volumen mínimo
        if (dims.volume < MIN_VOLUME) {
            return ValidationResult.error(
                    Component.translatable(
                            ModKeys.GUI_SITE_PLANNER_ERROR_VOLUME_SMALL,
                            MIN_VOLUME, dims.volume
                    ).withStyle(ChatFormatting.RED),
                    dims
            );
        }

        return ValidationResult.success(dims);
    }

    /**
     * Verifica si las dimensiones cumplen el tamaño máximo.
     */
    public static boolean isValidSize(SelectionDimensions dims) {
        return dims.dx <= MAX_SIZE && dims.dy <= MAX_SIZE && dims.dz <= MAX_SIZE;
    }

    /**
     * Verifica si el volumen cumple el mínimo requerido.
     */
    public static boolean isValidVolume(SelectionDimensions dims) {
        return dims.volume >= MIN_VOLUME;
    }
}
