package com.example.examplemod.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

public record RealmCrystalData(
        UUID owner,
        String ownerName,
        UUID crystalId,
        GlobalPos outsideAnchor,
        GlobalPos realmAnchor,
        long createdGameTime,
        int visits
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<RealmCrystalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("owner").forGetter(RealmCrystalData::owner),
            Codec.STRING.optionalFieldOf("owner_name", "Unknown").forGetter(RealmCrystalData::ownerName),
            Codec.STRING.optionalFieldOf("realm_name").forGetter(data -> Optional.empty()),
            UUID_CODEC.optionalFieldOf("crystal_id").forGetter(data -> Optional.of(data.crystalId())),
            UUID_CODEC.optionalFieldOf("portal_id").forGetter(data -> Optional.empty()),
            GlobalPos.CODEC.fieldOf("origin").forGetter(RealmCrystalData::outsideAnchor),
            GlobalPos.CODEC.fieldOf("destination").forGetter(RealmCrystalData::realmAnchor),
            Codec.LONG.optionalFieldOf("created_game_time", 0L).forGetter(RealmCrystalData::createdGameTime),
            Codec.INT.optionalFieldOf("realm_visits", 0).forGetter(RealmCrystalData::visits)
    ).apply(instance, RealmCrystalData::fromCodec));

    public static RealmCrystalData create(UUID owner, String ownerName, UUID crystalId, GlobalPos outsideAnchor, GlobalPos realmAnchor, long createdGameTime) {
        return new RealmCrystalData(owner, ownerName, crystalId, outsideAnchor, realmAnchor, createdGameTime, 0);
    }

    private static RealmCrystalData fromCodec(
            UUID owner,
            String ownerName,
            Optional<String> ignoredLegacyRealmName,
            Optional<UUID> crystalId,
            Optional<UUID> legacyPortalId,
            GlobalPos outsideAnchor,
            GlobalPos realmAnchor,
            long createdGameTime,
            int visits
    ) {
        UUID id = crystalId.or(() -> legacyPortalId).orElseGet(UUID::randomUUID);
        return new RealmCrystalData(owner, ownerName, id, outsideAnchor, realmAnchor, createdGameTime, visits);
    }

    public RealmCrystalData withOutsideAnchor(GlobalPos outsideAnchor) {
        return new RealmCrystalData(owner, ownerName, crystalId, outsideAnchor, realmAnchor, createdGameTime, visits);
    }

    public RealmCrystalData withVisitRecorded() {
        return new RealmCrystalData(owner, ownerName, crystalId, outsideAnchor, realmAnchor, createdGameTime, visits + 1);
    }

    public boolean isOutsideAnchor(GlobalPos pos) {
        return sameColumn(outsideAnchor, pos);
    }

    public boolean isRealmAnchor(GlobalPos pos) {
        return sameColumn(realmAnchor, pos);
    }

    public boolean isAnchor(GlobalPos pos) {
        return isOutsideAnchor(pos) || isRealmAnchor(pos);
    }

    private static boolean sameColumn(GlobalPos first, GlobalPos second) {
        return first.dimension().equals(second.dimension())
                && first.pos().getX() == second.pos().getX()
                && first.pos().getZ() == second.pos().getZ()
                && (first.pos().getY() == second.pos().getY() || first.pos().getY() + 1 == second.pos().getY());
    }
}
