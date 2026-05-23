package com.tonten.tonten;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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
        PacketDistributor.sendToServer(new TontenNetwork.CycleModePayload(direction));
        event.setCanceled(true);
    }

    private static boolean isControlDown(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
