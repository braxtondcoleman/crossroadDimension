package com.example.examplemod.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PocketRealmData(
        UUID owner,
        ResourceKey<Level> levelKey,
        Optional<GlobalPos> lastReturnLocation
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<PocketRealmData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("owner").forGetter(PocketRealmData::owner),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("level_key").forGetter(PocketRealmData::levelKey),
            GlobalPos.CODEC.optionalFieldOf("last_return_location").forGetter(PocketRealmData::lastReturnLocation)
    ).apply(instance, PocketRealmData::new));

    public Identifier dimensionId() {
        return levelKey.identifier();
    }

    public PocketRealmData withReturnLocation(GlobalPos returnLocation) {
        return new PocketRealmData(owner, levelKey, Optional.of(returnLocation));
    }

    public static PocketRealmData uncreated(UUID owner, ResourceKey<Level> levelKey) {
        return new PocketRealmData(owner, levelKey, Optional.empty());
    }
}
