package com.example.examplemod.client.gui;

import com.example.examplemod.client.TravelManager;
import com.example.examplemod.network.TravelConfirmPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class CountdownScreen extends Screen {

    private int countdownTicks = TravelManager.COUNTDOWN_DURATION_TICKS;
    private boolean completed = false;

    public CountdownScreen() {
        super(Component.literal("Traveling"));
    }

    @Override
    public void tick() {

        countdownTicks--;

        if (countdownTicks <= 0) {
            completed = true;
            ClientPacketDistributor.sendToServer(TravelConfirmPayload.INSTANCE);
            minecraft.setScreen(null);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;
        int secondsRemaining = Math.max(1, (countdownTicks + 19) / 20);
        int panelWidth = 190;
        int panelHeight = 52;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - 30;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xAA100A18);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xCCB78CFF);
        graphics.centeredText(font, Component.literal("Traveling in " + secondsRemaining + "..."), centerX, centerY - 18, 0xFFEDE6FF);

        int barWidth = 160;
        int barHeight = 6;
        int barX = centerX - barWidth / 2;
        int barY = centerY + 4;
        float progress = 1.0F - Math.max(0.0F, Math.min(1.0F, countdownTicks / (float) TravelManager.COUNTDOWN_DURATION_TICKS));
        int filledWidth = Math.round(barWidth * progress);

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x661D1430);
        graphics.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFFE4C36A);
        graphics.outline(barX, barY, barWidth, barHeight, 0xCCB78CFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void onClose() {

        if (!completed && minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(
                Component.literal("Travel Cancelled")
            );
        }

        minecraft.setScreen(null);
    }
}
