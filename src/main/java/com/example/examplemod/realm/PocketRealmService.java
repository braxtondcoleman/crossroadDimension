package com.example.examplemod.realm;

import com.example.examplemod.portal.RealmCrystalData;
import com.example.examplemod.portal.RealmCrystalManager;
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
    private final RealmCrystalManager crystalManager = new RealmCrystalManager();

    public PocketRealmService(PocketRealmManager realmManager) {
        this.realmManager = realmManager;
    }

    public PocketRealmData getOrCreateRealm(ServerPlayer player) {
        return realmManager.findRealm(player)
                .orElseGet(() -> realmManager.createRealmSkeleton(player));
    }

    public Component requestCrystalPlacement(ServerPlayer player) {
        PocketRealmData realm = getOrCreateRealm(player);

        if (realmManager.isPocketRealmDimension(player.level().dimension())) {
            return Component.literal("Cannot use here.");
        }

        Optional<BlockPos> selectedOrigin = selectedCrystalOrigin(player);
        if (selectedOrigin.isEmpty()) {
            return Component.literal("Cannot place here.");
        }

        return anchorCrystalToRealm(player, realm, selectedOrigin.get());
    }

    private Component anchorCrystalToRealm(ServerPlayer player, PocketRealmData realm, BlockPos originPos) {
        ServerLevel realmLevel = realmManager.ensureRealmLoaded(player.level().getServer(), realm);
        BlockPos spawnPos = realmManager.ensureSpawnPlatform(realmLevel);
        Optional<RealmCrystalData> crystal = crystalManager.createOrMoveCrystal(
                player,
                GlobalPos.of(player.level().dimension(), originPos),
                GlobalPos.of(realmLevel.dimension(), spawnPos)
        );

        return crystal
                .map(data -> Component.literal("Crossroads Crystal anchored. Right-click it to travel."))
                .orElseGet(() -> Component.literal("Crossroads Crystal could not be anchored."));
    }

    private Optional<BlockPos> selectedCrystalOrigin(ServerPlayer player) {
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
}
