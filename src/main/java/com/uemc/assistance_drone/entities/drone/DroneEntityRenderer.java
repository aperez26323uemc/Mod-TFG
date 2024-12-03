package com.uemc.assistance_drone.entities.drone;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class DroneEntityRenderer extends EntityRenderer<DroneEntity> {

    public DroneEntityRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull DroneEntity pEntity) {
        return null;
    }
}