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
        String ownerName,
        UUID portalId,
        ResourceKey<Level> originDimension,
        BlockPos originPosition,
        ResourceKey<Level> destinationDimension,
        BlockPos destinationPosition,
        RealmPortalState outsideState,
        RealmPortalState realmState,
        long createdGameTime,
        OptionalLong outsideExpirationGameTime,
        OptionalLong realmExpirationGameTime,
        int realmPopulation,
        int portalPrimaryColor,
        int portalSecondaryColor
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<RealmPortalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("owner").forGetter(RealmPortalData::owner),
            Codec.STRING.optionalFieldOf("owner_name", "Unknown").forGetter(RealmPortalData::ownerName),
            UUID_CODEC.fieldOf("portal_id").forGetter(RealmPortalData::portalId),
            GlobalPos.CODEC.fieldOf("origin").forGetter(RealmPortalData::origin),
            GlobalPos.CODEC.fieldOf("destination").forGetter(RealmPortalData::destination),
            RealmPortalState.CODEC.optionalFieldOf("state").forGetter(RealmPortalData::legacyStateAsOptional),
            RealmPortalState.CODEC.optionalFieldOf("outside_state").forGetter(portal -> Optional.of(portal.outsideState())),
            RealmPortalState.CODEC.optionalFieldOf("realm_state").forGetter(portal -> Optional.of(portal.realmState())),
            Codec.LONG.optionalFieldOf("created_game_time", 0L).forGetter(RealmPortalData::createdGameTime),
            Codec.LONG.optionalFieldOf("expiration_game_time").forGetter(RealmPortalData::legacyExpirationGameTimeAsOptional),
            Codec.LONG.optionalFieldOf("outside_expiration_game_time").forGetter(RealmPortalData::outsideExpirationGameTimeAsOptional),
            Codec.LONG.optionalFieldOf("realm_expiration_game_time").forGetter(RealmPortalData::realmExpirationGameTimeAsOptional),
            Codec.INT.optionalFieldOf("realm_population", 0).forGetter(RealmPortalData::realmPopulation),
            Codec.INT.optionalFieldOf("portal_primary_color", 0x7A4DFF).forGetter(RealmPortalData::portalPrimaryColor),
            Codec.INT.optionalFieldOf("portal_secondary_color", 0xE4C36A).forGetter(RealmPortalData::portalSecondaryColor)
    ).apply(instance, RealmPortalData::fromCodec));

    public static RealmPortalData create(
            UUID owner,
            String ownerName,
            UUID portalId,
            GlobalPos origin,
            GlobalPos destination,
            long createdGameTime,
            OptionalLong expirationGameTime
    ) {
        return fromGlobalPositions(
                owner,
                ownerName,
                portalId,
                origin,
                destination,
                RealmPortalState.OPENING,
                RealmPortalState.OPENING,
                createdGameTime,
                optionalLongToOptional(expirationGameTime),
                Optional.empty(),
                0,
                0x7A4DFF,
                0xE4C36A
        );
    }

    private static RealmPortalData fromCodec(
            UUID owner,
            String ownerName,
            UUID portalId,
            GlobalPos origin,
            GlobalPos destination,
            Optional<RealmPortalState> legacyState,
            Optional<RealmPortalState> outsideState,
            Optional<RealmPortalState> realmState,
            long createdGameTime,
            Optional<Long> legacyExpirationGameTime,
            Optional<Long> outsideExpirationGameTime,
            Optional<Long> realmExpirationGameTime,
            int realmPopulation,
            int portalPrimaryColor,
            int portalSecondaryColor
    ) {
        RealmPortalState fallbackOutside = legacyState.orElse(RealmPortalState.OPEN);
        RealmPortalState fallbackRealm = switch (fallbackOutside) {
            case CLOSED -> RealmPortalState.SEALED;
            case CLOSING -> RealmPortalState.CLOSING;
            case OPENING -> RealmPortalState.OPENING;
            case SEALED -> RealmPortalState.SEALED;
            case OPEN -> RealmPortalState.OPEN;
        };
        return fromGlobalPositions(
                owner,
                ownerName,
                portalId,
                origin,
                destination,
                outsideState.orElse(fallbackOutside),
                realmState.orElse(fallbackRealm),
                createdGameTime,
                outsideExpirationGameTime.or(() -> legacyExpirationGameTime),
                realmExpirationGameTime,
                realmPopulation,
                portalPrimaryColor,
                portalSecondaryColor
        );
    }

    private static RealmPortalData fromGlobalPositions(
            UUID owner,
            String ownerName,
            UUID portalId,
            GlobalPos origin,
            GlobalPos destination,
            RealmPortalState outsideState,
            RealmPortalState realmState,
            long createdGameTime,
            Optional<Long> outsideExpirationGameTime,
            Optional<Long> realmExpirationGameTime,
            int realmPopulation,
            int portalPrimaryColor,
            int portalSecondaryColor
    ) {
        return new RealmPortalData(
                owner,
                ownerName,
                portalId,
                origin.dimension(),
                origin.pos(),
                destination.dimension(),
                destination.pos(),
                outsideState,
                realmState,
                createdGameTime,
                outsideExpirationGameTime.map(OptionalLong::of).orElseGet(OptionalLong::empty),
                realmExpirationGameTime.map(OptionalLong::of).orElseGet(OptionalLong::empty),
                realmPopulation,
                portalPrimaryColor,
                portalSecondaryColor
        );
    }

    public RealmPortalState state() {
        return outsideState;
    }

    public OptionalLong expirationGameTime() {
        return outsideExpirationGameTime;
    }

    public GlobalPos origin() {
        return GlobalPos.of(originDimension, originPosition);
    }

    public GlobalPos destination() {
        return GlobalPos.of(destinationDimension, destinationPosition);
    }

    public RealmPortalData withOutsideState(RealmPortalState state, OptionalLong expirationGameTime) {
        return copy(state, realmState, expirationGameTime, realmExpirationGameTime, realmPopulation);
    }

    public RealmPortalData withRealmState(RealmPortalState state, OptionalLong expirationGameTime) {
        return copy(outsideState, state, outsideExpirationGameTime, expirationGameTime, realmPopulation);
    }

    public RealmPortalData outsideOpen() {
        return withOutsideState(RealmPortalState.OPEN, OptionalLong.empty());
    }

    public RealmPortalData realmOpen() {
        return withRealmState(RealmPortalState.OPEN, OptionalLong.empty());
    }

    public RealmPortalData outsideClosing(long expirationGameTime) {
        return withOutsideState(RealmPortalState.CLOSING, OptionalLong.of(expirationGameTime));
    }

    public RealmPortalData realmClosing(long expirationGameTime) {
        return withRealmState(RealmPortalState.CLOSING, OptionalLong.of(expirationGameTime));
    }

    public RealmPortalData outsideClosed() {
        return copy(RealmPortalState.CLOSED, realmState, OptionalLong.empty(), realmExpirationGameTime, realmPopulation);
    }

    public RealmPortalData realmSealed() {
        return copy(outsideState, RealmPortalState.SEALED, outsideExpirationGameTime, OptionalLong.empty(), realmPopulation);
    }

    public RealmPortalData closed() {
        return copy(RealmPortalState.CLOSED, RealmPortalState.SEALED, OptionalLong.empty(), OptionalLong.empty(), 0);
    }

    public RealmPortalData openWithPopulation(int population) {
        return copy(RealmPortalState.OPEN, RealmPortalState.OPEN, OptionalLong.empty(), OptionalLong.empty(), population);
    }

    public RealmPortalData withPopulation(int population) {
        return copy(outsideState, realmState, outsideExpirationGameTime, realmExpirationGameTime, population);
    }

    private RealmPortalData copy(
            RealmPortalState outsideState,
            RealmPortalState realmState,
            OptionalLong outsideExpirationGameTime,
            OptionalLong realmExpirationGameTime,
            int realmPopulation
    ) {
        return new RealmPortalData(
                owner,
                ownerName,
                portalId,
                originDimension,
                originPosition,
                destinationDimension,
                destinationPosition,
                outsideState,
                realmState,
                createdGameTime,
                outsideExpirationGameTime,
                realmExpirationGameTime,
                realmPopulation,
                portalPrimaryColor,
                portalSecondaryColor
        );
    }

    private Optional<RealmPortalState> legacyStateAsOptional() {
        return Optional.of(outsideState);
    }

    private Optional<Long> legacyExpirationGameTimeAsOptional() {
        return outsideExpirationGameTimeAsOptional();
    }

    private Optional<Long> outsideExpirationGameTimeAsOptional() {
        return outsideExpirationGameTime.isPresent() ? Optional.of(outsideExpirationGameTime.getAsLong()) : Optional.empty();
    }

    private Optional<Long> realmExpirationGameTimeAsOptional() {
        return realmExpirationGameTime.isPresent() ? Optional.of(realmExpirationGameTime.getAsLong()) : Optional.empty();
    }

    private static Optional<Long> optionalLongToOptional(OptionalLong value) {
        return value.isPresent() ? Optional.of(value.getAsLong()) : Optional.empty();
    }
}
