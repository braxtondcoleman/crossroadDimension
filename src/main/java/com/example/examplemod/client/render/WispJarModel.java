package com.example.examplemod.client.render;

import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.item.WispJarItem;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

import net.minecraft.resources.Identifier;

public class WispJarModel extends GeoModel<WispJarItem> {
    private static final Identifier MODEL =
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "wisp_jar");
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "textures/item/wisp_jar.png");
    private static final Identifier ANIMATION =
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "wisp_jar");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(WispJarItem animatable) {
        return ANIMATION;
    }
}
