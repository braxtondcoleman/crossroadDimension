package com.example.examplemod.network;

import com.example.examplemod.CrossroadDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CrystalMenuActionPayload(BlockPos anchorPos, String action) implements CustomPacketPayload {
    public static final Type<CrystalMenuActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "crystal_menu_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CrystalMenuActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.anchorPos());
                buffer.writeUtf(payload.action());
            },
            buffer -> new CrystalMenuActionPayload(buffer.readBlockPos(), buffer.readUtf())
    );

    @Override
    public Type<CrystalMenuActionPayload> type() {
        return TYPE;
    }
}
