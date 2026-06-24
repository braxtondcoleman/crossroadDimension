package com.example.examplemod.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record RealmPortalData(
        UUID owner,
        UUID portalId,
        ResourceKey<Level> originDimension,
        BlockPos originPosition,
        ResourceKey<Level> destinationDimension,
        BlockPos destinationPosition,
        RealmPortalState state,
        OptionalLong expirationGameTime,
        int realmPopulation,
        int portalPrimaryColor,
        int portalSecondaryColor
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<RealmPortalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("owner").forGetter(RealmPortalData::owner),
            UUID_CODEC.fieldOf("portal_id").forGetter(RealmPortalData::portalId),
            GlobalPos.CODEC.fieldOf("origin").forGetter(RealmPortalData::origin),
            GlobalPos.CODEC.fieldOf("destination").forGetter(RealmPortalData::destination),
            RealmPortalState.CODEC.fieldOf("state").forGetter(RealmPortalData::state),
            Codec.LONG.optionalFieldOf("expiration_game_time").forGetter(RealmPortalData::expirationGameTimeAsOptional),
            Codec.INT.optionalFieldOf("realm_population", 0).forGetter(RealmPortalData::realmPopulation),
            Codec.INT.optionalFieldOf("portal_primary_color", 0x7A4DFF).forGetter(RealmPortalData::portalPrimaryColor),
            Codec.INT.optionalFieldOf("portal_secondary_color", 0xE4C36A).forGetter(RealmPortalData::portalSecondaryColor)
    ).apply(instance, RealmPortalData::fromGlobalPositions));

    public static RealmPortalData create(
            UUID owner,
            UUID portalId,
            GlobalPos origin,
            GlobalPos destination,
            OptionalLong expirationGameTime
    ) {
        return fromGlobalPositions(
                owner,
                portalId,
                origin,
                destination,
                RealmPortalState.OPEN,
                optionalLongToOptional(expirationGameTime),
                0,
                0x7A4DFF,
                0xE4C36A
        );
    }

    private static RealmPortalData fromGlobalPositions(
            UUID owner,
            UUID portalId,
            GlobalPos origin,
            GlobalPos destination,
            RealmPortalState state,
            Optional<Long> expirationGameTime,
            int realmPopulation,
            int portalPrimaryColor,
            int portalSecondaryColor
    ) {
        return new RealmPortalData(
                owner,
                portalId,
                origin.dimension(),
                origin.pos(),
                destination.dimension(),
                destination.pos(),
                state,
                expirationGameTime.map(OptionalLong::of).orElseGet(OptionalLong::empty),
                realmPopulation,
                portalPrimaryColor,
                portalSecondaryColor
        );
    }

    private Optional<Long> expirationGameTimeAsOptional() {
        return expirationGameTime.isPresent() ? Optional.of(expirationGameTime.getAsLong()) : Optional.empty();
    }

    private static Optional<Long> optionalLongToOptional(OptionalLong value) {
        return value.isPresent() ? Optional.of(value.getAsLong()) : Optional.empty();
    }

    public GlobalPos origin() {
        return GlobalPos.of(originDimension, originPosition);
    }

    public GlobalPos destination() {
        return GlobalPos.of(destinationDimension, destinationPosition);
    }

    public RealmPortalData closing(long expirationGameTime) {
        return new RealmPortalData(
                owner,
                portalId,
                originDimension,
                originPosition,
                destinationDimension,
                destinationPosition,
                RealmPortalState.CLOSING,
                OptionalLong.of(expirationGameTime),
                realmPopulation,
                portalPrimaryColor,
                portalSecondaryColor
        );
    }

    public RealmPortalData closed() {
        return new RealmPortalData(
                owner,
                portalId,
                originDimension,
                originPosition,
                destinationDimension,
                destinationPosition,
                RealmPortalState.CLOSED,
                OptionalLong.empty(),
                0,
                portalPrimaryColor,
                portalSecondaryColor
        );
    }

    public RealmPortalData openWithPopulation(int population) {
        return new RealmPortalData(
                owner,
                portalId,
                originDimension,
                originPosition,
                destinationDimension,
                destinationPosition,
                RealmPortalState.OPEN,
                OptionalLong.empty(),
                population,
                portalPrimaryColor,
                portalSecondaryColor
        );
    }

    public RealmPortalData withPopulation(int population) {
        return new RealmPortalData(
                owner,
                portalId,
                originDimension,
                originPosition,
                destinationDimension,
                destinationPosition,
                state,
                expirationGameTime,
                population,
                portalPrimaryColor,
                portalSecondaryColor
        );
    }
}
