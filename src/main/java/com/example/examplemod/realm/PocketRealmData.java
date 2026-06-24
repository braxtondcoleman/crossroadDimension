package com.example.examplemod.realm;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PocketRealmData(
        UUID owner,
        ResourceKey<Level> levelKey,
        Optional<GlobalPos> lastReturnLocation
) {
    public Identifier dimensionId() {
        return levelKey.identifier();
    }

    public static PocketRealmData uncreated(UUID owner, ResourceKey<Level> levelKey) {
        return new PocketRealmData(owner, levelKey, Optional.empty());
    }
}
