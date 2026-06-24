package com.example.examplemod.client;

public class TravelManager {
    public static final int COUNTDOWN_DURATION_TICKS = 60;

    public static boolean awaitingConfirmation = false;
    public static boolean countdownActive = false;

    public static int countdownTicks = 0;

    public static void startConfirmation() {
        awaitingConfirmation = true;
        countdownActive = false;
        countdownTicks = 0;
    }

    public static void startCountdown() {
        awaitingConfirmation = false;
        countdownActive = true;
        countdownTicks = COUNTDOWN_DURATION_TICKS;
    }

    public static void cancel() {
        awaitingConfirmation = false;
        countdownActive = false;
        countdownTicks = 0;
    }

    public static boolean tickCountdown() {
        if (!countdownActive) {
            return false;
        }

        countdownTicks--;
        return countdownTicks <= 0;
    }

    public static int secondsRemaining() {
        return Math.max(1, (countdownTicks + 19) / 20);
    }

    public static float countdownProgress() {
        if (!countdownActive) {
            return 0.0F;
        }

        return 1.0F - Math.max(0.0F, Math.min(1.0F, countdownTicks / (float) COUNTDOWN_DURATION_TICKS));
    }
}
