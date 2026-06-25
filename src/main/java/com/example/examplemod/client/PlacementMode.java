package com.example.examplemod.client;

import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.network.TravelConfirmPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class PlacementMode {
    private static final int MESSAGE_FADE_TICKS = 30;
    private static final int PARTICLE_INTERVAL_TICKS = 4;

    private static boolean active = false;
    private static int fadeTicks = 0;
    private static int particleTicks = 0;
    private static Component statusMessage = Component.empty();
    private static int statusTicks = 0;
    private static boolean placedThisHold = false;
    private static final RandomSource RANDOM = RandomSource.create();

    private PlacementMode() {
    }

    public static void tick(Minecraft minecraft, boolean placementKeyDown) {
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }

        if (isInsidePocketRealm(minecraft)) {
            if (placementKeyDown) {
                showStatus(Component.literal("Cannot use here."));
            }
            active = false;
        } else if (placementKeyDown && minecraft.screen == null) {
            active = true;
            fadeTicks = MESSAGE_FADE_TICKS;
            tickParticles(minecraft);
        } else if (active) {
            active = false;
            fadeTicks = MESSAGE_FADE_TICKS;
            placedThisHold = false;
        } else if (!placementKeyDown) {
            placedThisHold = false;
        }

        if (!active && fadeTicks > 0) {
            fadeTicks--;
        }

        if (statusTicks > 0) {
            statusTicks--;
        }
    }

    public static boolean tryPlace(Minecraft minecraft) {
        if (!active || minecraft.player == null || minecraft.level == null) {
            return false;
        }

        if (placedThisHold) {
            return true;
        }

        if (isInsidePocketRealm(minecraft)) {
            showStatus(Component.literal("Cannot use here."));
            return true;
        }

        if (!hasPotentialPlacementTarget(minecraft)) {
            showStatus(Component.literal("Cannot place here."));
            return true;
        }

        showStatus(Component.literal("Summoning Realm..."));
        ClientPacketDistributor.sendToServer(TravelConfirmPayload.INSTANCE);
        placedThisHold = true;
        return true;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int centerX = width / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 78;

        if (active || fadeTicks > 0) {
            int alpha = active ? 255 : Math.round(255.0F * fadeTicks / MESSAGE_FADE_TICKS);
            int color = alpha << 24 | 0xE4C36A;
            graphics.centeredText(minecraft.font, Component.literal("Crossroads Placement"), centerX, y, color);
        }

        if (statusTicks > 0) {
            int alpha = Math.round(255.0F * statusTicks / MESSAGE_FADE_TICKS);
            int color = alpha << 24 | 0xEDE6FF;
            graphics.centeredText(minecraft.font, statusMessage, centerX, y - 12, color);
        }
    }

    private static void tickParticles(Minecraft minecraft) {
        if (++particleTicks < PARTICLE_INTERVAL_TICKS) {
            return;
        }
        particleTicks = 0;

        BlockHitResult hit = targetedBlock(minecraft);
        if (hit == null) {
            return;
        }

        BlockPos surface = hit.getBlockPos();
        double x = surface.getX() + 0.5D + (RANDOM.nextDouble() - 0.5D) * 0.55D;
        double y = surface.getY() + 1.05D;
        double z = surface.getZ() + 0.5D + (RANDOM.nextDouble() - 0.5D) * 0.55D;

        if (hasPotentialPlacementTarget(minecraft)) {
            minecraft.level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0D, 0.035D, 0.0D);
        } else {
            minecraft.level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0D, 0.01D, 0.0D);
        }
    }

    private static boolean hasPotentialPlacementTarget(Minecraft minecraft) {
        BlockHitResult hit = targetedBlock(minecraft);
        if (hit == null || hit.getDirection() != Direction.UP) {
            return false;
        }

        BlockPos surface = hit.getBlockPos();
        FluidState surfaceFluid = minecraft.level.getFluidState(surface);
        if (surfaceFluid.is(FluidTags.WATER) || surfaceFluid.is(FluidTags.LAVA)) {
            return false;
        }

        BlockPos anchor = surface.above();
        return minecraft.level.getBlockState(anchor).isAir()
                && minecraft.level.getBlockState(anchor.above()).isAir()
                && minecraft.level.getFluidState(anchor).isEmpty()
                && minecraft.level.getFluidState(anchor.above()).isEmpty();
    }

    private static BlockHitResult targetedBlock(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit;
        }

        return null;
    }

    private static void showStatus(Component message) {
        statusMessage = message;
        statusTicks = MESSAGE_FADE_TICKS;
    }

    private static void clear() {
        active = false;
        fadeTicks = 0;
        statusTicks = 0;
        particleTicks = 0;
        placedThisHold = false;
    }

    private static boolean isInsidePocketRealm(Minecraft minecraft) {
        Identifier dimensionId = minecraft.player.level().dimension().identifier();
        return dimensionId.getNamespace().equals(CrossroadDimension.MODID)
                && dimensionId.getPath().startsWith("pocket_realm/");
    }
}
