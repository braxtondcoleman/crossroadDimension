package com.example.examplemod.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import com.example.examplemod.client.gui.CountdownScreen;



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

       minecraft.setScreen(new CountdownScreen());
    }

    private void onCancel() {
        minecraft.setScreen(null);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        // Y = accept
        if (keyCode == GLFW.GLFW_KEY_Y) {
            onAccept();
            return true;
        }

        // N = cancel
        if (keyCode == GLFW.GLFW_KEY_N) {
            onCancel();
            return true;
        }

        // Any other key closes the menu
        onCancel();
        return true;
    }

    @Override
    public void onClose() {
        onCancel();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {

        if (event.key() == GLFW.GLFW_KEY_Y) {
            onAccept();
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_N) {
            onCancel();
            return true;
        }

        onCancel();
        return true;
    }
}