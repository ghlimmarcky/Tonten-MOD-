package com.tonten.tonten;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TontenNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(Tonten.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private TontenNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(CycleModePayload.class, 0)
                .encoder(CycleModePayload::encode)
                .decoder(CycleModePayload::decode)
                .consumerMainThread((payload, contextSupplier) -> {
                    var context = contextSupplier.get();
                    context.enqueueWork(() -> handleCycleMode(payload, context.getSender()));
                    context.setPacketHandled(true);
                })
                .add();
    }

    private static void handleCycleMode(CycleModePayload payload, net.minecraft.server.level.ServerPlayer player) {
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof TonkachiItem tonkachi) {
            tonkachi.cycleMode(stack, player, payload.direction());
        }
    }

    public record CycleModePayload(int direction) {
        public static void encode(CycleModePayload payload, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeInt(payload.direction());
        }

        public static CycleModePayload decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new CycleModePayload(buffer.readInt());
        }
    }
}
