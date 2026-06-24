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

public class RealmPortalSavedData extends SavedData {
    public static final Codec<RealmPortalSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RealmPortalData.CODEC.listOf().fieldOf("portals").forGetter(RealmPortalSavedData::portalsList)
    ).apply(instance, RealmPortalSavedData::fromList));
    public static final SavedDataType<RealmPortalSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CrossroadDimension.MODID, "realm_portals"),
            RealmPortalSavedData::new,
            CODEC
    );

    private final Map<UUID, RealmPortalData> portals = new HashMap<>();

    public static RealmPortalSavedData fromList(List<RealmPortalData> portals) {
        RealmPortalSavedData data = new RealmPortalSavedData();
        for (RealmPortalData portal : portals) {
            data.portals.put(portal.portalId(), portal);
        }
        return data;
    }

    public Optional<RealmPortalData> find(UUID portalId) {
        return Optional.ofNullable(portals.get(portalId));
    }

    public Optional<RealmPortalData> findActiveForOwner(UUID owner) {
        return portals.values().stream()
                .filter(portal -> portal.owner().equals(owner))
                .filter(portal -> portal.state() != RealmPortalState.CLOSED)
                .findFirst();
    }

    public Optional<RealmPortalData> findAt(GlobalPos position) {
        return portals.values().stream()
                .filter(portal -> portal.state() != RealmPortalState.CLOSED)
                .filter(portal -> sameColumn(portal.origin(), position) || sameColumn(portal.destination(), position))
                .findFirst();
    }

    public List<RealmPortalData> all() {
        return portalsList();
    }

    public void put(RealmPortalData portal) {
        portals.put(portal.portalId(), portal);
        setDirty();
    }

    public int size() {
        return portals.size();
    }

    private List<RealmPortalData> portalsList() {
        return List.copyOf(portals.values());
    }

    private static boolean sameColumn(GlobalPos first, GlobalPos second) {
        return first.dimension().equals(second.dimension())
                && first.pos().getX() == second.pos().getX()
                && first.pos().getZ() == second.pos().getZ()
                && (first.pos().getY() == second.pos().getY() || first.pos().getY() + 1 == second.pos().getY());
    }
}
