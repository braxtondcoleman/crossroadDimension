package com.example.examplemod.client.render;

import com.example.examplemod.portal.CrossroadCrystalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.geckolib.renderer.GeoBlockRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

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
        return RenderTypes.entityTranslucent(texture);
    }

    @Override
    public CrossroadCrystalRenderState createRenderState() {
        return new CrossroadCrystalRenderState();
    }

    @Override
    public void extractRenderState(CrossroadCrystalBlockEntity animatable, CrossroadCrystalRenderState renderState, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        super.extractRenderState(animatable, renderState, partialTick, cameraPos, crumblingOverlay);
        renderState.portalScale = animatable.portalScale(partialTick);
        renderState.portalAlpha = animatable.portalAlpha(partialTick);
        renderState.portalScroll = animatable.portalScroll(partialTick);
        renderState.portalSpin = animatable.portalSpin(partialTick);
        renderState.portalSwirl = animatable.portalSwirl(partialTick);
    }

    @Override
    public void submit(CrossroadCrystalRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        CrossroadPortalRenderer.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
    }
}
