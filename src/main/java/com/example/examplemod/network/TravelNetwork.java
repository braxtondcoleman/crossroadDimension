package com.example.examplemod.network;

import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.portal.RealmCrystalManager;
import com.example.examplemod.realm.PocketRealmManager;
import com.example.examplemod.realm.PocketRealmService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TravelNetwork {
    private static final String NETWORK_VERSION = "1";
    private static final PocketRealmService POCKET_REALM_SERVICE = new PocketRealmService(new PocketRealmManager());
    private static final RealmCrystalManager CRYSTAL_MANAGER = new RealmCrystalManager();

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION)
                .playToServer(TravelConfirmPayload.TYPE, TravelConfirmPayload.STREAM_CODEC, TravelNetwork::handleTravelConfirmed)
                .playToServer(CrystalMenuActionPayload.TYPE, CrystalMenuActionPayload.STREAM_CODEC, TravelNetwork::handleCrystalMenuAction)
                .playToClient(OpenCrystalMenuPayload.TYPE, OpenCrystalMenuPayload.STREAM_CODEC, TravelNetwork::handleOpenCrystalMenu)
                .playToClient(HudNotificationPayload.TYPE, HudNotificationPayload.STREAM_CODEC, TravelNetwork::handleHudNotification);
    }

    private static void handleTravelConfirmed(TravelConfirmPayload payload, IPayloadContext context) {
        Player player = context.player();

        if (player instanceof ServerPlayer serverPlayer) {
            context.enqueueWork(() -> {
                BlockPos pos = serverPlayer.blockPosition();
                CrossroadDimension.LOGGER.info(
                        "Crossroads crystal placement requested by {} from {} at {}, {}, {}",
                        serverPlayer.getGameProfile().name(),
                        serverPlayer.level().dimension().identifier(),
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()
                );
                Component resultMessage = POCKET_REALM_SERVICE.requestCrystalPlacement(serverPlayer);
                CrossroadDimension.LOGGER.info("Crossroads crystal placement completed for {}: {}", serverPlayer.getGameProfile().name(), resultMessage.getString());
                HudNotifications.send(serverPlayer, resultMessage.getString());
            });
        }
    }

    private static void handleCrystalMenuAction(CrystalMenuActionPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player instanceof ServerPlayer serverPlayer) {
            context.enqueueWork(() -> CRYSTAL_MANAGER.handleMenuAction(serverPlayer, payload.anchorPos(), payload.action()));
        }
    }

    private static void handleOpenCrystalMenu(OpenCrystalMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> runClientPayloadHandler("openCrystalMenu", OpenCrystalMenuPayload.class, payload));
    }

    private static void handleHudNotification(HudNotificationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> runClientPayloadHandler("hudNotification", HudNotificationPayload.class, payload));
    }

    private static void runClientPayloadHandler(String methodName, Class<?> payloadType, Object payload) {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }

        try {
            Class.forName("com.example.examplemod.client.ClientNetworkHandlers")
                    .getMethod(methodName, payloadType)
                    .invoke(null, payload);
        } catch (ReflectiveOperationException exception) {
            CrossroadDimension.LOGGER.error("Unable to handle client payload {}", methodName, exception);
        }
    }
}
