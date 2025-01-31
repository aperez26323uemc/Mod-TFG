package com.uemc.assistance_drone.entities.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class DroneEntityModel<T extends DroneEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "droneentity"), "main");
    private final ModelPart Body;
    private final ModelPart Head;
    private final ModelPart LongAxis;
    private final ModelPart FrontBlade;
    private final ModelPart BackBlade;
    private final ModelPart WideAxis;
    private final ModelPart Back;
    private final ModelPart BRightBlade;
    private final ModelPart BLeftBlade;
    private final ModelPart Front;
    private final ModelPart FRightBlade;
    private final ModelPart FLeftBlade;

    public DroneEntityModel(ModelPart root) {
        this.Body = root.getChild("Body");
        this.Head = this.Body.getChild("Head");
        this.LongAxis = this.Body.getChild("LongAxis");
        this.FrontBlade = this.LongAxis.getChild("FrontBlade");
        this.BackBlade = this.LongAxis.getChild("BackBlade");
        this.WideAxis = this.Body.getChild("WideAxis");
        this.Back = this.WideAxis.getChild("Back");
        this.BRightBlade = this.Back.getChild("BRightBlade");
        this.BLeftBlade = this.Back.getChild("BLeftBlade");
        this.Front = this.WideAxis.getChild("Front");
        this.FRightBlade = this.Front.getChild("FRightBlade");
        this.FLeftBlade = this.Front.getChild("FLeftBlade");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Body = partdefinition.addOrReplaceChild("Body", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        PartDefinition Head = Body.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0).addBox(-9.0F, 0.0F, -1.0F, 10.0F, 7.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(13, 20).addBox(-10.0F, 1.0F, 0.0F, 1.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(13, 20).addBox(1.0F, 1.0F, 0.0F, 1.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 19).addBox(-8.0F, 1.0F, 7.0F, 8.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 19).addBox(-8.0F, 1.0F, -2.0F, 8.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 0.0F, -3.0F));

        PartDefinition LongAxis = Body.addOrReplaceChild("LongAxis", CubeListBuilder.create().texOffs(0, 15).addBox(-11.0F, 6.0F, 2.0F, 14.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 3).addBox(3.0F, 6.0F, 2.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 3).addBox(-13.0F, 6.0F, 2.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 0.0F, -3.0F));

        PartDefinition FrontBlade = LongAxis.addOrReplaceChild("FrontBlade", CubeListBuilder.create().texOffs(29, 6).addBox(-1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-12.0F, 9.0F, 3.0F));

        PartDefinition cube_r1 = FrontBlade.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(28, 5).addBox(0.0F, 0.0F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, -1.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition BackBlade = LongAxis.addOrReplaceChild("BackBlade", CubeListBuilder.create().texOffs(29, 6).addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 9.0F, 3.0F));

        PartDefinition cube_r2 = BackBlade.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(28, 5).addBox(0.0F, 0.0F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

        PartDefinition WideAxis = Body.addOrReplaceChild("WideAxis", CubeListBuilder.create(), PartPose.offset(-2.0F, 4.0F, 6.0F));

        PartDefinition Back = WideAxis.addOrReplaceChild("Back", CubeListBuilder.create().texOffs(0, 3).addBox(3.0F, 0.0F, -15.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 3).addBox(3.0F, 0.0F, 1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r3 = Back.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 15).addBox(-13.0F, 0.0F, -1.0F, 14.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition BRightBlade = Back.addOrReplaceChild("BRightBlade", CubeListBuilder.create().texOffs(29, 6).addBox(-1.0F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(28, 5).addBox(0.0F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 3.5F, 2.0F));

        PartDefinition BLeftBlade = Back.addOrReplaceChild("BLeftBlade", CubeListBuilder.create().texOffs(29, 6).addBox(-1.0F, 0.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(28, 5).addBox(0.0F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 3.0F, -14.0F));

        PartDefinition Front = WideAxis.addOrReplaceChild("Front", CubeListBuilder.create().texOffs(0, 3).addBox(-1.0F, 0.0F, -15.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 3).addBox(-1.0F, 0.0F, 1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r4 = Front.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 15).addBox(-13.0F, 0.0F, -1.0F, 14.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition FRightBlade = Front.addOrReplaceChild("FRightBlade", CubeListBuilder.create().texOffs(29, 6).addBox(-1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(28, 5).addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, 2.0F));

        PartDefinition FLeftBlade = Front.addOrReplaceChild("FLeftBlade", CubeListBuilder.create().texOffs(29, 6).addBox(-1.0F, 0.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(28, 5).addBox(0.0F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, -14.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void renderToBuffer(PoseStack pPoseStack, VertexConsumer pBuffer, int pPackedLight, int pPackedOverlay, int pColor) {
        Body.render(pPoseStack, pBuffer, pPackedLight, pPackedOverlay, pColor);
    }

    @Override
    public ModelPart root() {
        return Body;
    }

    /**
     * Sets this entity's model rotation angles
     *
     * @param pEntity
     * @param pLimbSwing
     * @param pLimbSwingAmount
     * @param pAgeInTicks
     * @param pNetHeadYaw
     * @param pHeadPitch
     */
    @Override
    public void setupAnim(DroneEntity pEntity, float pLimbSwing, float pLimbSwingAmount, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        // Update rotation
        this.Body.yRot = -pEntity.getYRot() * ((float)Math.PI / 180.0F);
        this.Head.zRot = Mth.clamp(pEntity.getXRot(), -1F, 1F) * ((float)Math.PI / 180.0F);


        this.animate(pEntity.bladeAnimation, DroneEntityAnimations.DRONE_MOTOR_LOOP, pEntity.tickCount, 0.75f);
    }

}