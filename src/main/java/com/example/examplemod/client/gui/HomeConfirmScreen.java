package com.example.examplemod.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HomeConfirmScreen extends Screen {

    public HomeConfirmScreen() {
        super(Component.literal("Travel to your Realm?"));
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

    @Override
    public void onClose() {
        onCancel();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;
        int panelWidth = 190;
        int panelHeight = 72;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - 40;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xAA100A18);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xCCB78CFF);
        graphics.centeredText(font, Component.literal("Travel to your Realm?"), centerX, centerY - 28, 0xFFEDE6FF);
        graphics.centeredText(font, Component.literal("Press Y or click Yes"), centerX, centerY - 14, 0xFFCBBDE8);
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
    public boolean keyPressed(KeyEvent event) {

        if (event.key() == GLFW.GLFW_KEY_Y) {
            onAccept();
            return true;
        }

        onCancel();
        return true;
    }
}
