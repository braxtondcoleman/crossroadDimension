package com.example.examplemod.portal;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CrossroadsCommands {
    private static final RealmPortalManager PORTAL_MANAGER = new RealmPortalManager();

    private CrossroadsCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(literal("crossroads")
                .then(literal("cleanup")
                        .executes(context -> cleanup(context.getSource())))
                .then(literal("list")
                        .executes(context -> list(context.getSource()))));
    }

    private static int cleanup(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        RealmPortalManager.PortalCleanupSummary portalSummary = PORTAL_MANAGER.cleanupAll(server);
        RealmPortalRuntime.RuntimeDebugInfo runtimeSummary = RealmPortalRuntime.clearRuntimeState();

        source.sendSuccess(
                () -> Component.literal(
                        "Crossroads cleanup: portals="
                                + portalSummary.savedPortalsCleared()
                                + ", gateBlocks="
                                + portalSummary.gateBlocksRemoved()
                                + ", labels="
                                + portalSummary.labelsRemoved()
                                + ", timers="
                                + portalSummary.timersCleared()
                                + ", channels="
                                + runtimeSummary.activeChannels()
                                + ", cooldowns="
                                + runtimeSummary.teleportCooldowns()
                ),
                true
        );
        return portalSummary.savedPortalsCleared() + portalSummary.gateBlocksRemoved() + portalSummary.labelsRemoved()
                + portalSummary.timersCleared() + runtimeSummary.activeChannels() + runtimeSummary.teleportCooldowns();
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        RealmPortalManager.PortalDebugInfo portalInfo = PORTAL_MANAGER.debugInfo(server);
        RealmPortalRuntime.RuntimeDebugInfo runtimeInfo = RealmPortalRuntime.debugInfo();

        source.sendSuccess(
                () -> Component.literal(
                        "Crossroads portals: saved="
                                + portalInfo.savedPortals()
                                + ", opening="
                                + portalInfo.openingPortals()
                                + ", open="
                                + portalInfo.openPortals()
                                + ", closing="
                                + portalInfo.closingPortals()
                                + ", closed="
                                + portalInfo.closedPortals()
                                + ", sealedCrystals="
                                + portalInfo.sealedCrystals()
                                + ", closingTimers="
                                + portalInfo.scheduledClosingEffects()
                                + ", channels="
                                + runtimeInfo.activeChannels()
                                + ", cooldowns="
                                + runtimeInfo.teleportCooldowns()
                ),
                false
        );
        return portalInfo.savedPortals();
    }
}
