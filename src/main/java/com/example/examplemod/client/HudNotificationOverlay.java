package com.example.examplemod.client;

import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class HudNotificationOverlay {
    private static final int FADE_TICKS = 8;
    private static final int HOLD_TICKS = 42;
    private static final int TOTAL_TICKS = FADE_TICKS + HOLD_TICKS + FADE_TICKS;
    private static final int MAX_QUEUE_SIZE = 8;
    private static final Queue<Component> QUEUE = new ArrayDeque<>();

    private static Component current = Component.empty();
    private static int ticksRemaining = 0;

    private HudNotificationOverlay() {
    }

    public static void push(String message) {
        push(Component.literal(message));
    }

    public static void push(Component message) {
        if (message == null || message.getString().isBlank()) {
            return;
        }

        if (ticksRemaining <= 0) {
            current = message;
            ticksRemaining = TOTAL_TICKS;
            return;
        }

        if (QUEUE.size() >= MAX_QUEUE_SIZE) {
            QUEUE.poll();
        }
        QUEUE.offer(message);
    }

    public static void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }

        if (ticksRemaining <= 0 && !QUEUE.isEmpty()) {
            current = QUEUE.poll();
            ticksRemaining = TOTAL_TICKS;
        }
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (ticksRemaining <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int textWidth = minecraft.font.width(current);
        int boxWidth = textWidth + 18;
        int boxHeight = 16;
        int x = (width - boxWidth) / 2;
        int y = height - 70;
        int alpha = Math.round(alpha() * 255.0F);

        graphics.fill(x, y, x + boxWidth, y + boxHeight, alpha << 24);
        graphics.centeredText(minecraft.font, current, width / 2, y + 4, alpha << 24 | 0xFFFFFF);
    }

    private static float alpha() {
        int elapsed = TOTAL_TICKS - ticksRemaining;
        if (elapsed < FADE_TICKS) {
            return (float) elapsed / FADE_TICKS;
        }
        if (ticksRemaining < FADE_TICKS) {
            return (float) ticksRemaining / FADE_TICKS;
        }
        return 1.0F;
    }
}
