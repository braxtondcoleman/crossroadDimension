package com.example.examplemod.client;

public class TravelManager {

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
        countdownTicks = 60;
    }

    public static void cancel() {
        awaitingConfirmation = false;
        countdownActive = false;
        countdownTicks = 0;
    }
}