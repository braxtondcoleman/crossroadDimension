package com.example.examplemod.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CountdownScreen extends Screen {

    private int countdownTicks = 60; // 3 seconds at 20 TPS

    public CountdownScreen() {
        super(Component.literal("Traveling"));
    }

    @Override
    public void tick() {

        countdownTicks--;

        if (countdownTicks <= 0) {

            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(
                    Component.literal("Would teleport home now.")
                );
            }

            minecraft.setScreen(null);
        }
    }
}