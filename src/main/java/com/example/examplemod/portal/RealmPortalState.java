package com.example.examplemod.portal;

import com.mojang.serialization.Codec;

public enum RealmPortalState {
    OPENING,
    OPEN,
    CLOSING,
    CLOSED,
    SEALED;

    public static final Codec<RealmPortalState> CODEC = Codec.STRING.xmap(RealmPortalState::valueOf, RealmPortalState::name);
}
