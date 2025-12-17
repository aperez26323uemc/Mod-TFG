package com.uemc.assistance_drone.events;

import com.mojang.blaze3d.vertex.PoseStack;
import com.uemc.assistance_drone.AssistanceDrone;
import com.uemc.assistance_drone.items.BluePrint;
import com.uemc.assistance_drone.items.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;


// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = AssistanceDrone.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ModItems.BluePrint.get())) return;

        PoseStack poseStack = event.getPoseStack();

        BlockPos start = BluePrint.getStartPos(mainHand);
        BlockPos end = BluePrint.getEndPos(mainHand);

        if (start != null) {
            renderBlockMarker(poseStack, start, Blocks.GOLD_BLOCK.defaultBlockState());
        }
        if (end != null) {
            renderBlockMarker(poseStack, end, Blocks.DIAMOND_BLOCK.defaultBlockState());
        }
    }

    private static void renderBlockMarker(PoseStack poseStack, BlockPos pos, BlockState blockState) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();

        // Obtener la posición de la cámara
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        // Escalar el bloque (1 píxel extra)
        float scaleFactor = 1.125f;
        poseStack.scale(scaleFactor, scaleFactor, scaleFactor);

        // Desplazamiento para centrar el bloque tras escalarlo
        float offset = (scaleFactor - 1) / 2.0f;
        poseStack.translate(-offset, -offset, -offset);

        mc.getBlockRenderer().renderSingleBlock(
                blockState,
                poseStack,
                bufferSource,
                15728880,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                RenderType.cutout()
        );

        poseStack.popPose();
        bufferSource.endBatch();
    }
}
