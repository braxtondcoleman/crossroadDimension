package com.example.examplemod.realm;

import com.example.examplemod.CrossroadDimension;
import com.example.examplemod.portal.RealmPortalData;
import com.example.examplemod.portal.RealmPortalManager;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class PocketRealmService {
    private final PocketRealmManager realmManager;
    private final RealmPortalManager portalManager = new RealmPortalManager();

    public PocketRealmService(PocketRealmManager realmManager) {
        this.realmManager = realmManager;
    }

    public PocketRealmData getOrCreateRealm(ServerPlayer player) {
        return realmManager.findRealm(player)
                .orElseGet(() -> realmManager.createRealmSkeleton(player));
    }

    public Optional<GlobalPos> getReturnLocation(ServerPlayer player) {
        return realmManager.findRealm(player)
                .flatMap(PocketRealmData::lastReturnLocation);
    }

    public Component requestTravel(ServerPlayer player) {
        PocketRealmData realm = getOrCreateRealm(player);

        if (realmManager.isPocketRealmDimension(player.level().dimension())) {
            return Component.literal("Cannot use here.");
        }

        Optional<BlockPos> selectedOrigin = selectedGateOrigin(player);
        if (selectedOrigin.isEmpty()) {
            return Component.literal("Cannot place here.");
        }

        return openGateToRealm(player, realm, selectedOrigin.get());
    }

    private Component openGateToRealm(ServerPlayer player, PocketRealmData realm, BlockPos originPos) {
        realm = realmManager.saveReturnLocation(player, realm);
        ServerLevel realmLevel = realmManager.ensureRealmLoaded(player.level().getServer(), realm);
        BlockPos spawnPos = realmManager.ensureSpawnPlatform(realmLevel);
        Optional<RealmPortalData> portal = portalManager.createActiveGate(
                player,
                GlobalPos.of(player.level().dimension(), originPos),
                GlobalPos.of(realmLevel.dimension(), spawnPos)
        );

        return portal
                .map(data -> Component.literal("Crossroads Gate opened. Step inside to enter your realm."))
                .orElseGet(() -> Component.literal("Crossroads Gate could not be opened."));
    }

    private Optional<BlockPos> selectedGateOrigin(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, true);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }

        if (blockHit.getDirection() != Direction.UP) {
            return Optional.empty();
        }

        BlockPos surface = blockHit.getBlockPos();
        if (player.level().getFluidState(surface).is(FluidTags.WATER)
                || player.level().getFluidState(surface).is(FluidTags.LAVA)) {
            return Optional.empty();
        }

        return Optional.of(surface.above());
    }

    private Component returnFromRealm(ServerPlayer player, PocketRealmData realm) {
        Optional<GlobalPos> returnLocation = realm.lastReturnLocation();
        if (returnLocation.isEmpty()) {
            CrossroadDimension.LOGGER.info("No saved return location for {}", player.getGameProfile().name());
            return Component.literal("No saved return location for this pocket realm.");
        }

        CrossroadDimension.LOGGER.info(
                "Return location restored for {}: {} at {}",
                player.getGameProfile().name(),
                returnLocation.get().dimension().identifier(),
                returnLocation.get().pos()
        );
        ServerLevel returnLevel = realmManager.resolveReturnLevel(player.level().getServer(), realm).orElse(null);
        if (returnLevel == null) {
            return Component.literal("Saved return dimension is not loaded: " + returnLocation.get().dimension().identifier());
        }

        realmManager.teleportTo(player, returnLevel, returnLocation.get().pos());
        return Component.literal("Returned to: " + returnLevel.dimension().identifier());
    }
}
