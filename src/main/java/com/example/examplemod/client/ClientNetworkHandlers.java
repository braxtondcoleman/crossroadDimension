package com.example.examplemod.client;

import com.example.examplemod.network.HudNotificationPayload;
import com.example.examplemod.network.OpenCrystalMenuPayload;
import net.minecraft.client.Minecraft;

public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    public static void openCrystalMenu(OpenCrystalMenuPayload payload) {
        Minecraft.getInstance().setScreen(new CrossroadsCrystalScreen(payload));
    }

    public static void hudNotification(HudNotificationPayload payload) {
        HudNotificationOverlay.push(payload.message());
    }
}
