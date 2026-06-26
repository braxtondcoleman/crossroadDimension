package com.example.examplemod.client.render;

import com.example.examplemod.portal.CrossroadCrystalBlockEntity;
import com.geckolib.renderer.GeoBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

public class CrossroadCrystalRenderer extends GeoBlockRenderer<CrossroadCrystalBlockEntity, CrossroadCrystalRenderState> {
    public CrossroadCrystalRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new CrossroadCrystalModel());
        withScale(1.35F);
    }

    @Override
    public int getRenderColor(CrossroadCrystalBlockEntity animatable, Void relatedObject, float partialTick) {
        int alpha = Math.round(animatable.renderAlpha(partialTick) * 255.0F);
        return alpha << 24 | 0xFFFFFF;
    }

    @Override
    public RenderType getRenderType(CrossroadCrystalRenderState renderState, Identifier texture) {
        return RenderTypes.entityTranslucent(texture, true);
    }

    @Override
    public CrossroadCrystalRenderState createRenderState() {
        return new CrossroadCrystalRenderState();
    }
}
