package com.example.examplemod.client.render;

import java.util.function.BiConsumer;

import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.item.SurveyScopeItem;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;

import org.joml.Quaternionf;

public class WispSpiritLayer
        extends GeoRenderLayer<SurveyScopeItem, GeoItemRenderer.RenderData, GeoRenderState> {
    private static final String SPIRIT_ANCHOR = "SpiritAnchor";
    private static final Identifier ORB_TEXTURE =
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "textures/item/wisp_orb.png");
    private static final Identifier DIAMOND_TEXTURE =
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "textures/item/wisp_diamond.png");

    public WispSpiritLayer(GeoItemRenderer<SurveyScopeItem> renderer) {
        super(renderer);
    }

    @Override
    public void addPerBoneRender(RenderPassInfo<GeoRenderState> renderPassInfo,
            BiConsumer<GeoBone, PerBoneRender<GeoRenderState>> consumer) {
        if (!renderPassInfo.willRender()) {
            return;
        }

        renderPassInfo.model().getBone(SPIRIT_ANCHOR)
                .ifPresentOrElse(
                        bone -> consumer.accept(bone, this::renderSpirit),
                        () -> CrossroadDimension.LOGGER.error(
                                "Unable to find {} bone for Survey Scope rendering", SPIRIT_ANCHOR));
    }

    private void renderSpirit(RenderPassInfo<GeoRenderState> renderPassInfo, GeoBone bone,
            SubmitNodeCollector renderTasks) {
        SurveyScopeItem scope = (SurveyScopeItem) renderPassInfo.renderState()
                .getGeckolibData(GeoItemRenderer.CURRENT_ITEM);
        if (scope == null) {
            return;
        }

        double time = renderTime(renderPassInfo.renderState());

        float diamondSway = Mth.sin((float) time * 0.035F) * 0.007F;
        int diamondColor = color(0.88F, scope.getRed(), scope.getGreen(), scope.getBlue());
        renderBillboard(renderPassInfo, renderTasks, DIAMOND_TEXTURE,
                diamondSway, 0.0F, 0.006F, 0.105F, diamondColor);

        float orbit = (float) time * 0.045F;
        float orbX = Mth.cos(orbit) * 0.042F;
        float orbZ = Mth.sin(orbit) * 0.026F;
        float orbY = 0.025F + Mth.sin((float) time * 0.085F) * 0.035F;
        float orbScale = 0.22F * (1.0F + Mth.sin((float) time * 0.11F) * 0.08F);
        float orbAlpha = 0.63F + Mth.sin((float) time * 0.075F) * 0.12F;
        renderBillboard(renderPassInfo, renderTasks, ORB_TEXTURE,
                orbX, orbY, orbZ, orbScale, color(orbAlpha, 1.0F, 1.0F, 1.0F));
    }

    private static double renderTime(GeoRenderState renderState) {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime() + renderState.getPartialTick();
        }
        return Util.getMillis() / 50.0;
    }

    private static void renderBillboard(RenderPassInfo<GeoRenderState> renderPassInfo,
            SubmitNodeCollector renderTasks, Identifier texture,
            float x, float y, float z, float size, int color) {
        PoseStack poseStack = renderPassInfo.poseStack();
        poseStack.pushPose();
        poseStack.translate(x, y, z);

        Quaternionf inversePoseRotation = poseStack.last().pose()
                .getUnnormalizedRotation(new Quaternionf())
                .normalize()
                .invert();
        poseStack.mulPose(inversePoseRotation);

        ItemDisplayContext context = renderPassInfo.renderState()
                .getOrDefaultGeckolibData(com.geckolib.constant.DataTickets.ITEM_RENDER_PERSPECTIVE,
                        ItemDisplayContext.NONE);
        if (context != ItemDisplayContext.GUI) {
            poseStack.mulPose(renderPassInfo.cameraState().orientation);
        }

        poseStack.scale(size, size, size);
        renderTasks.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(texture, false),
                (pose, buffer) -> {
                    vertex(buffer, pose, -0.5F, -0.5F, 0.0F, color, 0.0F, 1.0F);
                    vertex(buffer, pose, 0.5F, -0.5F, 0.0F, color, 1.0F, 1.0F);
                    vertex(buffer, pose, 0.5F, 0.5F, 0.0F, color, 1.0F, 0.0F);
                    vertex(buffer, pose, -0.5F, 0.5F, 0.0F, color, 0.0F, 0.0F);
                });
        poseStack.popPose();
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose,
            float x, float y, float z, int color, float u, float v) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static int color(float alpha, float red, float green, float blue) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        int r = Mth.clamp(Math.round(red * 255.0F), 0, 255);
        int g = Mth.clamp(Math.round(green * 255.0F), 0, 255);
        int b = Mth.clamp(Math.round(blue * 255.0F), 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
