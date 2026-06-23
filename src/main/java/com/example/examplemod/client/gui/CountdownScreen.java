package com.example.examplemod.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CountdownScreen extends Screen {

    private int countdownTicks = 60;
    private int lastSecondShown = 3;

    public CountdownScreen() {
        super(Component.literal("Traveling"));
    }

    @Override
    public void tick() {

        countdownTicks--;

        int secondsRemaining = (countdownTicks + 19) / 20;

        if (secondsRemaining != lastSecondShown) {
            lastSecondShown = secondsRemaining;

            if (minecraft != null && minecraft.player != null && secondsRemaining > 0) {
                minecraft.player.sendSystemMessage(
                    Component.literal("Traveling in " + secondsRemaining + "...")
                );
            }
        }

        if (countdownTicks <= 0) {

            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(
                    Component.literal("Would teleport home now.")
                );
            }

            minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {

        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(
                Component.literal("Travel Cancelled")
            );
        }

        minecraft.setScreen(null);
    }
}