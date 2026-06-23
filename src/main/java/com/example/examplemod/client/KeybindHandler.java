package com.example.examplemod.client;

import com.mojang.blaze3d.platform.InputConstants;

import com.example.examplemod.client.gui.HomeConfirmScreen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber
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

        while (OPEN_CROSSROADS.consumeClick()) {

            Minecraft mc = Minecraft.getInstance();

            if (mc.player != null) {
                mc.setScreen(new HomeConfirmScreen());
            }
        }
    }
}