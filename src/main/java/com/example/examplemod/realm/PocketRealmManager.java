package com.example.examplemod.realm;

import com.example.examplemod.CrossroadDimension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.core.BlockPos;
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

public class PocketRealmManager {
    private static final String DIMENSION_PATH_PREFIX = "pocket_realm/";
    private static final BlockPos PLATFORM_CENTER = new BlockPos(0, 80, 0);
    private static final int PLATFORM_RADIUS = 2;
    private static final int TELEPORT_Y_OFFSET = 1;

    public Optional<PocketRealmData> findRealm(ServerPlayer player) {
        return findRealm(player.level().getServer(), player.getUUID());
    }

    public Optional<PocketRealmData> findRealm(MinecraftServer server, UUID owner) {
        return savedData(server).find(owner);
    }

    public PocketRealmData createRealmSkeleton(ServerPlayer owner) {
        CrossroadDimension.LOGGER.info("Creating pocket realm metadata for {}", owner.getGameProfile().name());
        PocketRealmData realm = PocketRealmData.uncreated(owner.getUUID(), levelKeyFor(owner.getUUID()));
        saveRealm(owner.level().getServer(), realm);
        CrossroadDimension.LOGGER.info("Pocket realm metadata uses dimension key {}", realm.levelKey().identifier());
        return realm;
    }

    public void initialize(MinecraftServer server) {
        PocketRealmSavedData data = savedData(server);
        CrossroadDimension.LOGGER.info("Pocket realm SavedData loaded with {} realm(s)", data.size());
    }

    private void saveRealm(MinecraftServer server, PocketRealmData realm) {
        PocketRealmSavedData data = savedData(server);
        data.put(realm);
        CrossroadDimension.LOGGER.info("Pocket realm saved: owner={}, dimension={}", realm.owner(), realm.levelKey().identifier());
    }

    private PocketRealmSavedData savedData(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(PocketRealmSavedData.TYPE);
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

    public BlockPos ensureSpawnPlatform(ServerLevel realmLevel) {
        CrossroadDimension.LOGGER.info("Ensuring spawn platform exists in {}", realmLevel.dimension().identifier());

        if (realmLevel.getBlockState(PLATFORM_CENTER).is(Blocks.SMOOTH_STONE)) {
            CrossroadDimension.LOGGER.info("Spawn platform already exists at {}", PLATFORM_CENTER);
            return PLATFORM_CENTER.above(TELEPORT_Y_OFFSET);
        }

        CrossroadDimension.LOGGER.info("Creating spawn platform centered at {}", PLATFORM_CENTER);
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                BlockPos platformPos = PLATFORM_CENTER.offset(x, 0, z);
                realmLevel.setBlockAndUpdate(platformPos, Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }

        BlockPos spawnPos = PLATFORM_CENTER.above(TELEPORT_Y_OFFSET);
        realmLevel.setBlockAndUpdate(spawnPos, Blocks.AIR.defaultBlockState());
        realmLevel.setBlockAndUpdate(spawnPos.above(), Blocks.AIR.defaultBlockState());
        CrossroadDimension.LOGGER.info("Spawn platform ready; arrival position is {}", spawnPos);
        return spawnPos;
    }

    public Identifier dimensionIdFor(UUID owner) {
        return Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, DIMENSION_PATH_PREFIX + owner);
    }

    public boolean isPocketRealmDimension(ResourceKey<Level> levelKey) {
        Identifier id = levelKey.identifier();
        return id.getNamespace().equals(CrossroadDimension.MODID) && id.getPath().startsWith(DIMENSION_PATH_PREFIX);
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
