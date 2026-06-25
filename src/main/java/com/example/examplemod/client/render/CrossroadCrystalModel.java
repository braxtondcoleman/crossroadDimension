package com.example.examplemod.client.render;

import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.portal.CrossroadCrystalBlockEntity;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

public class CrossroadCrystalModel extends GeoModel<CrossroadCrystalBlockEntity> {
    private static final Identifier CRYSTAL = Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "crossroad_crystal");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "textures/block/crossroad_crystal.png");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return CRYSTAL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(CrossroadCrystalBlockEntity animatable) {
        return CRYSTAL;
    }
}
