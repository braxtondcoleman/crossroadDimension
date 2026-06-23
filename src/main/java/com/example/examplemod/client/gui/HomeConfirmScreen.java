package com.example.examplemod.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HomeConfirmScreen extends Screen {

    public HomeConfirmScreen() {
        super(Component.literal("Travel to your Sanctuary?"));
    }

    @Override
    protected void init() {

        int centerX = width / 2;
        int centerY = height / 2;

        addRenderableWidget(
            Button.builder(
                Component.literal("Yes"),
                button -> onAccept()
            )
            .bounds(centerX - 105, centerY + 20, 100, 20)
            .build()
        );

        addRenderableWidget(
            Button.builder(
                Component.literal("No"),
                button -> onCancel()
            )
            .bounds(centerX + 5, centerY + 20, 100, 20)
            .build()
        );
    }

    private void onAccept() {

        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(
                Component.literal("Starting countdown...")
            );
        }

        minecraft.setScreen(null);
    }

    private void onCancel() {
        minecraft.setScreen(null);
    }
}