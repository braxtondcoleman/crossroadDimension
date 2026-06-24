package com.example.examplemod.realm;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class PocketRealmService {
    private final PocketRealmManager realmManager;

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

    public ServerLevel requestTravelToRealm(ServerPlayer player) {
        PocketRealmData realm = getOrCreateRealm(player);
        return realmManager.ensureRealmLoaded(player.level().getServer(), realm);
    }

    public void requestReturnFromRealm(ServerPlayer player) {
    }
}
