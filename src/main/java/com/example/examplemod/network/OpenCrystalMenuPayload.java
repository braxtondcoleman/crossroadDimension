package com.example.examplemod.network;

import com.example.examplemod.CrossroadDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenCrystalMenuPayload(
        BlockPos anchorPos,
        boolean owner,
        String ownerName,
        boolean insideRealm
) implements CustomPacketPayload {
    public static final Type<OpenCrystalMenuPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "open_crystal_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCrystalMenuPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.anchorPos());
                buffer.writeBoolean(payload.owner());
                buffer.writeUtf(payload.ownerName());
                buffer.writeBoolean(payload.insideRealm());
            },
            buffer -> new OpenCrystalMenuPayload(
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readUtf(),
                    buffer.readBoolean()
            )
    );

    @Override
    public Type<OpenCrystalMenuPayload> type() {
        return TYPE;
    }
}
