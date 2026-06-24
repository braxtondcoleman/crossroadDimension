package com.example.examplemod.portal;

import com.mojang.serialization.Codec;

public enum RealmPortalState {
    OPEN,
    CLOSING,
    CLOSED;

    public static final Codec<RealmPortalState> CODEC = Codec.STRING.xmap(RealmPortalState::valueOf, RealmPortalState::name);
}
