package com.example.examplemod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import com.example.examplemod.client.HudNotificationOverlay;
import com.example.examplemod.client.KeybindHandler;
import com.example.examplemod.client.PlacementMode;
import com.example.examplemod.client.WispBreadcrumbParticle;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = CrossroadDimension.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = CrossroadDimension.MODID, value = Dist.CLIENT)
public class CrossroadDimensionClient {
    public CrossroadDimensionClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeybindHandler.OPEN_CROSSROADS);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CROSSHAIR,
                Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "placement_mode"),
                (graphics, deltaTracker) -> {
                    PlacementMode.render(graphics);
                    HudNotificationOverlay.render(graphics);
                }
        );
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CrossroadDimension.WISP_BREADCRUMB_PARTICLE.get(), WispBreadcrumbParticle.Provider::new);
    }
}
