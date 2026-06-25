package com.example.examplemod.portal;

import com.example.examplemod.CrossroadDimension;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class RealmPortalManager {
    public static final int MIN_GATE_SPACING_RADIUS = 16;
    private static final long CLOSING_EXPIRATION_TICKS = 2400L;
    private static final long TIMEOUT_DEBUG_INTERVAL_TICKS = 200L;
    private static final String LABEL_TAG_PREFIX = CrossroadDimension.MODID + ".portal_label.";
    private static final Map<UUID, Long> CLOSING_EFFECT_STARTS = new HashMap<>();
    private static final Map<String, Long> TIMEOUT_DEBUG_LOGS = new HashMap<>();

    public RealmPortalData createPortal(
            MinecraftServer server,
            UUID owner,
            String ownerName,
            GlobalPos origin,
            GlobalPos destination,
            OptionalLong expirationGameTime
    ) {
        UUID portalId = UUID.randomUUID();
        RealmPortalData portal = RealmPortalData.create(owner, ownerName, portalId, origin, destination, server.overworld().getGameTime(), expirationGameTime);
        savedData(server).put(portal);
        CrossroadDimension.LOGGER.info(
                "Created realm portal {} for owner {} outsideState={} realmState={} from {} at {} to {} at {}",
                portalId,
                owner,
                portal.outsideState(),
                portal.realmState(),
                origin.dimension().identifier(),
                origin.pos(),
                destination.dimension().identifier(),
                destination.pos()
        );
        return portal;
    }

    public Optional<RealmPortalData> createActiveGate(ServerPlayer owner, GlobalPos origin, GlobalPos destination) {
        MinecraftServer server = owner.level().getServer();
        CrossroadDimension.LOGGER.info(
                "Validating active gate placement for {} at {} {} with radius {}",
                owner.getGameProfile().name(),
                origin.dimension().identifier(),
                origin.pos(),
                MIN_GATE_SPACING_RADIUS
        );
        Component failure = validateGatePlacement(server, origin, owner.getUUID());
        if (failure != null) {
            owner.sendSystemMessage(failure);
            CrossroadDimension.LOGGER.info("Gate placement failed for {}: {}", owner.getGameProfile().name(), failure.getString());
            return Optional.empty();
        }

        savedData(server).findActiveForOwner(owner.getUUID()).ifPresent(oldPortal -> relocateOldGate(server, owner, oldPortal));

        boolean destinationWasSealed = hasGateColumn(server, destination);
        RealmPortalData portal = createPortal(server, owner.getUUID(), owner.getGameProfile().name(), origin, destination, OptionalLong.empty());
        placeGateBlocks(server, portal, destinationWasSealed);
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
            RealmPortalData closingPortal = portal.outsideClosing(expirationGameTime);
            savedData(server).put(closingPortal);
            CrossroadDimension.LOGGER.info(
                    "Portal {} outside state transition {} -> CLOSING; expiration game time {}",
                    portalId,
                    portal.outsideState(),
                    expirationGameTime
            );
        });
        return existing.map(portal -> portal.outsideClosing(expirationGameTime));
    }

    public boolean isPortalReadyForTravel(RealmPortalData portal, boolean fromOrigin) {
        RealmPortalState state = fromOrigin ? portal.outsideState() : portal.realmState();
        return state == RealmPortalState.OPEN || state == RealmPortalState.CLOSING;
    }

    public void touchPortalUse(MinecraftServer server, UUID portalId, boolean fromOrigin) {
        getPortal(server, portalId).ifPresent(portal -> {
            RealmPortalData updated = portal;
            if (fromOrigin && portal.outsideState() == RealmPortalState.CLOSING) {
                updated = updated.withOutsideState(RealmPortalState.OPEN, OptionalLong.empty());
                CrossroadDimension.LOGGER.info("Portal {} outside timeout reset: CLOSING -> OPEN", portalId);
            }
            if (portal.realmState() == RealmPortalState.CLOSING) {
                updated = updated.withRealmState(RealmPortalState.OPEN, OptionalLong.empty());
                CLOSING_EFFECT_STARTS.remove(portalId);
                CrossroadDimension.LOGGER.info("Portal {} realm timeout reset: CLOSING -> OPEN", portalId);
            }
            clearTimeoutDebug(portalId);
            savedData(server).put(updated);
        });
    }

    public void handleCrystalSlamComplete(MinecraftServer server, GlobalPos crystalPos) {
        for (RealmPortalData portal : savedData(server).all()) {
            if (sameColumn(portal.origin(), crystalPos)) {
                handleOutsideSlamComplete(server, portal);
                return;
            }
            if (sameColumn(portal.destination(), crystalPos)) {
                handleRealmSlamComplete(server, portal);
                return;
            }
        }
        CrossroadDimension.LOGGER.info("Crystal slam completion ignored at {}; no linked portal found", crystalPos);
    }

    public void handleCrystalReformComplete(MinecraftServer server, GlobalPos crystalPos) {
        for (RealmPortalData portal : savedData(server).all()) {
            if (sameColumn(portal.destination(), crystalPos)) {
                RealmPortalData sealed = portal.realmSealed();
                savedData(server).put(sealed);
                CLOSING_EFFECT_STARTS.remove(portal.portalId());
                clearTimeoutDebug(portal.portalId());
                CrossroadDimension.LOGGER.info(
                        "Portal {} realm state transition {} -> SEALED after crystal reform completion",
                        portal.portalId(),
                        portal.realmState()
                );
                return;
            }
        }
        CrossroadDimension.LOGGER.info("Crystal reform completion ignored at {}; no linked portal found", crystalPos);
    }

    public boolean tryReopenSealedRealm(ServerPlayer player, GlobalPos anchorPos) {
        MinecraftServer server = player.level().getServer();
        for (RealmPortalData portal : savedData(server).all()) {
            if (sameColumn(portal.destination(), anchorPos) && portal.realmState() == RealmPortalState.SEALED) {
                RealmPortalData opening = portal.withRealmState(RealmPortalState.OPENING, OptionalLong.empty());
                savedData(server).put(opening);
                triggerCrystalSlam(server, portal.destination());
                player.sendSystemMessage(Component.literal("Crossroads gate reopening."));
                CrossroadDimension.LOGGER.info(
                        "Portal {} sealed realm anchor used by {}; realm SEALED -> OPENING; slam triggered",
                        portal.portalId(),
                        player.getGameProfile().name()
                );
                return true;
            }
        }
        return false;
    }

    public PortalDebugInfo debugInfo(MinecraftServer server) {
        RealmPortalSavedData data = savedData(server);
        long opening = 0L;
        long open = 0L;
        long closing = 0L;
        long closed = 0L;
        long sealedCrystals = 0L;

        for (RealmPortalData portal : data.all()) {
            if (portal.outsideState() == RealmPortalState.OPENING || portal.realmState() == RealmPortalState.OPENING) {
                opening++;
            }
            if (portal.outsideState() == RealmPortalState.OPEN || portal.realmState() == RealmPortalState.OPEN) {
                open++;
            }
            if (portal.outsideState() == RealmPortalState.CLOSING || portal.realmState() == RealmPortalState.CLOSING) {
                closing++;
            }
            if (portal.outsideState() == RealmPortalState.CLOSED) {
                closed++;
            }
            if (portal.realmState() == RealmPortalState.SEALED && hasGateColumn(server, portal.destination())) {
                sealedCrystals++;
            }
        }

        return new PortalDebugInfo(data.size(), opening, open, closing, closed, sealedCrystals, CLOSING_EFFECT_STARTS.size() + TIMEOUT_DEBUG_LOGS.size());
    }

    public PortalCleanupSummary cleanupAll(MinecraftServer server) {
        RealmPortalSavedData data = savedData(server);
        int portals = data.size();
        int gateBlocks = 0;
        int labels = 0;

        for (RealmPortalData portal : data.all()) {
            gateBlocks += removeGateColumn(server, portal.origin());
            gateBlocks += removeGateColumn(server, portal.destination());
            labels += removeGateLabels(server, portal);
        }

        labels += removeAllGateLabels(server);
        int timers = CLOSING_EFFECT_STARTS.size() + TIMEOUT_DEBUG_LOGS.size();
        CLOSING_EFFECT_STARTS.clear();
        TIMEOUT_DEBUG_LOGS.clear();
        data.clear();
        CrossroadDimension.LOGGER.info(
                "Crossroads cleanup complete: portals={}, gateBlocks={}, labels={}, timers={}",
                portals,
                gateBlocks,
                labels,
                timers
        );
        return new PortalCleanupSummary(portals, gateBlocks, labels, timers);
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
        tickOpenPortalEffects(server, gameTime);
        tickClosingEffects(server, gameTime);
        for (RealmPortalData portal : savedData(server).all()) {
            tickOutsideTimeout(server, portal, gameTime);
            tickRealmTimeout(server, getPortal(server, portal.portalId()).orElse(portal), gameTime);
        }
    }

    public void handlePlayerDisconnectedInRealm(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        savedData(server).all().stream()
                .filter(portal -> portal.realmState() != RealmPortalState.SEALED)
                .filter(portal -> portal.destinationDimension().equals(player.level().dimension()))
                .findFirst()
                .ifPresent(portal -> {
                    CrossroadDimension.LOGGER.info(
                            "{} disconnected inside realm for portal {}; treating as realm exit",
                            player.getGameProfile().name(),
                            portal.portalId()
                    );
                    leaveRealm(server, portal);
                });
    }

    public void initialize(MinecraftServer server) {
        RealmPortalSavedData data = savedData(server);
        CrossroadDimension.LOGGER.info("Realm portal SavedData loaded with {} portal(s)", data.size());
        for (RealmPortalData portal : data.all()) {
            CrossroadDimension.LOGGER.info(
                    "Loaded portal {} owner={} outsideState={} realmState={} population={} outsideExpiration={} realmExpiration={}",
                    portal.portalId(),
                    portal.owner(),
                    portal.outsideState(),
                    portal.realmState(),
                    portal.realmPopulation(),
                    portal.outsideExpirationGameTime().isPresent() ? portal.outsideExpirationGameTime().getAsLong() : "none",
                    portal.realmExpirationGameTime().isPresent() ? portal.realmExpirationGameTime().getAsLong() : "none"
            );
            if (portal.outsideState() == RealmPortalState.CLOSED) {
                removeGateColumn(server, portal.origin());
                removeGateLabel(server.getLevel(portal.originDimension()), portal.origin(), portal);
            }
        }
    }

    private RealmPortalSavedData savedData(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(RealmPortalSavedData.TYPE);
    }

    private void relocateOldGate(MinecraftServer server, ServerPlayer owner, RealmPortalData oldPortal) {
        CrossroadDimension.LOGGER.info(
                "Relocating active gate for {}; old portal {} outsideState={} realmState={}",
                owner.getGameProfile().name(),
                oldPortal.portalId(),
                oldPortal.outsideState(),
                oldPortal.realmState()
        );
        removeGateColumn(server, oldPortal.origin());
        removeGateLabel(server.getLevel(oldPortal.originDimension()), oldPortal.origin(), oldPortal);
        if (oldPortal.realmState() != RealmPortalState.SEALED) {
            removeGateColumn(server, oldPortal.destination());
            removeGateLabel(server.getLevel(oldPortal.destinationDimension()), oldPortal.destination(), oldPortal);
        }
        savedData(server).put(oldPortal.closed());
    }

    private void enterRealm(MinecraftServer server, RealmPortalData portal) {
        RealmPortalData latest = getPortal(server, portal.portalId()).orElse(portal);
        int previousPopulation = latest.realmPopulation();
        int population = previousPopulation + 1;
        RealmPortalData updated = latest.withPopulation(population)
                .withOutsideState(RealmPortalState.OPEN, OptionalLong.empty())
                .withRealmState(RealmPortalState.OPEN, OptionalLong.empty());
        savedData(server).put(updated);
        clearTimeoutDebug(latest.portalId());
        CrossroadDimension.LOGGER.info(
                "Portal {} population {} -> {}; outside {} -> {}; realm {} -> {}; timeouts reset",
                latest.portalId(),
                previousPopulation,
                population,
                latest.outsideState(),
                updated.outsideState(),
                latest.realmState(),
                updated.realmState()
        );
    }

    private void leaveRealm(MinecraftServer server, RealmPortalData portal) {
        RealmPortalData latest = getPortal(server, portal.portalId()).orElse(portal);
        int previousPopulation = latest.realmPopulation();
        int population = Math.max(0, previousPopulation - 1);
        RealmPortalData updated = latest.withPopulation(population);
        if (population == 0) {
            long expirationGameTime = server.overworld().getGameTime() + CLOSING_EXPIRATION_TICKS;
            updated = updated.outsideClosing(expirationGameTime);
            clearTimeoutDebug(latest.portalId());
            CrossroadDimension.LOGGER.info(
                    "Portal {} outside timeout created: now={} expires={} durationTicks={}",
                    latest.portalId(),
                    server.overworld().getGameTime(),
                    expirationGameTime,
                    CLOSING_EXPIRATION_TICKS
            );
        }
        savedData(server).put(updated);
        CrossroadDimension.LOGGER.info(
                "Portal {} population {} -> {}; outside {} -> {}; realm {} -> {}",
                latest.portalId(),
                previousPopulation,
                population,
                latest.outsideState(),
                updated.outsideState(),
                latest.realmState(),
                updated.realmState()
        );
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

        int nearbyPortalCount = 0;
        for (RealmPortalData portal : savedData(server).all()) {
            if (portal.outsideState() != RealmPortalState.CLOSED && portal.originDimension().equals(origin.dimension())) {
                double distance = horizontalDistance(portal.originPosition(), pos);
                boolean withinRadius = distance < MIN_GATE_SPACING_RADIUS;
                CrossroadDimension.LOGGER.info(
                        "Gate radius check against portal {} owner={} outsideState={} origin={} distance={} withinRadius={}",
                        portal.portalId(),
                        portal.owner(),
                        portal.outsideState(),
                        portal.originPosition(),
                        String.format(java.util.Locale.ROOT, "%.2f", distance),
                        withinRadius
                );
                if (withinRadius) {
                    nearbyPortalCount++;
                }
            }
        }
        CrossroadDimension.LOGGER.info("Gate placement nearby active portal count within radius: {}", nearbyPortalCount);

        for (RealmPortalData portal : savedData(server).all()) {
            if (portal.outsideState() != RealmPortalState.CLOSED
                    && portal.originDimension().equals(origin.dimension())
                    && horizontalDistance(portal.originPosition(), pos) < MIN_GATE_SPACING_RADIUS) {
                CrossroadDimension.LOGGER.info(
                        "Gate placement failed: portal {} is within radius at distance {}",
                        portal.portalId(),
                        String.format(java.util.Locale.ROOT, "%.2f", horizontalDistance(portal.originPosition(), pos))
                );
                return Component.literal("Cannot open gate: another active gate is too close.");
            }
        }

        return null;
    }

    private void placeGateBlocks(MinecraftServer server, RealmPortalData portal, boolean destinationWasSealed) {
        placeGateColumn(server, portal.origin());
        placeGateColumn(server, portal.destination());
        placeGateLabel(server, portal.origin(), portal);
        placeGateLabel(server, portal.destination(), portal);
        triggerCrystalOpening(server, portal.origin());
        if (destinationWasSealed) {
            triggerCrystalSlam(server, portal.destination());
            CrossroadDimension.LOGGER.info("Portal {} sealed realm crystal interrupted: idle -> slam", portal.portalId());
        } else {
            triggerCrystalOpening(server, portal.destination());
        }
        CrossroadDimension.LOGGER.info(
                "Placed gate blocks for portal {}; outsideState={} realmState={}",
                portal.portalId(),
                portal.outsideState(),
                portal.realmState()
        );
    }

    private void handleOutsideSlamComplete(MinecraftServer server, RealmPortalData portal) {
        if (portal.outsideState() != RealmPortalState.OPENING) {
            CrossroadDimension.LOGGER.info(
                    "Portal {} outside slam completion ignored; outsideState={}",
                    portal.portalId(),
                    portal.outsideState()
            );
            return;
        }

        RealmPortalData opened = portal.outsideOpen();
        savedData(server).put(opened);
        spawnColumnParticles(server, opened.origin(), ParticleTypes.REVERSE_PORTAL, 28);
        CrossroadDimension.LOGGER.info(
                "Portal {} outside slam completion trigger: OPENING -> OPEN; portal effects started",
                portal.portalId()
        );
    }

    private void handleRealmSlamComplete(MinecraftServer server, RealmPortalData portal) {
        if (portal.realmState() == RealmPortalState.OPEN) {
            CrossroadDimension.LOGGER.info("Portal {} realm slam completion ignored; realm already OPEN", portal.portalId());
            return;
        }

        RealmPortalData opened = portal.realmOpen();
        savedData(server).put(opened);
        CLOSING_EFFECT_STARTS.remove(portal.portalId());
        clearTimeoutDebug(portal.portalId());
        spawnColumnParticles(server, opened.destination(), ParticleTypes.REVERSE_PORTAL, 28);
        CrossroadDimension.LOGGER.info(
                "Portal {} realm slam completion trigger: {} -> OPEN; portal effects started",
                portal.portalId(),
                portal.realmState()
        );
    }

    private void tickOpenPortalEffects(MinecraftServer server, long gameTime) {
        for (RealmPortalData portal : savedData(server).all()) {
            if ((portal.outsideState() == RealmPortalState.OPEN || portal.outsideState() == RealmPortalState.CLOSING) && gameTime % 10L == 0L) {
                spawnColumnParticles(server, portal.origin(), ParticleTypes.END_ROD, 4);
            }
            if ((portal.realmState() == RealmPortalState.OPEN || portal.realmState() == RealmPortalState.CLOSING) && gameTime % 10L == 0L) {
                spawnColumnParticles(server, portal.destination(), ParticleTypes.END_ROD, 4);
            }

            RealmPortalData updated = portal;
            if (portal.outsideState() == RealmPortalState.OPEN && portal.realmPopulation() == 0 && portal.outsideExpirationGameTime().isEmpty()) {
                long expiration = gameTime + CLOSING_EXPIRATION_TICKS;
                updated = updated.outsideClosing(expiration);
                CrossroadDimension.LOGGER.info("Portal {} outside OPEN -> CLOSING; expiration={}", portal.portalId(), expiration);
            }
            if (updated.realmState() == RealmPortalState.OPEN && updated.realmExpirationGameTime().isEmpty()) {
                long expiration = gameTime + CLOSING_EXPIRATION_TICKS;
                updated = updated.realmClosing(expiration);
                CrossroadDimension.LOGGER.info("Portal {} realm OPEN -> CLOSING; expiration={}", portal.portalId(), expiration);
            }
            if (updated != portal) {
                savedData(server).put(updated);
                clearTimeoutDebug(portal.portalId());
            }
        }
    }

    private void tickOutsideTimeout(MinecraftServer server, RealmPortalData portal, long gameTime) {
        if (portal.outsideState() != RealmPortalState.CLOSING || portal.outsideExpirationGameTime().isEmpty()) {
            return;
        }
        if (!isChunkLoaded(server, portal.origin())) {
            RealmPortalData paused = portal.outsideClosing(portal.outsideExpirationGameTime().getAsLong() + 1L);
            savedData(server).put(paused);
            if (gameTime % TIMEOUT_DEBUG_INTERVAL_TICKS == 0L) {
                CrossroadDimension.LOGGER.info("Portal {} outside timeout paused; origin chunk unloaded", portal.portalId());
            }
            return;
        }
        logTimeoutStatus(portal, "outside", portal.outsideExpirationGameTime().getAsLong(), gameTime);
        if (gameTime >= portal.outsideExpirationGameTime().getAsLong()) {
            removeGateColumn(server, portal.origin());
            removeGateLabel(server.getLevel(portal.originDimension()), portal.origin(), portal);
            RealmPortalData closed = portal.outsideClosed();
            savedData(server).put(closed);
            clearTimeoutDebug(portal.portalId(), "outside");
            CrossroadDimension.LOGGER.info("Portal {} outside timeout expired: CLOSING -> CLOSED", portal.portalId());
        }
    }

    private void tickRealmTimeout(MinecraftServer server, RealmPortalData portal, long gameTime) {
        if (portal.realmState() != RealmPortalState.CLOSING || portal.realmExpirationGameTime().isEmpty()) {
            return;
        }
        if (!isChunkLoaded(server, portal.destination())) {
            RealmPortalData paused = portal.realmClosing(portal.realmExpirationGameTime().getAsLong() + 1L);
            savedData(server).put(paused);
            if (gameTime % TIMEOUT_DEBUG_INTERVAL_TICKS == 0L) {
                CrossroadDimension.LOGGER.info("Portal {} realm timeout paused; destination chunk unloaded", portal.portalId());
            }
            return;
        }
        logTimeoutStatus(portal, "realm", portal.realmExpirationGameTime().getAsLong(), gameTime);
        if (gameTime >= portal.realmExpirationGameTime().getAsLong() && !CLOSING_EFFECT_STARTS.containsKey(portal.portalId())) {
            removeGateLabel(server.getLevel(portal.destinationDimension()), portal.destination(), portal);
            spawnColumnParticles(server, portal.destination(), ParticleTypes.WITCH, 18);
            triggerCrystalReformToIdle(server, portal);
            CLOSING_EFFECT_STARTS.put(portal.portalId(), gameTime);
            clearTimeoutDebug(portal.portalId(), "realm");
            CrossroadDimension.LOGGER.info("Portal {} realm timeout expired: portal visual removed; crystal reform triggered", portal.portalId());
        }
    }

    private void tickClosingEffects(MinecraftServer server, long gameTime) {
        CLOSING_EFFECT_STARTS.entrySet().removeIf(entry -> {
            Optional<RealmPortalData> portal = getPortal(server, entry.getKey());
            if (portal.isEmpty()) {
                return true;
            }
            if (portal.get().realmState() != RealmPortalState.CLOSING) {
                return true;
            }
            if (gameTime - entry.getValue() == 10L) {
                spawnColumnParticles(server, portal.get().destination(), ParticleTypes.SMOKE, 10);
                CrossroadDimension.LOGGER.info("Portal {} realm closing visual: portal effect fading", portal.get().portalId());
            }
            return false;
        });
    }

    private void triggerCrystalSlam(MinecraftServer server, GlobalPos pos) {
        triggerCrystalAnimation(server, pos, CrystalAnimation.SLAM);
    }

    private void triggerCrystalOpening(MinecraftServer server, GlobalPos pos) {
        triggerCrystalAnimation(server, pos, CrystalAnimation.OPENING);
    }

    private void triggerCrystalReformToIdle(MinecraftServer server, RealmPortalData portal) {
        triggerCrystalAnimation(server, portal.destination(), CrystalAnimation.REFORM_TO_IDLE);
        CrossroadDimension.LOGGER.info("Portal {} crystal animation triggered: reform -> idle", portal.portalId());
    }

    private void triggerCrystalAnimation(MinecraftServer server, GlobalPos pos, CrystalAnimation animation) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            if (level.getBlockEntity(pos.pos()) instanceof CrossroadCrystalBlockEntity crystal) {
                CrossroadDimension.LOGGER.info(
                        "Crossroads crystal animation dispatch: pos={} {} animation={} blockEntityPresent=true",
                        pos.dimension().identifier(),
                        pos.pos(),
                        animation
                );
                switch (animation) {
                    case SLAM -> crystal.playSlam();
                    case OPENING -> crystal.playOpeningSequence();
                    case REFORM_TO_IDLE -> crystal.playReformToIdle();
                }
            } else {
                CrossroadDimension.LOGGER.info(
                        "Crossroads crystal animation dispatch skipped: pos={} {} animation={} blockEntityPresent=false",
                        pos.dimension().identifier(),
                        pos.pos(),
                        animation
                );
            }
        } else {
            CrossroadDimension.LOGGER.info(
                    "Crossroads crystal animation dispatch skipped: dimension={} animation={} levelLoaded=false",
                    pos.dimension().identifier(),
                    animation
            );
        }
    }

    private void placeGateColumn(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            if (!level.getBlockState(pos.pos()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos(), CrossroadDimension.CROSSROADS_GATE.get().defaultBlockState().setValue(CrossroadsGateBlock.ANCHOR, true));
            }
            if (!level.getBlockState(pos.pos().above()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos().above(), CrossroadDimension.CROSSROADS_GATE.get().defaultBlockState().setValue(CrossroadsGateBlock.ANCHOR, false));
            }
        }
    }

    private int removeGateColumn(MinecraftServer server, GlobalPos pos) {
        int removed = 0;
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            if (level.getBlockState(pos.pos()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos(), Blocks.AIR.defaultBlockState());
                removed++;
                CrossroadDimension.LOGGER.info("Removed gate block at {} {}", pos.dimension().identifier(), pos.pos());
            }
            if (level.getBlockState(pos.pos().above()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos().above(), Blocks.AIR.defaultBlockState());
                removed++;
                CrossroadDimension.LOGGER.info("Removed gate block at {} {}", pos.dimension().identifier(), pos.pos().above());
            }
        }
        return removed;
    }

    private boolean hasGateColumn(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        return level != null && level.getBlockState(pos.pos()).is(CrossroadDimension.CROSSROADS_GATE.get());
    }

    private boolean isChunkLoaded(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        return level != null && level.hasChunkAt(pos.pos());
    }

    private void spawnColumnParticles(MinecraftServer server, GlobalPos pos, ParticleOptions particle, int count) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            level.sendParticles(particle, pos.pos().getX() + 0.5D, pos.pos().getY() + 1.0D, pos.pos().getZ() + 0.5D, count, 0.25D, 0.75D, 0.25D, 0.02D);
        }
    }

    private void placeGateLabel(MinecraftServer server, GlobalPos pos, RealmPortalData portal) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null) {
            return;
        }
        removeGateLabel(level, pos, portal);
        ArmorStand label = new ArmorStand(level, pos.pos().getX() + 0.5D, pos.pos().getY() + 1.25D, pos.pos().getZ() + 0.5D);
        label.setInvisible(true);
        label.setNoGravity(true);
        label.setInvulnerable(true);
        label.setCustomName(Component.literal(portal.ownerName() + "'s Realm"));
        label.setCustomNameVisible(true);
        label.addTag(labelTag(portal));
        level.addFreshEntity(label);
    }

    private int removeGateLabels(MinecraftServer server, RealmPortalData portal) {
        return removeGateLabel(server.getLevel(portal.originDimension()), portal.origin(), portal)
                + removeGateLabel(server.getLevel(portal.destinationDimension()), portal.destination(), portal);
    }

    private int removeGateLabel(ServerLevel level, GlobalPos pos, RealmPortalData portal) {
        if (level == null) {
            return 0;
        }

        int removed = 0;
        AABB bounds = new AABB(pos.pos()).inflate(2.0D, 4.0D, 2.0D);
        for (ArmorStand label : level.getEntitiesOfClass(ArmorStand.class, bounds, entity -> entity.entityTags().contains(labelTag(portal)))) {
            label.discard();
            removed++;
        }
        return removed;
    }

    private int removeAllGateLabels(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof ArmorStand label && hasCrossroadsLabelTag(label)) {
                    label.discard();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            CrossroadDimension.LOGGER.info("Removed {} orphaned Crossroads portal label(s)", removed);
        }
        return removed;
    }

    private void logTimeoutStatus(RealmPortalData portal, String side, long expiration, long gameTime) {
        long remaining = Math.max(0L, expiration - gameTime);
        long bucket = remaining / TIMEOUT_DEBUG_INTERVAL_TICKS;
        String key = timeoutKey(portal.portalId(), side);
        Long previousBucket = TIMEOUT_DEBUG_LOGS.put(key, bucket);
        if (previousBucket == null || previousBucket.longValue() != bucket) {
            CrossroadDimension.LOGGER.info(
                    "Portal {} {} timeout status: now={} expires={} remainingTicks={} population={} outsideState={} realmState={}",
                    portal.portalId(),
                    side,
                    gameTime,
                    expiration,
                    remaining,
                    portal.realmPopulation(),
                    portal.outsideState(),
                    portal.realmState()
            );
        }
    }

    private void clearTimeoutDebug(UUID portalId) {
        clearTimeoutDebug(portalId, "outside");
        clearTimeoutDebug(portalId, "realm");
    }

    private void clearTimeoutDebug(UUID portalId, String side) {
        TIMEOUT_DEBUG_LOGS.remove(timeoutKey(portalId, side));
    }

    private static String timeoutKey(UUID portalId, String side) {
        return portalId + ":" + side;
    }

    private static String labelTag(RealmPortalData portal) {
        return LABEL_TAG_PREFIX + portal.portalId();
    }

    private static boolean hasCrossroadsLabelTag(ArmorStand entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(LABEL_TAG_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        long x = (long) first.getX() - second.getX();
        long z = (long) first.getZ() - second.getZ();
        return Math.sqrt((double) x * x + (double) z * z);
    }

    private static boolean sameColumn(GlobalPos first, GlobalPos second) {
        return first.dimension().equals(second.dimension())
                && first.pos().getX() == second.pos().getX()
                && first.pos().getZ() == second.pos().getZ()
                && (first.pos().getY() == second.pos().getY() || first.pos().getY() + 1 == second.pos().getY());
    }

    private enum CrystalAnimation {
        OPENING,
        SLAM,
        REFORM_TO_IDLE
    }

    public record PortalDebugInfo(
            int savedPortals,
            long openingPortals,
            long openPortals,
            long closingPortals,
            long closedPortals,
            long sealedCrystals,
            int scheduledClosingEffects
    ) {
    }

    public record PortalCleanupSummary(int savedPortalsCleared, int gateBlocksRemoved, int labelsRemoved, int timersCleared) {
    }
}
