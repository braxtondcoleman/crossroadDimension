package com.example.examplemod.portal;

import com.example.examplemod.CrossroadDimension;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class RealmPortalRuntime {
    private static final int CHANNEL_TICKS = 60;
    private static final int TELEPORT_COOLDOWN_TICKS = 20;
    private static final Map<UUID, ChannelState> CHANNELS = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    private static final RealmPortalManager PORTAL_MANAGER = new RealmPortalManager();

    public static void playerInsideGate(ServerPlayer player, BlockPos pos) {
        if (COOLDOWNS.containsKey(player.getUUID())) {
            return;
        }

        GlobalPos gatePos = GlobalPos.of(player.level().dimension(), baseGatePos(player, pos));
        PORTAL_MANAGER.getPortalAt(player.level().getServer(), gatePos)
                .filter(portal -> portal.state() == RealmPortalState.OPEN || portal.state() == RealmPortalState.CLOSING)
                .filter(portal -> PORTAL_MANAGER.isPortalReadyForTravel(player.level().getServer(), portal))
                .ifPresent(portal -> startOrContinueChannel(player, portal, gatePos));
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tickCooldown(player);
            tickChannel(player);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        PORTAL_MANAGER.expireClosingPortals(event.getServer());
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CHANNELS.remove(player.getUUID());
            COOLDOWNS.remove(player.getUUID());
            PORTAL_MANAGER.handlePlayerDisconnectedInRealm(player);
        }
    }

    private static void startOrContinueChannel(ServerPlayer player, RealmPortalData portal, GlobalPos gatePos) {
        boolean fromOrigin = sameColumn(portal.origin(), gatePos);
        ChannelState current = CHANNELS.get(player.getUUID());
        if (current == null || !current.portalId().equals(portal.portalId()) || current.fromOrigin() != fromOrigin) {
            PORTAL_MANAGER.touchPortalUse(player.level().getServer(), portal.portalId());
            CHANNELS.put(player.getUUID(), new ChannelState(portal.portalId(), fromOrigin, 0));
            CrossroadDimension.LOGGER.info("Started gate channel for {} through portal {}", player.getGameProfile().name(), portal.portalId());
            player.sendSystemMessage(Component.literal("Crossroads gate channeling. Stay inside the tear to travel."));
        }
    }

    private static void tickChannel(ServerPlayer player) {
        ChannelState state = CHANNELS.get(player.getUUID());
        if (state == null) {
            return;
        }

        RealmPortalData portal = PORTAL_MANAGER.getPortal(player.level().getServer(), state.portalId()).orElse(null);
        if (portal == null || portal.state() == RealmPortalState.CLOSED || !playerStillInsideExpectedGate(player, portal, state.fromOrigin())) {
            CHANNELS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Crossroads travel cancelled."));
            return;
        }

        int ticks = state.ticksInside() + 1;
        showChannelFeedback(player, ticks);
        if (ticks >= CHANNEL_TICKS) {
            CHANNELS.remove(player.getUUID());
            PORTAL_MANAGER.completeGateTravel(player, portal, state.fromOrigin());
            COOLDOWNS.put(player.getUUID(), TELEPORT_COOLDOWN_TICKS);
        } else {
            CHANNELS.put(player.getUUID(), new ChannelState(state.portalId(), state.fromOrigin(), ticks));
        }
    }

    private static void showChannelFeedback(ServerPlayer player, int ticks) {
        int secondsRemaining = Math.max(1, (CHANNEL_TICKS - ticks + 19) / 20);
        if (ticks == 1 || ticks % 20 == 0) {
            player.sendSystemMessage(Component.literal("Channeling... stay inside (" + secondsRemaining + ")"));
        }

        if (ticks % 4 == 0 && player.level() instanceof ServerLevel level) {
            BlockPos pos = baseGatePos(player, player.blockPosition());
            double progress = (double) ticks / CHANNEL_TICKS;
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.6D + progress,
                    pos.getZ() + 0.5D,
                    6,
                    0.2D + progress * 0.15D,
                    0.3D,
                    0.2D + progress * 0.15D,
                    0.01D
            );
            level.sendParticles(
                    ParticleTypes.WITCH,
                    pos.getX() + 0.5D,
                    pos.getY() + 1.0D,
                    pos.getZ() + 0.5D,
                    2,
                    0.15D,
                    0.55D,
                    0.15D,
                    0.0D
            );
        }
    }

    private static boolean playerStillInsideExpectedGate(ServerPlayer player, RealmPortalData portal, boolean fromOrigin) {
        GlobalPos playerPos = GlobalPos.of(player.level().dimension(), baseGatePos(player, player.blockPosition()));
        return sameColumn(fromOrigin ? portal.origin() : portal.destination(), playerPos);
    }

    private static void tickCooldown(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Integer ticks = COOLDOWNS.get(playerId);
        if (ticks == null) {
            return;
        }

        if (ticks <= 1) {
            COOLDOWNS.remove(playerId);
        } else {
            COOLDOWNS.put(playerId, ticks - 1);
        }
    }

    private static BlockPos baseGatePos(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockState(pos.below()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
            return pos.below();
        }
        return pos;
    }

    private static boolean sameColumn(GlobalPos first, GlobalPos second) {
        return first.dimension().equals(second.dimension())
                && first.pos().getX() == second.pos().getX()
                && first.pos().getZ() == second.pos().getZ()
                && (first.pos().getY() == second.pos().getY() || first.pos().getY() + 1 == second.pos().getY());
    }

    private record ChannelState(UUID portalId, boolean fromOrigin, int ticksInside) {
    }
}
