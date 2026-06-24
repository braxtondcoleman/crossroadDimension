package com.example.examplemod.realm;

import com.example.examplemod.CrossroadDimension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public class PocketRealmManager {
    private static final String DIMENSION_PATH_PREFIX = "pocket_realm/";
    private final Map<UUID, PocketRealmData> knownRealms = new HashMap<>();

    public Optional<PocketRealmData> findRealm(ServerPlayer player) {
        return findRealm(player.getUUID());
    }

    public Optional<PocketRealmData> findRealm(UUID owner) {
        return Optional.ofNullable(knownRealms.get(owner));
    }

    public PocketRealmData createRealmSkeleton(ServerPlayer owner) {
        CrossroadDimension.LOGGER.info("Creating pocket realm metadata for {}", owner.getGameProfile().name());
        PocketRealmData realm = PocketRealmData.uncreated(owner.getUUID(), levelKeyFor(owner.getUUID()));
        knownRealms.put(owner.getUUID(), realm);
        CrossroadDimension.LOGGER.info("Pocket realm metadata uses dimension key {}", realm.levelKey().identifier());
        return realm;
    }

    public ServerLevel ensureRealmLoaded(MinecraftServer server, PocketRealmData realm) {
        CrossroadDimension.LOGGER.info("Ensuring pocket realm is loaded: {}", realm.levelKey().identifier());

        ServerLevel existingLevel = server.getLevel(realm.levelKey());
        if (existingLevel != null) {
            CrossroadDimension.LOGGER.info("Pocket realm already loaded: {}", realm.levelKey().identifier());
            return existingLevel;
        }

        CrossroadDimension.LOGGER.info("Pocket realm not loaded; requesting runtime dimension from Infiniverse: {}", realm.levelKey().identifier());
        ServerLevel createdLevel = InfiniverseAPI.get().getOrCreateLevel(server, realm.levelKey(), () -> createVoidLevelStem(server));
        CrossroadDimension.LOGGER.info("Infiniverse returned runtime dimension: {}", createdLevel.dimension().identifier());
        return createdLevel;
    }

    public Identifier dimensionIdFor(UUID owner) {
        return Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, DIMENSION_PATH_PREFIX + owner);
    }

    public ResourceKey<Level> levelKeyFor(UUID owner) {
        return ResourceKey.create(Registries.DIMENSION, dimensionIdFor(owner));
    }

    private LevelStem createVoidLevelStem(MinecraftServer server) {
        CrossroadDimension.LOGGER.info("Creating LevelStem for a flat void pocket realm");

        Holder<DimensionType> dimensionType = server.registryAccess()
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        Holder<Biome> biome = server.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getOrThrow(Biomes.THE_VOID);

        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                Optional.empty(),
                biome,
                List.<Holder<PlacedFeature>>of()
        );
        settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        settings.updateLayers();

        return new LevelStem(dimensionType, new FlatLevelSource(settings));
    }
}
