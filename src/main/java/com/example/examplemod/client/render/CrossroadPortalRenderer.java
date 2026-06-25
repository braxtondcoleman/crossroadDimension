package com.example.examplemod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class CrossroadPortalRenderer {
    private static final Identifier NOISE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/nether_portal.png");
    private static final RenderType PORTAL_RENDER_TYPE = RenderTypes.entityTranslucent(NOISE_TEXTURE);
    private static final Matrix4f BILLBOARD_ROTATION = new Matrix4f();
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float WIDTH = 1.30F;
    private static final float HEIGHT = 2.40F;
    private static final int GRID_X = 24;
    private static final int GRID_Y = 48;
    private static final int NOISE_SIZE = 64;
    private static final float[] NOISE = createNoise();
    private static final float TEXTURE_SAMPLE_U = 0.5F;
    private static final float TEXTURE_SAMPLE_V = 0.5F;
    private static final int CENTER_RED = 5;
    private static final int CENTER_GREEN = 5;
    private static final int CENTER_BLUE = 7;
    private static final int EDGE_RED = 22;
    private static final int EDGE_GREEN = 18;
    private static final int EDGE_BLUE = 34;
    private static final float TAU = (float) (Math.PI * 2.0D);

    private CrossroadPortalRenderer() {
    }

    public static void submit(CrossroadCrystalRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.portalScale <= 0.0F || state.portalAlpha <= 0.0F) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 1.25D, 0.5D);
        poseStack.mulPose(BILLBOARD_ROTATION.set(camera.viewRotationMatrix).invert());
        poseStack.scale(state.portalScale, state.portalScale, state.portalScale);

        int alpha = Math.round(state.portalAlpha * 255.0F);
        float halfWidth = WIDTH * 0.5F;
        float halfHeight = HEIGHT * 0.5F;
        float flickerPhase = state.portalSwirl;

        submitNodeCollector.submitCustomGeometry(
                poseStack,
                PORTAL_RENDER_TYPE,
                (pose, consumer) -> emitSurface(consumer, pose, halfWidth, halfHeight, alpha, flickerPhase)
        );
        poseStack.popPose();
    }

    private static void emitSurface(VertexConsumer consumer, PoseStack.Pose pose, float halfWidth, float halfHeight, int alpha, float flickerPhase) {
        for (int y = 0; y < GRID_Y; y++) {
            float v0 = (float) y / GRID_Y;
            float v1 = (float) (y + 1) / GRID_Y;
            float localY0 = lerp(-halfHeight, halfHeight, v0);
            float localY1 = lerp(-halfHeight, halfHeight, v1);
            for (int x = 0; x < GRID_X; x++) {
                float u0 = (float) x / GRID_X;
                float u1 = (float) (x + 1) / GRID_X;
                float localX0 = lerp(-halfWidth, halfWidth, u0);
                float localX1 = lerp(-halfWidth, halfWidth, u1);
                vertex(consumer, pose, localX0, localY0, halfWidth, halfHeight, u0, v0, alpha, flickerPhase);
                vertex(consumer, pose, localX1, localY0, halfWidth, halfHeight, u1, v0, alpha, flickerPhase);
                vertex(consumer, pose, localX1, localY1, halfWidth, halfHeight, u1, v1, alpha, flickerPhase);
                vertex(consumer, pose, localX0, localY1, halfWidth, halfHeight, u0, v1, alpha, flickerPhase);
            }
        }
    }

    private static float lerp(float start, float end, float value) {
        return start + (end - start) * value;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float localX, float localY, float halfWidth, float halfHeight, float u, float v, int alpha, float flickerPhase) {
        float normalizedX = localX / halfWidth;
        float normalizedY = localY / halfHeight;
        float distance = normalizedX * normalizedX + normalizedY * normalizedY;
        float mask = alphaMask(distance);
        float edge = edgeExposure(distance);
        float cloud = cloudyNoise(u, v);
        float flicker = brightnessModulation(cloud, flickerPhase);
        float brightness = (0.78F + edge * 0.28F) * flicker;
        int vertexAlpha = Math.round(alpha * mask * (0.78F + edge * 0.10F));
        int red = tint(CENTER_RED, EDGE_RED, edge, brightness);
        int green = tint(CENTER_GREEN, EDGE_GREEN, edge, brightness);
        int blue = tint(CENTER_BLUE, EDGE_BLUE, edge, brightness);
        consumer.addVertex(pose, localX, localY, 0.0F)
                .setColor(red, green, blue, vertexAlpha)
                .setUv(TEXTURE_SAMPLE_U, TEXTURE_SAMPLE_V)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static float alphaMask(float distance) {
        return 1.0F - smoothstep(0.66F, 1.0F, distance);
    }

    private static float edgeExposure(float distance) {
        return smoothstep(0.48F, 0.94F, distance);
    }

    private static float cloudyNoise(float u, float v) {
        float low = sampleNoise(u * 0.72F + 0.11F, v * 0.50F + 0.23F);
        float mid = sampleNoise(u * 1.85F + 0.37F, v * 1.30F + 0.41F);
        float high = sampleNoise(u * 3.60F + 0.61F, v * 2.80F + 0.17F);
        return low * 0.56F + mid * 0.31F + high * 0.13F;
    }

    private static float brightnessModulation(float cloud, float phase) {
        float flicker = 0.5F + 0.5F * (float) Math.sin(phase + cloud * TAU);
        return 0.98F + flicker * 0.035F;
    }

    private static int tint(int center, int edge, float edgeAmount, float brightness) {
        return Math.max(0, Math.min(255, Math.round(lerp(center, edge, edgeAmount) * brightness)));
    }

    private static float sampleNoise(float u, float v) {
        float x = wrap01(u) * NOISE_SIZE;
        float y = wrap01(v) * NOISE_SIZE;
        int x0 = (int) x;
        int y0 = (int) y;
        int x1 = (x0 + 1) & (NOISE_SIZE - 1);
        int y1 = (y0 + 1) & (NOISE_SIZE - 1);
        float tx = smoothFraction(x - x0);
        float ty = smoothFraction(y - y0);
        float a = NOISE[index(x0, y0)];
        float b = NOISE[index(x1, y0)];
        float c = NOISE[index(x0, y1)];
        float d = NOISE[index(x1, y1)];
        return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
    }

    private static float wrap01(float value) {
        return value - (float) Math.floor(value);
    }

    private static float smoothFraction(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static int index(int x, int y) {
        return (y & (NOISE_SIZE - 1)) * NOISE_SIZE + (x & (NOISE_SIZE - 1));
    }

    private static float[] createNoise() {
        float[] noise = new float[NOISE_SIZE * NOISE_SIZE];
        for (int y = 0; y < NOISE_SIZE; y++) {
            for (int x = 0; x < NOISE_SIZE; x++) {
                noise[index(x, y)] = hashNoise(x, y);
            }
        }
        return noise;
    }

    private static float hashNoise(int x, int y) {
        int value = x * 374761393 + y * 668265263;
        value = (value ^ (value >>> 13)) * 1274126177;
        value = value ^ (value >>> 16);
        return (value & 0xFFFF) / 65535.0F;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float amount = Math.max(0.0F, Math.min(1.0F, (value - edge0) / (edge1 - edge0)));
        return amount * amount * (3.0F - 2.0F * amount);
    }
}
