package com.tonten.tonten;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Tonten.MODID, value = Dist.CLIENT)
public final class TontenClient {
    private TontenClient() {
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !isControlDown(minecraft)) {
            return;
        }

        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof TonkachiItem)) {
            return;
        }

        int direction = event.getScrollDeltaY() > 0.0D ? 1 : -1;
        ClientPacketDistributor.sendToServer(new TontenNetwork.CycleModePayload(direction));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof TonkachiItem)) {
            return;
        }
        BlockHitResult blockHit = minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        var previewBlocks = TonkachiItem.getClientPreview(
                minecraft.level,
                minecraft.player,
                stack,
                blockHit == null ? null : blockHit.getBlockPos(),
                blockHit == null ? null : blockHit.getDirection());
        if (previewBlocks.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lines());
        for (TonkachiItem.PreviewBlock preview : previewBlocks) {
            AABB box = new AABB(preview.pos()).inflate(0.002D).move(-camera.x, -camera.y, -camera.z);
            int color = preview.anchor() ? 0xFFFFFFFF : preview.placeable() ? 0x99F2D940 : 0xFFFF4040;
            ShapeRenderer.renderShape(poseStack, consumer, Shapes.create(box), 0, 0, 0, color, 1.0F);
        }
        bufferSource.endBatch(RenderTypes.lines());
    }

    private static boolean isControlDown(Minecraft minecraft) {
        long window = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
