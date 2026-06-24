package com.example.examplemod.portal;

import com.example.examplemod.CrossroadDimension;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

public class RealmPortalManager {
    public static final int MIN_GATE_SPACING_RADIUS = 16;
    private static final long CLOSING_EXPIRATION_TICKS = 2400L;

    public RealmPortalData createPortal(
            MinecraftServer server,
            UUID owner,
            GlobalPos origin,
            GlobalPos destination,
            OptionalLong expirationGameTime
    ) {
        UUID portalId = UUID.randomUUID();
        RealmPortalData portal = RealmPortalData.create(owner, portalId, origin, destination, expirationGameTime);
        savedData(server).put(portal);
        CrossroadDimension.LOGGER.info(
                "Created realm portal {} for owner {} from {} at {} to {} at {}",
                portalId,
                owner,
                origin.dimension().identifier(),
                origin.pos(),
                destination.dimension().identifier(),
                destination.pos()
        );
        return portal;
    }

    public Optional<RealmPortalData> createActiveGate(
            ServerPlayer owner,
            GlobalPos origin,
            GlobalPos destination
    ) {
        MinecraftServer server = owner.level().getServer();
        Component failure = validateGatePlacement(server, origin, owner.getUUID());
        if (failure != null) {
            owner.sendSystemMessage(failure);
            CrossroadDimension.LOGGER.info("Gate placement failed for {}: {}", owner.getGameProfile().name(), failure.getString());
            return Optional.empty();
        }

        savedData(server).findActiveForOwner(owner.getUUID()).ifPresent(oldPortal -> {
            CrossroadDimension.LOGGER.info("Relocating active gate for {}; closing old portal {}", owner.getGameProfile().name(), oldPortal.portalId());
            removeGateBlocks(server, oldPortal);
            savedData(server).put(oldPortal.closed());
        });

        RealmPortalData portal = createPortal(server, owner.getUUID(), origin, destination, OptionalLong.empty());
        placeGateBlocks(server, portal);
        return Optional.of(portal);
    }

    public Optional<RealmPortalData> getPortal(MinecraftServer server, UUID portalId) {
        return savedData(server).find(portalId);
    }

    public Optional<RealmPortalData> getPortalAt(MinecraftServer server, GlobalPos position) {
        return savedData(server).findAt(position);
    }

    public Optional<RealmPortalData> closePortal(MinecraftServer server, UUID portalId, long expirationGameTime) {
        Optional<RealmPortalData> existing = getPortal(server, portalId);
        existing.ifPresent(portal -> {
            RealmPortalData closingPortal = portal.closing(expirationGameTime);
            savedData(server).put(closingPortal);
            CrossroadDimension.LOGGER.info(
                    "Realm portal {} moved to CLOSING; expiration game time {}",
                    portalId,
                    expirationGameTime
            );
        });
        return existing.map(portal -> portal.closing(expirationGameTime));
    }

    public boolean isPortalExpired(RealmPortalData portal, long currentGameTime) {
        return portal.expirationGameTime().isPresent()
                && currentGameTime >= portal.expirationGameTime().getAsLong();
    }

    public void completeGateTravel(ServerPlayer player, RealmPortalData portal, boolean fromOrigin) {
        MinecraftServer server = player.level().getServer();
        RealmPortalData current = getPortal(server, portal.portalId()).orElse(portal);
        ServerLevel targetLevel = server.getLevel(fromOrigin ? current.destinationDimension() : current.originDimension());
        if (targetLevel == null) {
            player.sendSystemMessage(Component.literal("Crossroads destination is not loaded."));
            return;
        }

        BlockPos targetPos = fromOrigin ? current.destinationPosition() : current.originPosition();
        player.teleportTo(targetLevel, targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, java.util.Set.of(), player.getYRot(), player.getXRot(), true);

        if (fromOrigin) {
            enterRealm(server, current);
        } else {
            leaveRealm(server, current);
        }
        player.sendSystemMessage(Component.literal("Crossroads travel complete."));
    }

    public void expireClosingPortals(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        for (RealmPortalData portal : savedData(server).all()) {
            if (portal.state() == RealmPortalState.CLOSING && isPortalExpired(portal, gameTime)) {
                CrossroadDimension.LOGGER.info("Expiring portal {}", portal.portalId());
                removeGateBlocks(server, portal);
                savedData(server).put(portal.closed());
            }
        }
    }

    public void initialize(MinecraftServer server) {
        RealmPortalSavedData data = savedData(server);
        CrossroadDimension.LOGGER.info("Realm portal SavedData loaded with {} portal(s)", data.size());
    }

    private RealmPortalSavedData savedData(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(RealmPortalSavedData.TYPE);
    }

    private void enterRealm(MinecraftServer server, RealmPortalData portal) {
        int population = portal.realmPopulation() + 1;
        RealmPortalData updated = portal.openWithPopulation(population);
        savedData(server).put(updated);
        CrossroadDimension.LOGGER.info("Portal {} population increased to {}", portal.portalId(), population);
    }

    private void leaveRealm(MinecraftServer server, RealmPortalData portal) {
        int population = Math.max(0, portal.realmPopulation() - 1);
        RealmPortalData updated = population == 0
                ? portal.withPopulation(0).closing(server.overworld().getGameTime() + CLOSING_EXPIRATION_TICKS)
                : portal.withPopulation(population);
        savedData(server).put(updated);
        CrossroadDimension.LOGGER.info("Portal {} population decreased to {}; state {}", portal.portalId(), population, updated.state());
    }

    private Component validateGatePlacement(MinecraftServer server, GlobalPos origin, UUID owner) {
        ServerLevel level = server.getLevel(origin.dimension());
        if (level == null) {
            return Component.literal("Cannot open gate: origin dimension is not loaded.");
        }

        BlockPos pos = origin.pos();
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return Component.literal("Cannot open gate: needs two blocks of vertical air.");
        }

        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return Component.literal("Cannot open gate: the space is inside liquid.");
        }

        if (getPortalAt(server, origin).isPresent()) {
            return Component.literal("Cannot open gate: this space is already a gate.");
        }

        for (RealmPortalData portal : savedData(server).all()) {
            if (portal.state() != RealmPortalState.CLOSED
                    && portal.originDimension().equals(origin.dimension())
                    && !portal.owner().equals(owner)
                    && portal.originPosition().closerThan(pos, MIN_GATE_SPACING_RADIUS)) {
                return Component.literal("Cannot open gate: another active gate is too close.");
            }
        }

        return null;
    }

    private void placeGateBlocks(MinecraftServer server, RealmPortalData portal) {
        placeGateColumn(server, portal.origin());
        placeGateColumn(server, portal.destination());
        CrossroadDimension.LOGGER.info("Placed gate blocks for portal {}", portal.portalId());
    }

    private void placeGateColumn(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            level.setBlockAndUpdate(pos.pos(), CrossroadDimension.CROSSROADS_GATE.get().defaultBlockState());
            level.setBlockAndUpdate(pos.pos().above(), CrossroadDimension.CROSSROADS_GATE.get().defaultBlockState());
        }
    }

    private void removeGateBlocks(MinecraftServer server, RealmPortalData portal) {
        removeGateColumn(server, portal.origin());
        removeGateColumn(server, portal.destination());
    }

    private void removeGateColumn(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            if (level.getBlockState(pos.pos()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos(), Blocks.AIR.defaultBlockState());
            }
            if (level.getBlockState(pos.pos().above()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos().above(), Blocks.AIR.defaultBlockState());
            }
        }
    }
}
