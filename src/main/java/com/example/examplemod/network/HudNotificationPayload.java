package com.example.examplemod.network;

import com.example.examplemod.CrossroadDimension;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HudNotificationPayload(String message) implements CustomPacketPayload {
    public static final Type<HudNotificationPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "hud_notification"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HudNotificationPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.message()),
            buffer -> new HudNotificationPayload(buffer.readUtf())
    );

    @Override
    public Type<HudNotificationPayload> type() {
        return TYPE;
    }
}
