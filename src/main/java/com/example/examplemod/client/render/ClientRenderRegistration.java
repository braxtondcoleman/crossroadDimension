package com.example.examplemod.client.render;

import com.example.examplemod.CrossroadDimension;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ClientRenderRegistration {
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CrossroadDimension.CROSSROADS_GATE_BLOCK_ENTITY.get(), CrossroadCrystalRenderer::new);
    }
}
