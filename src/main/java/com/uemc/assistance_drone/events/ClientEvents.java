package com.uemc.assistance_drone.events;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.entities.client.DroneFlyingSound;
import com.uemc.assistance_drone.entities.drone.DroneEntity;
import com.uemc.assistance_drone.items.SitePlanner;
import com.uemc.assistance_drone.items.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = AssistanceDrone.MODID, value = Dist.CLIENT)
public class ClientEvents {

    // CAMBIO: Usamos ModelResourceLocation con la variante "standalone" para coincidir con el registro
    private static final ModelResourceLocation START_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "block/bottom_marker"));
    private static final ModelResourceLocation END_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(AssistanceDrone.MODID, "block/top_marker"));

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
                    .setCullState(RenderStateShard.NO_CULL) // <--- LA CLAVE: Desactivar ocultación de caras traseras
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

        if (start != null && end == null) {
            HitResult rayTrace = mc.hitResult;
            if (rayTrace != null && rayTrace.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) rayTrace;
                end = blockHit.getBlockPos(); // Sobrescribimos 'end' temporalmente con lo que miras
            }
        }

        // Marcador INICIO: Le decimos 'false' (no es el final), su default será +1
        if (start != null) {
            renderStandaloneModel(mc, poseStack, start, end, START_MODEL, false);
        }

        // Marcador FINAL: Le decimos 'true' (es el final), su default será -1
        if (end != null) {
            renderStandaloneModel(mc, poseStack, end, start, END_MODEL, true);
        }

        // --- CAJA DE SELECCIÓN CON VALIDACIÓN DE COLOR ---
        if (start != null && end != null) {
            AABB box = new AABB(start).minmax(new AABB(end));

            // Calculamos volumen usando la fórmula matemática exacta
            long volume = calculateVolume(start, end);
            boolean isValid = volume >= 8;

            // Si es inválido -> ROJO (1, 0, 0)
            float r = isValid ? 0.5F : 1.0F;
            float g = isValid ? 1.0F : 0.0F;
            float b = isValid ? 0.5F : 0.0F;

            renderSelectionBox(poseStack, box, r, g, b);
        }
    }

    private static long calculateVolume(BlockPos pos1, BlockPos pos2) {
        int dx = Math.abs(pos1.getX() - pos2.getX()) + 1;
        int dy = Math.abs(pos1.getY() - pos2.getY()) + 1;
        int dz = Math.abs(pos1.getZ() - pos2.getZ()) + 1;
        return (long) dx * dy * dz;
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof DroneEntity drone && event.getLevel().isClientSide()) {
            Minecraft.getInstance().getSoundManager().play(new DroneFlyingSound(drone));
        }
    }

    // --- MÉTODOS PRIVADOS ---

    private static void renderStandaloneModel(Minecraft mc,
                                              PoseStack poseStack,
                                              BlockPos currentPos,
                                              BlockPos targetPos,
                                              ModelResourceLocation modelLoc,
                                              boolean isEndMarker) {
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        // 1. Calcular distancias crudas
        double dx = (targetPos != null) ? targetPos.getX() - currentPos.getX() : 0;
        double dy = (targetPos != null) ? targetPos.getY() - currentPos.getY() : 0;
        double dz = (targetPos != null) ? targetPos.getZ() - currentPos.getZ() : 0;

        // 2. Lógica de Signos (Orientación)
        // Si dx no es 0, usamos la dirección real.
        // Si dx ES 0 (están en línea), usamos el desempate:
        //    - Inicio: +1
        //    - Fin:    -1
        // Esto garantiza que siempre se miren el uno al otro.

        float defaultDir = isEndMarker ? -1.0f : 1.0f;

        float sx = (dx != 0) ? (float) Math.signum(dx) : defaultDir;
        float sy = (dy != 0) ? (float) Math.signum(dy) : defaultDir;
        float sz = (dz != 0) ? (float) Math.signum(dz) : defaultDir;

        poseStack.pushPose();
        poseStack.translate(currentPos.getX() - camPos.x, currentPos.getY() - camPos.y, currentPos.getZ() - camPos.z);

        // 3. Compensación de posición
        // Si el eje es negativo (-1), el modelo se dibuja en el bloque anterior,
        // así que lo empujamos +1 para que vuelva a su sitio.
        poseStack.translate(sx < 0 ? 1 : 0, sy < 0 ? 1 : 0, sz < 0 ? 1 : 0);

        // Escala con inversión (Espejo)
        float s = 1.002f;
        poseStack.translate(-(s - 1) / 2 * sx, -(s - 1) / 2 * sy, -(s - 1) / 2 * sz);
        poseStack.scale(sx * s, sy * s, sz * s);

        BakedModel model = mc.getModelManager().getModel(modelLoc);

        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(MARKER_RENDER_TYPE);

        int light;
        if (mc.level != null) {
            light = calculateBestLight(mc.level, currentPos);
        } else {
            light = 15728880;
        }

        mc.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffer, null, model,
                1.0F, 1.0F, 1.0F,
                light, OverlayTexture.NO_OVERLAY, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, MARKER_RENDER_TYPE
        );
        poseStack.popPose();
    }

    private static int calculateBestLight(net.minecraft.world.level.Level level, BlockPos pos) {
        int currentLight = LevelRenderer.getLightColor(level, pos);

        // Si el bloque actual es sólido (luz 0), buscamos en los vecinos
        if (level.getBlockState(pos).isSolidRender(level, pos)) {
            int bestLight = 0;
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (!level.getBlockState(neighbor).isSolidRender(level, neighbor)) {
                    int l = LevelRenderer.getLightColor(level, neighbor);
                    if (l > bestLight) bestLight = l;
                }
            }
            // Si encontramos luz mejor en los vecinos, usamos esa
            if (bestLight > currentLight) currentLight = bestLight;
        }

        // APLICAMOS TU REGLA DE MÁXIMOS Y MÍNIMOS
        return clampLight(currentLight);
    }

    private static int clampLight(int packedLight) {
        // Desempaquetamos los valores (Minecraft guarda la luz en bits)
        // Formato: SkyLight << 20 | BlockLight << 4
        int blockLight = (packedLight >> 4) & 0xF;
        int skyLight = (packedLight >> 20) & 0xF;

        // Regla: Mínimo 1, Máximo 14
        blockLight = Math.max(1, Math.min(14, blockLight));
        skyLight = Math.max(1, Math.min(14, skyLight));

        // Volvemos a empaquetar
        return (blockLight << 4) | (skyLight << 20);
    }

    private static void renderSelectionBox(PoseStack poseStack, AABB box, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        VertexConsumer builder = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        LevelRenderer.renderLineBox(poseStack, builder,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                r, g, b, 0.7F); // Alpha 0.5 siempre

        poseStack.popPose();
    }
}