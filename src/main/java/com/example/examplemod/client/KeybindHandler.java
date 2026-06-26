package com.example.examplemod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.example.examplemod.CrossroadDimension;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
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
        PlacementMode.tick(mc, OPEN_CROSSROADS.isDown());
        HudNotificationOverlay.tick();
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }

        if (PlacementMode.tryPlace(Minecraft.getInstance())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
