package com.example.examplemod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.client.gui.HomeConfirmScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CrossroadDimension.MODID, value = Dist.CLIENT)
public class KeybindHandler {

    public static final KeyMapping OPEN_CROSSROADS =
        new KeyMapping(
                "key.crossroaddimension.open",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KeyMapping.Category.MISC
        );

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            TravelManager.cancel();
            return;
        }

        while (OPEN_CROSSROADS.consumeClick()) {
            if (mc.screen == null) {
                if (isInsidePocketRealm(mc)) {
                    TravelManager.cancel();
                    mc.player.sendSystemMessage(Component.literal("You are currently inside a Pocket Realm. Use the Crossroads Gate to leave."));
                    continue;
                }
                mc.setScreen(new HomeConfirmScreen());
            }
        }
    }

    private static boolean isInsidePocketRealm(Minecraft mc) {
        Identifier dimensionId = mc.player.level().dimension().identifier();
        return dimensionId.getNamespace().equals(CrossroadDimension.MODID)
                && dimensionId.getPath().startsWith("pocket_realm/");
    }
}
