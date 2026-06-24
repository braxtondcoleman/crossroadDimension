package com.example.examplemod.network;

import com.example.examplemod.CrossroadDimension;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TravelConfirmPayload() implements CustomPacketPayload {
    public static final TravelConfirmPayload INSTANCE = new TravelConfirmPayload();
    public static final Type<TravelConfirmPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "travel_confirmed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TravelConfirmPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<TravelConfirmPayload> type() {
        return TYPE;
    }
}
