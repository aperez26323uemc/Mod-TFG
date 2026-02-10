package com.uemc.assistance_drone.events.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.items.SitePlanner;
import com.uemc.assistance_drone.items.ModItems;
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

    // Modelos de marcadores
    private static final ModelResourceLocation START_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, ModKeys.SITE_PLANNER_START_MARKER_MODEL_PATH));
    private static final ModelResourceLocation END_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, ModKeys.SITE_PLANNER_END_MARKER_MODEL_PATH));

    // Render type para marcadores
    private static final RenderType MARKER_RENDER_TYPE = RenderType.create(
            "marker_no_cull",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true)
    );

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

        // Si solo hay inicio, usar el bloque mirado como preview
        if (start != null && end == null) {
            HitResult rayTrace = mc.hitResult;
            if (rayTrace != null && rayTrace.getType() == HitResult.Type.BLOCK) {
                end = ((BlockHitResult) rayTrace).getBlockPos();
            }
        }

        // Renderizar marcadores
        if (start != null) {
            renderMarker(mc, poseStack, start, end, START_MODEL, false);
        }
        if (end != null) {
            renderMarker(mc, poseStack, end, start, END_MODEL, true);
        }

        // Renderizar caja de selección con validación
        if (start != null && end != null) {
            renderValidatedBox(poseStack, start, end);
        }
    }

    // --- Renderizado de Caja Validada ---

    /**
     * Renderiza la caja con color basado en validación.
     */
    private static void renderValidatedBox(PoseStack poseStack, BlockPos start, BlockPos end) {
        AABB box = new AABB(start).minmax(new AABB(end));

        // Usar validador centralizado
        SiteSelectionValidator.SelectionDimensions dims =
                SiteSelectionValidator.calculateDimensions(start, end);

        boolean validSize = SiteSelectionValidator.isValidSize(dims);
        boolean validVolume = SiteSelectionValidator.isValidVolume(dims);

        // Colores según validación
        float r, g, b;
        if (!validSize) {
            // Rojo oscuro - Excede tamaño máximo
            r = 0.8F;
            g = 0.0F;
            b = 0.0F;
        } else if (!validVolume) {
            // Naranja - Volumen insuficiente
            r = 1.0F;
            g = 0.5F;
            b = 0.0F;
        } else {
            // Verde - Válido
            r = 0.5F;
            g = 1.0F;
            b = 0.5F;
        }

        renderSelectionBox(poseStack, box, r, g, b);
    }

    // --- Renderizado de Marcadores ---

    /**
     * Renderiza un marcador 3D en la posición especificada.
     */
    private static void renderMarker(Minecraft mc,
                                     PoseStack poseStack,
                                     BlockPos currentPos,
                                     BlockPos targetPos,
                                     ModelResourceLocation modelLoc,
                                     boolean isEndMarker) {
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        // Calcular orientación
        float sx, sy, sz;
        if (targetPos != null) {
            double dx = targetPos.getX() - currentPos.getX();
            double dy = targetPos.getY() - currentPos.getY();
            double dz = targetPos.getZ() - currentPos.getZ();

            float defaultDir = isEndMarker ? -1.0f : 1.0f;
            sx = dx != 0 ? (float) Math.signum(dx) : defaultDir;
            sy = dy != 0 ? (float) Math.signum(dy) : defaultDir;
            sz = dz != 0 ? (float) Math.signum(dz) : defaultDir;
        } else {
            float defaultDir = isEndMarker ? -1.0f : 1.0f;
            sx = sy = sz = defaultDir;
        }

        poseStack.pushPose();
        poseStack.translate(
                currentPos.getX() - camPos.x,
                currentPos.getY() - camPos.y,
                currentPos.getZ() - camPos.z
        );

        // Compensación de posición
        poseStack.translate(sx < 0 ? 1 : 0, sy < 0 ? 1 : 0, sz < 0 ? 1 : 0);

        // Escala con inversión (espejo)
        float s = 1.002f;
        poseStack.translate(-(s - 1) / 2 * sx, -(s - 1) / 2 * sy, -(s - 1) / 2 * sz);
        poseStack.scale(sx * s, sy * s, sz * s);

        BakedModel model = mc.getModelManager().getModel(modelLoc);
        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(MARKER_RENDER_TYPE);

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

    // --- Utilidades de Luz ---

    /**
     * Calcula la mejor iluminación para un marcador.
     */
    private static int calculateBestLight(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level == null) return 15728880;

        int currentLight = LevelRenderer.getLightColor(level, pos);

        // Si el bloque es sólido, buscar luz en vecinos
        if (level.getBlockState(pos).isSolidRender(level, pos)) {
            int bestLight = 0;
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (!level.getBlockState(neighbor).isSolidRender(level, neighbor)) {
                    int l = LevelRenderer.getLightColor(level, neighbor);
                    if (l > bestLight) bestLight = l;
                }
            }
            if (bestLight > currentLight) currentLight = bestLight;
        }

        return clampLight(currentLight);
    }

    /**
     * Limita los valores de luz entre 1 y 14.
     */
    private static int clampLight(int packedLight) {
        int blockLight = (packedLight >> 4) & 0xF;
        int skyLight = (packedLight >> 20) & 0xF;

        blockLight = Math.max(1, Math.min(14, blockLight));
        skyLight = Math.max(1, Math.min(14, skyLight));

        return (blockLight << 4) | (skyLight << 20);
    }

    // --- Renderizado de Caja ---

    /**
     * Renderiza una caja de selección con el color especificado.
     */
    private static void renderSelectionBox(PoseStack poseStack, AABB box, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        VertexConsumer builder = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        LevelRenderer.renderLineBox(
                poseStack,
                builder,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                r, g, b, 0.7F
        );

        poseStack.popPose();
    }
}