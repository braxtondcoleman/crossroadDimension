package com.example.examplemod.client.render;

import java.util.function.BiConsumer;

import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.item.WispJarItem;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
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
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;

import org.joml.Quaternionf;

public class WispSpiritLayer
        extends GeoRenderLayer<WispJarItem, GeoItemRenderer.RenderData, GeoRenderState> {
    private static final DataTicket<Integer> RESONANCE_COLOR =
            DataTicket.create("wisp_jar_resonance_color", Integer.class);
    private static final String SPIRIT_ANCHOR = "SpiritAnchor";
    private static final Identifier ORB_TEXTURE =
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "textures/item/wisp_orb.png");
    private static final Identifier DIAMOND_TEXTURE =
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "textures/item/wisp_diamond.png");

    public WispSpiritLayer(GeoItemRenderer<WispJarItem> renderer) {
        super(renderer);
    }

    @Override
    public void addRenderData(WispJarItem jar, GeoItemRenderer.RenderData renderData,
            GeoRenderState renderState, float partialTick) {
        if (renderData != null) {
            renderState.addGeckolibData(RESONANCE_COLOR, ARGB.colorFromFloat(1.0F,
                    jar.getRed(renderData.itemStack()),
                    jar.getGreen(renderData.itemStack()),
                    jar.getBlue(renderData.itemStack())));
        }
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
                                "Unable to find {} bone for Wisp Jar rendering", SPIRIT_ANCHOR));
    }

    private void renderSpirit(RenderPassInfo<GeoRenderState> renderPassInfo, GeoBone ignoredBone,
            SubmitNodeCollector renderTasks) {
        float time = (float) renderTime(renderPassInfo.renderState());
        // Organic wandering instead of a perfect orbit
        float driftY = Mth.sin(time * 0.023F + 0.6F) * 0.005F;

        float orbX =
                Mth.sin(time * 0.014F) * 0.060F +
                Mth.sin(time * 0.027F + 2.3F) * 0.028F;

        float orbZ =
                Mth.cos(time * 0.016F + 1.4F) * 0.050F +
                Mth.sin(time * 0.022F + 0.8F) * 0.022F;
        float orbY = 0.21F + Mth.sin(time * 0.072F) * 0.045F + driftY;
        float orbScale = 0.75F * (1.0F + Mth.sin(time * 0.068F) * 0.15F);
        float orbAlpha = 0.72F + Mth.sin(time * 0.055F) * 0.10F;
        int orbColor = renderPassInfo.renderState()
                .getOrDefaultGeckolibData(RESONANCE_COLOR, ARGB.white(1.0F));
        renderBillboard(renderPassInfo, renderTasks, ORB_TEXTURE,
                orbX, orbY, orbZ, orbScale * 1.5F, ARGB.white(0.22F));
        renderBillboard(renderPassInfo, renderTasks, ORB_TEXTURE,
                orbX, orbY, orbZ, orbScale,
                ARGB.color(orbAlpha, orbColor));

        float diamondSway = Mth.sin(time * 0.035F) * 0.006F;
        float diamondY = Mth.sin(time * 0.028F + 1.6F) * 0.010F;
        float diamondScale = 0.6F * (1.0F + Mth.sin(time * 0.08F + 1.2F) * 0.08F);
        float diamondAlpha = 1.0F;
        int resonanceColor = renderPassInfo.renderState()
                .getOrDefaultGeckolibData(RESONANCE_COLOR, 0xE0FFFFFF);
        renderBillboard(renderPassInfo, renderTasks, DIAMOND_TEXTURE,
                diamondSway + 0.006F,
                diamondY,
                0.020F,
                diamondScale,
                resonanceColor);
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
                .getOrDefaultGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE,
                        ItemDisplayContext.NONE);
        if (context != ItemDisplayContext.GUI) {
            poseStack.mulPose(renderPassInfo.cameraState().orientation);
        }

        poseStack.scale(size, size, size);
        renderTasks.submitCustomGeometry(poseStack,
                RenderTypes.textSeeThrough(texture),
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

}
