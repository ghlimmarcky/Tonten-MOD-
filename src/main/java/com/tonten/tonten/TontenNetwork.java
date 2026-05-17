package com.tonten.tonten;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class TontenNetwork {
    private TontenNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(CycleModePayload.TYPE, CycleModePayload.STREAM_CODEC, TontenNetwork::handleCycleMode);
    }

    private static void handleCycleMode(CycleModePayload payload, IPayloadContext context) {
        ItemStack stack = context.player().getMainHandItem();
        if (stack.getItem() instanceof TonkachiItem tonkachi) {
            tonkachi.cycleMode(stack, context.player(), payload.direction());
        }
    }

    public record CycleModePayload(int direction) implements CustomPacketPayload {
        public static final Type<CycleModePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Tonten.MODID, "cycle_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CycleModePayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeInt(payload.direction()),
                buffer -> new CycleModePayload(buffer.readInt()));

        @Override
        public Type<CycleModePayload> type() {
            return TYPE;
        }
    }
}
