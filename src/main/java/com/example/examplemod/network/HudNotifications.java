package com.example.examplemod.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HudNotifications {
    private HudNotifications() {
    }

    public static void send(ServerPlayer player, String message) {
        PacketDistributor.sendToPlayer(player, new HudNotificationPayload(message));
    }
}
