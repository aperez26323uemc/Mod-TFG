package com.uemc.assistance_drone.entities.drone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class DroneEntityRenderer extends EntityRenderer<DroneEntity> {
    private static final ResourceLocation DRONE_TEXTURE = ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "textures/entity/drone.png");
    private final DroneEntityModel<DroneEntity> model;

    public DroneEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
        this.model = new DroneEntityModel<>(context.bakeLayer(DroneEntityModel.LAYER_LOCATION));
    }

    @Override
    public void render(@NotNull DroneEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        this.model.setupAnim(entity, 0, 0, partialTicks, entity.getYRot(), entity.getXRot());

        // Prepara el VertexConsumer y dibuja
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull DroneEntity entity) {
        return DRONE_TEXTURE;
    }
}
