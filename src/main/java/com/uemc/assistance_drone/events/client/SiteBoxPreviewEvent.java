package com.uemc.assistance_drone.events.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.items.SitePlanner;
import com.uemc.assistance_drone.util.ModKeys;
import com.uemc.assistance_drone.util.SiteSelectionValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AssistanceDrone.MODID, value = Dist.CLIENT)
public class SiteBoxPreviewEvent {

    /* ------------------------------------------------------------ */
    /* Marker Models                                                */
    /* ------------------------------------------------------------ */

    private static final ModelResourceLocation START_MODEL =
            ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(
                            AssistanceDrone.MODID,
                            ModKeys.SITE_PLANNER_START_MARKER_MODEL_PATH
                    )
            );

    private static final ModelResourceLocation END_MODEL =
            ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(
                            AssistanceDrone.MODID,
                            ModKeys.SITE_PLANNER_END_MARKER_MODEL_PATH
                    )
            );

    /* ------------------------------------------------------------ */
    /* Marker Render Type                                           */
    /* ------------------------------------------------------------ */

    private static final RenderType MARKER_RENDER_TYPE = RenderType.create(
            "marker_no_cull",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2_097_152,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            InventoryMenu.BLOCK_ATLAS, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true)
    );

    /* ------------------------------------------------------------ */
    /* Render Event                                                 */
    /* ------------------------------------------------------------ */

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ModItems.SITE_PLANNER.get())) return;

        PoseStack poseStack = event.getPoseStack();

        BlockPos start = SitePlanner.getStartPos(mainHand);
        BlockPos end = SitePlanner.getEndPos(mainHand);

        // Preview end position from crosshair when only start is defined
        if (start != null && end == null) {
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult bhr) {
                end = bhr.getBlockPos();
            }
        }

        if (start != null) {
            renderMarker(mc, poseStack, start, end, START_MODEL, false);
        }
        if (end != null) {
            renderMarker(mc, poseStack, end, start, END_MODEL, true);
        }

        if (start != null && end != null) {
            renderValidatedBox(poseStack, start, end);
        }
    }

    /* ------------------------------------------------------------ */
    /* Selection Box                                                */
    /* ------------------------------------------------------------ */

    private static void renderValidatedBox(PoseStack poseStack, BlockPos start, BlockPos end) {
        AABB box = new AABB(start).minmax(new AABB(end));

        SiteSelectionValidator.SelectionDimensions dims =
                SiteSelectionValidator.calculateDimensions(start, end);

        boolean validSize = SiteSelectionValidator.isValidSize(dims);
        boolean validVolume = SiteSelectionValidator.isValidVolume(dims);

        float r, g, b;
        if (!validSize) {
            r = 0.8F; g = 0.0F; b = 0.0F;   // Too large
        } else if (!validVolume) {
            r = 1.0F; g = 0.5F; b = 0.0F;   // Too small
        } else {
            r = 0.5F; g = 1.0F; b = 0.5F;   // Valid
        }

        renderSelectionBox(poseStack, box, r, g, b);
    }

    /* ------------------------------------------------------------ */
    /* Marker Rendering                                             */
    /* ------------------------------------------------------------ */

    private static void renderMarker(
            Minecraft mc,
            PoseStack poseStack,
            BlockPos currentPos,
            BlockPos targetPos,
            ModelResourceLocation modelLoc,
            boolean isEndMarker
    ) {
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        float defaultDir = isEndMarker ? -1.0F : 1.0F;
        float sx = defaultDir, sy = defaultDir, sz = defaultDir;

        if (targetPos != null) {
            sx = targetPos.getX() != currentPos.getX()
                    ? Math.signum(targetPos.getX() - currentPos.getX()) : defaultDir;
            sy = targetPos.getY() != currentPos.getY()
                    ? Math.signum(targetPos.getY() - currentPos.getY()) : defaultDir;
            sz = targetPos.getZ() != currentPos.getZ()
                    ? Math.signum(targetPos.getZ() - currentPos.getZ()) : defaultDir;
        }

        poseStack.pushPose();
        poseStack.translate(
                currentPos.getX() - camPos.x,
                currentPos.getY() - camPos.y,
                currentPos.getZ() - camPos.z
        );

        // Align marker to block corner
        poseStack.translate(sx < 0 ? 1 : 0, sy < 0 ? 1 : 0, sz < 0 ? 1 : 0);

        // Slight scale to avoid Z-fighting
        float s = 1.002F;
        poseStack.translate(-(s - 1) / 2 * sx, -(s - 1) / 2 * sy, -(s - 1) / 2 * sz);
        poseStack.scale(sx * s, sy * s, sz * s);

        BakedModel model = mc.getModelManager().getModel(modelLoc);
        VertexConsumer buffer =
                mc.renderBuffers().bufferSource().getBuffer(MARKER_RENDER_TYPE);

        int light = calculateBestLight(mc.level, currentPos);

        mc.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                buffer,
                null,
                model,
                1.0F, 1.0F, 1.0F,
                light,
                OverlayTexture.NO_OVERLAY,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                MARKER_RENDER_TYPE
        );

        poseStack.popPose();
    }

    /* ------------------------------------------------------------ */
    /* Lighting                                                     */
    /* ------------------------------------------------------------ */

    private static int calculateBestLight(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level == null) return 0x00F000F0;

        int light = LevelRenderer.getLightColor(level, pos);

        // If inside solid block, sample neighbors
        if (level.getBlockState(pos).isSolidRender(level, pos)) {
            int best = light;
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (!level.getBlockState(neighbor).isSolidRender(level, neighbor)) {
                    best = Math.max(best, LevelRenderer.getLightColor(level, neighbor));
                }
            }
            light = best;
        }

        return clampLight(light);
    }

    private static int clampLight(int packedLight) {
        int block = (packedLight >> 4) & 0xF;
        int sky = (packedLight >> 20) & 0xF;

        block = Math.max(1, Math.min(14, block));
        sky = Math.max(1, Math.min(14, sky));

        return (block << 4) | (sky << 20);
    }

    /* ------------------------------------------------------------ */
    /* Box Rendering                                                */
    /* ------------------------------------------------------------ */

    private static void renderSelectionBox(
            PoseStack poseStack,
            AABB box,
            float r, float g, float b
    ) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        VertexConsumer buffer =
                mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        LevelRenderer.renderLineBox(
                poseStack,
                buffer,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                r, g, b, 0.7F
        );

        poseStack.popPose();
    }
}
