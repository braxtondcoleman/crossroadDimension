package com.example.examplemod.portal;

import com.example.examplemod.CrossroadDimension;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class RealmCrystalSavedData extends SavedData {
    public static final Codec<RealmCrystalSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RealmCrystalData.CODEC.listOf().fieldOf("portals").forGetter(RealmCrystalSavedData::crystalsList)
    ).apply(instance, RealmCrystalSavedData::fromList));
    public static final SavedDataType<RealmCrystalSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "realm_portals"),
            RealmCrystalSavedData::new,
            CODEC
    );

    private final Map<UUID, RealmCrystalData> crystals = new HashMap<>();

    public static RealmCrystalSavedData fromList(List<RealmCrystalData> crystals) {
        RealmCrystalSavedData data = new RealmCrystalSavedData();
        for (RealmCrystalData crystal : crystals) {
            data.crystals.put(crystal.crystalId(), crystal);
        }
        return data;
    }

    public Optional<RealmCrystalData> find(UUID crystalId) {
        return Optional.ofNullable(crystals.get(crystalId));
    }

    public Optional<RealmCrystalData> findForOwner(UUID owner) {
        return crystals.values().stream()
                .filter(crystal -> crystal.owner().equals(owner))
                .findFirst();
    }

    public Optional<RealmCrystalData> findAt(GlobalPos position) {
        return crystals.values().stream()
                .filter(crystal -> crystal.isAnchor(position))
                .findFirst();
    }

    public List<RealmCrystalData> all() {
        return crystalsList();
    }

    public void put(RealmCrystalData crystal) {
        crystals.put(crystal.crystalId(), crystal);
        setDirty();
    }

    public void clear() {
        crystals.clear();
        setDirty();
    }

    public int size() {
        return crystals.size();
    }

    private List<RealmCrystalData> crystalsList() {
        return List.copyOf(crystals.values());
    }
}
