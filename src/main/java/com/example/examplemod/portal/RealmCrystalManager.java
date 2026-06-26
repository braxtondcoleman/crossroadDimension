package com.example.examplemod.portal;

import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.network.HudNotifications;
import com.example.examplemod.network.OpenCrystalMenuPayload;
import com.example.examplemod.realm.PocketRealmData;
import com.example.examplemod.realm.PocketRealmManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("deprecation")
public class RealmCrystalManager {
    public static final int MIN_CRYSTAL_SPACING_RADIUS = 16;
    private static final double CREATURE_BRING_RADIUS = 3.0D;
    private static final long PENDING_TRAVEL_TIMEOUT_TICKS = 200L;
    private static final Map<String, PendingTravel> PENDING_TRAVELS = new HashMap<>();
    private static final PocketRealmManager REALM_MANAGER = new PocketRealmManager();

    public void initialize(MinecraftServer server) {
        RealmCrystalSavedData data = savedData(server);
        CrossroadDimension.LOGGER.info("Realm crystal SavedData loaded with {} crystal anchor(s)", data.size());
        for (RealmCrystalData crystal : data.all()) {
            CrossroadDimension.LOGGER.info(
                    "Loaded realm crystal {} owner={} displayName='{}' outside={} realm={} created={} visits={}",
                    crystal.crystalId(),
                    crystal.owner(),
                    displayName(crystal),
                    endpoint(crystal.outsideAnchor()),
                    endpoint(crystal.realmAnchor()),
                    crystal.createdGameTime(),
                    crystal.visits()
            );
        }
    }

    public Optional<RealmCrystalData> findAt(MinecraftServer server, GlobalPos pos) {
        return savedData(server).findAt(pos);
    }

    public Optional<RealmCrystalData> createOrMoveCrystal(ServerPlayer owner, GlobalPos outsideAnchor, GlobalPos realmAnchor) {
        MinecraftServer server = owner.level().getServer();
        RealmCrystalSavedData data = savedData(server);
        Optional<RealmCrystalData> existing = data.findForOwner(owner.getUUID());

        Component failure = validateCrystalPlacement(server, outsideAnchor, owner.getUUID());
        if (failure != null) {
            notify(owner, failure.getString());
            CrossroadDimension.LOGGER.info("Crystal placement failed for {}: {}", owner.getGameProfile().name(), failure.getString());
            return Optional.empty();
        }

        if (existing.isPresent()) {
            RealmCrystalData oldCrystal = existing.get();
            if (hasActivePendingTravel(server, oldCrystal)) {
                notify(owner, "Crossroads Crystal is already active.");
                CrossroadDimension.LOGGER.info("Crystal relocation rejected for {}; crystal {} has an active travel", owner.getUUID(), oldCrystal.crystalId());
                return existing;
            }

            if (oldCrystal.isOutsideAnchor(outsideAnchor)) {
                notify(owner, "Crossroads Crystal is already anchored here.");
                return existing;
            }

            placeCrystalColumn(server, outsideAnchor);
            RealmCrystalData moved = oldCrystal.withOutsideAnchor(outsideAnchor);
            data.put(moved);
            removeCrystalColumn(server, oldCrystal.outsideAnchor());
            placeCrystalColumn(server, moved.realmAnchor());
            CrossroadDimension.LOGGER.info(
                    "Moved realm crystal {} for owner {} from {} to {}; realm anchor remains {}",
                    moved.crystalId(),
                    owner.getUUID(),
                    endpoint(oldCrystal.outsideAnchor()),
                    endpoint(moved.outsideAnchor()),
                    endpoint(moved.realmAnchor())
            );
            notify(owner, "Crossroads Crystal moved.");
            return Optional.of(moved);
        }

        placeCrystalColumn(server, outsideAnchor);
        placeCrystalColumn(server, realmAnchor);
        RealmCrystalData crystal = RealmCrystalData.create(
                owner.getUUID(),
                owner.getGameProfile().name(),
                UUID.randomUUID(),
                outsideAnchor,
                realmAnchor,
                server.overworld().getGameTime()
        );
        data.put(crystal);
        CrossroadDimension.LOGGER.info(
                "Created permanent realm crystal {} for owner {} outside={} realm={}",
                crystal.crystalId(),
                owner.getUUID(),
                endpoint(crystal.outsideAnchor()),
                endpoint(crystal.realmAnchor())
        );
        notify(owner, "Crossroads Crystal anchored.");
        return Optional.of(crystal);
    }

    public void openMenu(ServerPlayer player, GlobalPos anchorPos) {
        Optional<RealmCrystalData> crystal = findAt(player.level().getServer(), anchorPos);
        if (crystal.isEmpty()) {
            notify(player, "This crystal is not linked to a realm.");
            return;
        }

        RealmCrystalData data = crystal.get();
        boolean owner = data.owner().equals(player.getUUID());
        PacketDistributor.sendToPlayer(player, new OpenCrystalMenuPayload(
                anchorPos.pos(),
                owner,
                data.ownerName(),
                data.isRealmAnchor(anchorPos)
        ));
    }

    public void handleMenuAction(ServerPlayer player, BlockPos anchorPos, String action) {
        if (!canUseAnchor(player, anchorPos)) {
            notify(player, "Move closer to the crystal.");
            CrossroadDimension.LOGGER.info("Rejected crystal menu action '{}' from {}; anchor {} is not usable", action, player.getUUID(), anchorPos);
            return;
        }

        GlobalPos pos = GlobalPos.of(player.level().dimension(), anchorPos);
        Optional<RealmCrystalData> crystal = findAt(player.level().getServer(), pos);
        if (crystal.isEmpty()) {
            notify(player, "This crystal is not linked to a realm.");
            return;
        }

        RealmCrystalData data = crystal.get();
        boolean isOwner = data.owner().equals(player.getUUID());
        switch (action) {
            case "enter" -> beginPlayerTravel(player, data, pos);
            case "bring_nearby" -> {
                if (isOwner) {
                    beginGroupTravel(player, data, pos);
                }
            }
            default -> CrossroadDimension.LOGGER.info("Ignoring unknown crystal menu action '{}'", action);
        }
    }

    public void handleCrystalSlamComplete(MinecraftServer server, GlobalPos source) {
        PendingTravel travel = PENDING_TRAVELS.remove(anchorKey(source));
        if (travel == null) {
            CrossroadDimension.LOGGER.info("Crystal slam completed at {} with no pending travel", endpoint(source));
            return;
        }

        Entity traveler = findEntity(server, travel.travelerId()).orElse(null);
        if (traveler == null || traveler.isRemoved()) {
            CrossroadDimension.LOGGER.info("Pending crystal travel at {} cancelled; player missing", endpoint(source));
            return;
        }

        RealmCrystalData crystal = savedData(server).find(travel.crystalId()).orElse(null);
        ServerLevel targetLevel = crystal == null
                ? server.getLevel(travel.destination().dimension())
                : resolveDestinationLevel(server, crystal, travel.destination());
        if (targetLevel == null) {
            CrossroadDimension.LOGGER.info("Pending crystal travel at {} cancelled; destination not loaded: {}", endpoint(source), travel.destination().dimension().identifier());
            return;
        }

        BlockPos targetPos = travel.destination().pos();
        traveler.teleportTo(targetLevel, targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, java.util.Set.of(), traveler.getYRot(), traveler.getXRot(), true);
        int companionsMoved = 0;
        for (UUID companionId : travel.companionIds()) {
            Entity companion = findEntity(server, companionId).orElse(null);
            if (companion != null && !companion.isRemoved()) {
                companion.teleportTo(targetLevel, targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, java.util.Set.of(), companion.getYRot(), companion.getXRot(), true);
                companionsMoved++;
            }
        }
        triggerCrystalReform(server, travel.destination());
        if (traveler instanceof ServerPlayer player) {
            notify(player, "Crossroads travel complete.");
        }
        CrossroadDimension.LOGGER.info(
                "Crystal travel completed from {} to {} for traveler {} with {} companion(s)",
                endpoint(source),
                endpoint(travel.destination()),
                traveler.getUUID(),
                companionsMoved
        );
    }

    private void beginPlayerTravel(ServerPlayer player, RealmCrystalData crystal, GlobalPos source) {
        GlobalPos destination = crystal.isRealmAnchor(source) ? crystal.outsideAnchor() : crystal.realmAnchor();
        beginTravel(player, crystal, source, destination, List.of());
    }

    private void beginGroupTravel(ServerPlayer player, RealmCrystalData crystal, GlobalPos source) {
        GlobalPos destination = crystal.isRealmAnchor(source) ? crystal.outsideAnchor() : crystal.realmAnchor();
        if (resolveDestinationLevel(player.level().getServer(), crystal, destination) == null || !(player.level() instanceof ServerLevel sourceLevel)) {
            notify(player, "Crossroads destination is not loaded.");
            return;
        }

        AABB bounds = new AABB(source.pos()).inflate(CREATURE_BRING_RADIUS);
        List<UUID> companions = new ArrayList<>();
        for (Entity entity : sourceLevel.getEntities((Entity) null, bounds, entity -> isBringableEntity(player, entity))) {
            companions.add(entity.getUUID());
        }
        beginTravel(player, crystal, source, destination, companions);
        CrossroadDimension.LOGGER.info("Owner {} started group travel through crystal {} with {} companion(s)", player.getUUID(), crystal.crystalId(), companions.size());
    }

    private void beginTravel(ServerPlayer player, RealmCrystalData crystal, GlobalPos source, GlobalPos destination, List<UUID> companions) {
        MinecraftServer server = player.level().getServer();
        if (hasActivePendingTravel(server, crystal)) {
            notify(player, "Crossroads Crystal is already active.");
            CrossroadDimension.LOGGER.info("Crystal travel rejected for {}; crystal {} already has an active pending travel", player.getUUID(), crystal.crystalId());
            return;
        }

        if (resolveDestinationLevel(server, crystal, destination) == null) {
            notify(player, "Crossroads destination is not loaded.");
            CrossroadDimension.LOGGER.info("Crystal travel rejected for {}; destination not loaded: {}", player.getUUID(), destination.dimension().identifier());
            return;
        }

        RealmCrystalData updated = crystal.withVisitRecorded();
        savedData(server).put(updated);
        PENDING_TRAVELS.put(anchorKey(source), new PendingTravel(player.getUUID(), List.copyOf(companions), destination, crystal.crystalId(), server.overworld().getGameTime()));
        triggerCrystalSlam(server, source);
        CrossroadDimension.LOGGER.info(
                "Crystal travel started for {} through crystal {} from {} to {} with {} companion(s)",
                player.getGameProfile().name(),
                crystal.crystalId(),
                endpoint(source),
                endpoint(destination),
                companions.size()
        );
    }

    private boolean isBringableEntity(ServerPlayer player, Entity entity) {
        if (entity == player || entity instanceof ServerPlayer || entity.isRemoved() || hasInternalCrossroadsTag(entity)) {
            return false;
        }
        return true;
    }

    private boolean canUseAnchor(ServerPlayer player, BlockPos anchorPos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        BlockState state = level.getBlockState(anchorPos);
        if (!state.is(CrossroadDimension.CROSSROADS_CRYSTAL.get()) || !state.getValue(CrossroadsCrystalBlock.ANCHOR)) {
            return false;
        }

        double range = player.blockInteractionRange() + 1.0D;
        return player.distanceToSqr(anchorPos.getX() + 0.5D, anchorPos.getY() + 0.5D, anchorPos.getZ() + 0.5D) <= range * range;
    }

    private boolean hasInternalCrossroadsTag(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(CrossroadDimension.MODID + ".portal_label")) {
                return true;
            }
        }
        return false;
    }

    private Component validateCrystalPlacement(MinecraftServer server, GlobalPos origin, UUID owner) {
        ServerLevel level = server.getLevel(origin.dimension());
        if (level == null) {
            return Component.literal("Cannot place here.");
        }

        if (!level.getBlockState(origin.pos()).isAir()
                && !level.getBlockState(origin.pos()).is(CrossroadDimension.CROSSROADS_CRYSTAL.get())) {
            return Component.literal("Cannot place here.");
        }
        if (!level.getBlockState(origin.pos().above()).isAir()
                && !level.getBlockState(origin.pos().above()).is(CrossroadDimension.CROSSROADS_CRYSTAL.get())) {
            return Component.literal("Cannot place here.");
        }

        for (RealmCrystalData crystal : savedData(server).all()) {
            if (!crystal.owner().equals(owner)
                    && crystal.outsideAnchor().dimension().equals(origin.dimension())
                    && horizontalDistance(crystal.outsideAnchor().pos(), origin.pos()) < MIN_CRYSTAL_SPACING_RADIUS) {
                return Component.literal("Cannot place crystal: another realm anchor is too close.");
            }
        }

        return null;
    }

    private void placeCrystalColumn(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            if (!level.getBlockState(pos.pos()).is(CrossroadDimension.CROSSROADS_CRYSTAL.get())) {
                level.setBlockAndUpdate(pos.pos(), CrossroadDimension.CROSSROADS_CRYSTAL.get().defaultBlockState().setValue(CrossroadsCrystalBlock.ANCHOR, true));
            }
            if (!level.getBlockState(pos.pos().above()).is(CrossroadDimension.CROSSROADS_CRYSTAL.get())) {
                level.setBlockAndUpdate(pos.pos().above(), CrossroadDimension.CROSSROADS_CRYSTAL.get().defaultBlockState().setValue(CrossroadsCrystalBlock.ANCHOR, false));
            }
        }
    }

    private void removeCrystalColumn(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null) {
            if (level.getBlockState(pos.pos()).is(CrossroadDimension.CROSSROADS_CRYSTAL.get())) {
                level.setBlockAndUpdate(pos.pos(), Blocks.AIR.defaultBlockState());
            }
            if (level.getBlockState(pos.pos().above()).is(CrossroadDimension.CROSSROADS_CRYSTAL.get())) {
                level.setBlockAndUpdate(pos.pos().above(), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private void triggerCrystalSlam(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null && level.getBlockEntity(pos.pos()) instanceof CrossroadCrystalBlockEntity crystal) {
            crystal.playSlam();
        }
    }

    private void triggerCrystalReform(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level != null && level.getBlockEntity(pos.pos()) instanceof CrossroadCrystalBlockEntity crystal) {
            crystal.playReformToIdle();
        }
    }

    private ServerLevel resolveDestinationLevel(MinecraftServer server, RealmCrystalData crystal, GlobalPos destination) {
        ServerLevel level = server.getLevel(destination.dimension());
        if (level != null) {
            return level;
        }

        Optional<PocketRealmData> realm = REALM_MANAGER.findRealm(server, crystal.owner());
        if (realm.isPresent() && realm.get().levelKey().equals(destination.dimension())) {
            return REALM_MANAGER.ensureRealmLoaded(server, realm.get());
        }

        return null;
    }

    private Optional<Entity> findEntity(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    private RealmCrystalSavedData savedData(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(RealmCrystalSavedData.TYPE);
    }

    private static void notify(ServerPlayer player, String message) {
        HudNotifications.send(player, message);
    }

    private static String anchorKey(GlobalPos pos) {
        return pos.dimension().identifier() + ":" + pos.pos().getX() + "," + pos.pos().getY() + "," + pos.pos().getZ();
    }

    private static String endpoint(GlobalPos pos) {
        return pos.dimension().identifier() + " " + pos.pos();
    }

    private static String displayName(RealmCrystalData crystal) {
        return crystal.ownerName() + "'s Realm";
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        long x = (long) first.getX() - second.getX();
        long z = (long) first.getZ() - second.getZ();
        return Math.sqrt((double) x * x + (double) z * z);
    }

    private boolean hasActivePendingTravel(MinecraftServer server, RealmCrystalData crystal) {
        clearStalePendingTravel(server, crystal.outsideAnchor());
        clearStalePendingTravel(server, crystal.realmAnchor());
        return PENDING_TRAVELS.containsKey(anchorKey(crystal.outsideAnchor()))
                || PENDING_TRAVELS.containsKey(anchorKey(crystal.realmAnchor()));
    }

    private void clearStalePendingTravel(MinecraftServer server, GlobalPos source) {
        String key = anchorKey(source);
        PendingTravel pending = PENDING_TRAVELS.get(key);
        if (pending == null) {
            return;
        }

        long age = server.overworld().getGameTime() - pending.startedGameTime();
        if (age > PENDING_TRAVEL_TIMEOUT_TICKS) {
            PENDING_TRAVELS.remove(key);
            CrossroadDimension.LOGGER.info("Cleared stale pending crystal travel at {} after {} tick(s)", endpoint(source), age);
        }
    }

    private record PendingTravel(UUID travelerId, List<UUID> companionIds, GlobalPos destination, UUID crystalId, long startedGameTime) {
    }
}
