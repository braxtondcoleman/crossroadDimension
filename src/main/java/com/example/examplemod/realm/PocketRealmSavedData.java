package com.example.examplemod.realm;

import com.example.examplemod.CrossroadDimension;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class PocketRealmSavedData extends SavedData {
    public static final Codec<PocketRealmSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PocketRealmData.CODEC.listOf().fieldOf("realms").forGetter(PocketRealmSavedData::realmsList)
    ).apply(instance, PocketRealmSavedData::fromList));
    public static final SavedDataType<PocketRealmSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "pocket_realms"),
            PocketRealmSavedData::new,
            CODEC
    );

    private final Map<UUID, PocketRealmData> realms = new HashMap<>();

    public static PocketRealmSavedData fromList(List<PocketRealmData> realms) {
        PocketRealmSavedData data = new PocketRealmSavedData();
        for (PocketRealmData realm : realms) {
            data.realms.put(realm.owner(), realm);
        }
        return data;
    }

    public Optional<PocketRealmData> find(UUID owner) {
        return Optional.ofNullable(realms.get(owner));
    }

    public void put(PocketRealmData realm) {
        realms.put(realm.owner(), realm);
        setDirty();
    }

    public int size() {
        return realms.size();
    }

    private List<PocketRealmData> realmsList() {
        return List.copyOf(realms.values());
    }
}
