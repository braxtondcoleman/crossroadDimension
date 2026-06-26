package com.example.examplemod.client;

import com.example.examplemod.network.CrystalMenuActionPayload;
import com.example.examplemod.network.OpenCrystalMenuPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class CrossroadsCrystalScreen extends Screen {
    private final OpenCrystalMenuPayload payload;

    public CrossroadsCrystalScreen(OpenCrystalMenuPayload payload) {
        super(Component.literal(realmTitle(payload)));
        this.payload = payload;
    }

    @Override
    protected void init() {
        int buttonWidth = 160;
        int x = (width - buttonWidth) / 2;
        int y = height / 2 - 12;
        int spacing = 24;

        addRenderableWidget(Button.builder(Component.literal(payload.insideRealm() ? "Exit Realm" : "Enter Realm"), button -> sendAndClose("enter")).bounds(x, y, buttonWidth, 20).build());
        int next = y + spacing;
        if (payload.owner()) {
            addRenderableWidget(Button.builder(Component.literal("Bring Nearby"), button -> sendAndClose("bring_nearby")).bounds(x, next, buttonWidth, 20).build());
            next += spacing;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose()).bounds(x, next, buttonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
        int centerX = width / 2;
        int top = height / 2 - 52;
        graphics.centeredText(font, Component.literal(realmTitle(payload)), centerX, top, 0xFFE4C36A);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    private void sendAndClose(String action) {
        ClientPacketDistributor.sendToServer(new CrystalMenuActionPayload(payload.anchorPos(), action));
        onClose();
    }

    private static String realmTitle(OpenCrystalMenuPayload payload) {
        return payload.ownerName() + "'s Realm";
    }
}
