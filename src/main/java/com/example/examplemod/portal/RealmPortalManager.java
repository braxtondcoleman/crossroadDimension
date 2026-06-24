package com.example.examplemod.portal;

import com.example.examplemod.CrossroadDimension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
    private static final int OPENING_STAGE_ONE_TICKS = 12;
    private static final int OPENING_STAGE_TWO_TICKS = 32;
    private static final int CLOSING_STAGE_ONE_TICKS = 10;
    private static final int CLOSING_REMOVE_TICKS = 24;
    private static final long CLOSING_EXPIRATION_TICKS = 2400L;
    private static final Map<UUID, Long> CLOSING_EFFECT_STARTS = new HashMap<>();

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

        savedData(server).findActiveForOwner(owner.getUUID()).ifPresent(oldPortal -> {
            CrossroadDimension.LOGGER.info("Relocating active gate for {}; closing old portal {}", owner.getGameProfile().name(), oldPortal.portalId());
            removeGateBlocks(server, oldPortal);
            removeGateLabels(server, oldPortal);
            savedData(server).put(oldPortal.closed());
        });

        RealmPortalData portal = createPortal(server, owner.getUUID(), owner.getGameProfile().name(), origin, destination, OptionalLong.empty());
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
                    "Portal {} state transition {} -> CLOSING; expiration game time {}",
                    portalId,
                    portal.state(),
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
        tickOpeningEffects(server, gameTime);
        tickClosingEffects(server, gameTime);
        for (RealmPortalData portal : savedData(server).all()) {
            if (portal.state() == RealmPortalState.CLOSING && portal.expirationGameTime().isPresent()) {
                CrossroadDimension.LOGGER.info(
                        "Checking portal {} expiration: now={}, expires={}, population={}",
                        portal.portalId(),
                        gameTime,
                        portal.expirationGameTime().getAsLong(),
                        portal.realmPopulation()
                );
            }

            if (portal.state() == RealmPortalState.CLOSING
                    && isPortalExpired(portal, gameTime)
                    && !CLOSING_EFFECT_STARTS.containsKey(portal.portalId())) {
                CrossroadDimension.LOGGER.info(
                        "Portal {} expired at {}; starting physical closing collapse before CLOSED",
                        portal.portalId(),
                        gameTime
                );
                setGateStage(server, portal, 1);
                spawnGateParticles(server, portal, ParticleTypes.WITCH, 18);
                CLOSING_EFFECT_STARTS.put(portal.portalId(), gameTime);
            }
        }
    }

    public void handlePlayerDisconnectedInRealm(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        savedData(server).all().stream()
                .filter(portal -> portal.state() != RealmPortalState.CLOSED)
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
                    "Loaded portal {} owner={} state={} population={} expiration={}",
                    portal.portalId(),
                    portal.owner(),
                    portal.state(),
                    portal.realmPopulation(),
                    portal.expirationGameTime().isPresent() ? portal.expirationGameTime().getAsLong() : "none"
            );
            if (portal.state() == RealmPortalState.CLOSED) {
                CrossroadDimension.LOGGER.info("Cleaning physical gate blocks for already CLOSED portal {}", portal.portalId());
                removeGateBlocks(server, portal);
            }
        }
    }

    private RealmPortalSavedData savedData(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(RealmPortalSavedData.TYPE);
    }

    private void enterRealm(MinecraftServer server, RealmPortalData portal) {
        RealmPortalData latest = getPortal(server, portal.portalId()).orElse(portal);
        int previousPopulation = latest.realmPopulation();
        int population = previousPopulation + 1;
        RealmPortalData updated = latest.openWithPopulation(population);
        savedData(server).put(updated);
        CrossroadDimension.LOGGER.info(
                "Portal {} population {} -> {}; state {} -> {}; expiration cleared",
                latest.portalId(),
                previousPopulation,
                population,
                latest.state(),
                updated.state()
        );
    }

    private void leaveRealm(MinecraftServer server, RealmPortalData portal) {
        RealmPortalData latest = getPortal(server, portal.portalId()).orElse(portal);
        int previousPopulation = latest.realmPopulation();
        int population = Math.max(0, previousPopulation - 1);
        long expirationGameTime = server.overworld().getGameTime() + CLOSING_EXPIRATION_TICKS;
        RealmPortalData updated = population == 0
                ? latest.withPopulation(0).closing(expirationGameTime)
                : latest.withPopulation(population);
        savedData(server).put(updated);
        CrossroadDimension.LOGGER.info(
                "Portal {} population {} -> {}; state {} -> {}; expiration={}",
                latest.portalId(),
                previousPopulation,
                population,
                latest.state(),
                updated.state(),
                updated.expirationGameTime().isPresent() ? updated.expirationGameTime().getAsLong() : "none"
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
            if (portal.state() != RealmPortalState.CLOSED && portal.originDimension().equals(origin.dimension())) {
                double distance = horizontalDistance(portal.originPosition(), pos);
                boolean withinRadius = distance < MIN_GATE_SPACING_RADIUS;
                CrossroadDimension.LOGGER.info(
                        "Gate radius check against portal {} owner={} state={} origin={} distance={} withinRadius={}",
                        portal.portalId(),
                        portal.owner(),
                        portal.state(),
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
            if (portal.state() != RealmPortalState.CLOSED
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

    private void placeGateBlocks(MinecraftServer server, RealmPortalData portal) {
        placeGateColumn(server, portal.origin());
        placeGateColumn(server, portal.destination());
        placeGateLabel(server, portal.origin(), portal);
        placeGateLabel(server, portal.destination(), portal);
        spawnGateParticles(server, portal, ParticleTypes.REVERSE_PORTAL, 24);
        CrossroadDimension.LOGGER.info("Placed gate blocks for portal {}", portal.portalId());
    }

    private void placeGateColumn(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            level.setBlockAndUpdate(pos.pos(), CrossroadDimension.CROSSROADS_GATE.get().defaultBlockState().setValue(CrossroadsGateBlock.STAGE, 0));
            level.setBlockAndUpdate(pos.pos().above(), CrossroadDimension.CROSSROADS_GATE.get().defaultBlockState().setValue(CrossroadsGateBlock.STAGE, 0));
        }
    }

    private void removeGateBlocks(MinecraftServer server, RealmPortalData portal) {
        CrossroadDimension.LOGGER.info("Removing physical gate blocks for portal {}", portal.portalId());
        removeGateColumn(server, portal.origin());
        removeGateColumn(server, portal.destination());
        removeGateLabels(server, portal);
    }

    private void removeGateColumn(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            if (level.getBlockState(pos.pos()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos(), Blocks.AIR.defaultBlockState());
                CrossroadDimension.LOGGER.info("Removed gate block at {} {}", pos.dimension().identifier(), pos.pos());
            } else {
                CrossroadDimension.LOGGER.info("No gate block to remove at {} {}", pos.dimension().identifier(), pos.pos());
            }
            if (level.getBlockState(pos.pos().above()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos().above(), Blocks.AIR.defaultBlockState());
                CrossroadDimension.LOGGER.info("Removed gate block at {} {}", pos.dimension().identifier(), pos.pos().above());
            } else {
                CrossroadDimension.LOGGER.info("No gate block to remove at {} {}", pos.dimension().identifier(), pos.pos().above());
            }
        } else {
            CrossroadDimension.LOGGER.info("Cannot remove gate column at {}; level is not loaded", pos.dimension().identifier());
        }
    }

    private void tickOpeningEffects(MinecraftServer server, long gameTime) {
        for (RealmPortalData portal : savedData(server).all()) {
            if (portal.state() == RealmPortalState.OPEN) {
                long age = gameTime - portal.createdGameTime();
                if (age == OPENING_STAGE_ONE_TICKS) {
                    setGateStage(server, portal, 1);
                    spawnGateParticles(server, portal, ParticleTypes.END_ROD, 12);
                    CrossroadDimension.LOGGER.info("Portal {} opening stage: widening rift", portal.portalId());
                } else if (age == OPENING_STAGE_TWO_TICKS) {
                    setGateStage(server, portal, 2);
                    spawnGateParticles(server, portal, ParticleTypes.WITCH, 16);
                    CrossroadDimension.LOGGER.info("Portal {} opening stage: stabilized gate", portal.portalId());
                }
            }
        }
    }

    private void tickClosingEffects(MinecraftServer server, long gameTime) {
        CLOSING_EFFECT_STARTS.entrySet().removeIf(entry -> {
            Optional<RealmPortalData> portal = getPortal(server, entry.getKey());
            if (portal.isEmpty()) {
                return true;
            }
            if (portal.get().state() != RealmPortalState.CLOSING) {
                CrossroadDimension.LOGGER.info(
                        "Stopping closing effect for portal {}; state is now {}",
                        portal.get().portalId(),
                        portal.get().state()
                );
                return true;
            }

            long age = gameTime - entry.getValue();
            if (age == CLOSING_STAGE_ONE_TICKS) {
                setGateStage(server, portal.get(), 0);
                spawnGateParticles(server, portal.get(), ParticleTypes.SMOKE, 10);
                CrossroadDimension.LOGGER.info("Portal {} closing stage: collapsed crack", portal.get().portalId());
            }
            if (age >= CLOSING_REMOVE_TICKS) {
                removeGateBlocks(server, portal.get());
                RealmPortalData closed = portal.get().closed();
                savedData(server).put(closed);
                CrossroadDimension.LOGGER.info(
                        "Portal {} state transition {} -> CLOSED after physical gate removal",
                        portal.get().portalId(),
                        portal.get().state()
                );
                return true;
            }
            return false;
        });
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        long x = (long) first.getX() - second.getX();
        long z = (long) first.getZ() - second.getZ();
        return Math.sqrt((double) x * x + (double) z * z);
    }

    private void setGateStage(MinecraftServer server, RealmPortalData portal, int stage) {
        setGateColumnStage(server, portal.origin(), stage);
        setGateColumnStage(server, portal.destination(), stage);
    }

    private void setGateColumnStage(MinecraftServer server, GlobalPos pos, int stage) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            if (level.getBlockState(pos.pos()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos(), level.getBlockState(pos.pos()).setValue(CrossroadsGateBlock.STAGE, stage));
            }
            if (level.getBlockState(pos.pos().above()).is(CrossroadDimension.CROSSROADS_GATE.get())) {
                level.setBlockAndUpdate(pos.pos().above(), level.getBlockState(pos.pos().above()).setValue(CrossroadsGateBlock.STAGE, stage));
            }
        }
    }

    private void spawnGateParticles(MinecraftServer server, RealmPortalData portal, net.minecraft.core.particles.ParticleOptions particle, int count) {
        spawnColumnParticles(server, portal.origin(), particle, count);
        spawnColumnParticles(server, portal.destination(), particle, count);
    }

    private void spawnColumnParticles(MinecraftServer server, GlobalPos pos, net.minecraft.core.particles.ParticleOptions particle, int count) {
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
        ArmorStand label = new ArmorStand(level, pos.pos().getX() + 0.5D, pos.pos().getY() + 2.25D, pos.pos().getZ() + 0.5D);
        label.setInvisible(true);
        label.setNoGravity(true);
        label.setInvulnerable(true);
        label.setCustomName(Component.literal(portal.ownerName() + "'s Realm"));
        label.setCustomNameVisible(true);
        label.addTag(labelTag(portal));
        level.addFreshEntity(label);
    }

    private void removeGateLabels(MinecraftServer server, RealmPortalData portal) {
        removeGateLabel(server.getLevel(portal.originDimension()), portal.origin(), portal);
        removeGateLabel(server.getLevel(portal.destinationDimension()), portal.destination(), portal);
    }

    private void removeGateLabel(ServerLevel level, GlobalPos pos, RealmPortalData portal) {
        if (level == null) {
            return;
        }

        AABB bounds = new AABB(pos.pos()).inflate(2.0D, 4.0D, 2.0D);
        for (ArmorStand label : level.getEntitiesOfClass(ArmorStand.class, bounds, entity -> entity.entityTags().contains(labelTag(portal)))) {
            label.discard();
        }
    }

    private static String labelTag(RealmPortalData portal) {
        return CrossroadDimension.MODID + ".portal_label." + portal.portalId();
    }
}
