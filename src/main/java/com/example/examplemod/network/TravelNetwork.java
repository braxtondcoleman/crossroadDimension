package com.example.examplemod.network;

import com.example.examplemod.CrossroadDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TravelNetwork {
    private static final String NETWORK_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION)
                .playToServer(TravelConfirmPayload.TYPE, TravelConfirmPayload.STREAM_CODEC, TravelNetwork::handleTravelConfirmed);
    }

    private static void handleTravelConfirmed(TravelConfirmPayload payload, IPayloadContext context) {
        Player player = context.player();

        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos pos = serverPlayer.blockPosition();
            CrossroadDimension.LOGGER.info(
                    "Pocket dimension travel requested by {} from {} at {}, {}, {}",
                    serverPlayer.getGameProfile().name(),
                    serverPlayer.level().dimension().identifier(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
            );
            serverPlayer.sendSystemMessage(Component.literal("Realm travel request received."));
        }
    }
}
