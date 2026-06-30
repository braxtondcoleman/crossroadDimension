package com.example.examplemod.client.render;

import com.example.examplemod.item.SurveyScopeItem;
import com.geckolib.renderer.GeoItemRenderer;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class WispJarRenderer extends GeoItemRenderer<SurveyScopeItem> {
    public WispJarRenderer() {
        super(new WispJarModel());
        useAlternateGuiLighting();
        withRenderLayer(new WispSpiritLayer(this));
    }

    @Override
    public RenderType getRenderType(com.geckolib.renderer.base.GeoRenderState renderState, Identifier texture) {
        return RenderTypes.entityTranslucent(texture, true);
    }
}
