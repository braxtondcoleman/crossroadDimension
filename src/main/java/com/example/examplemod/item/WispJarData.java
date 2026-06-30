package com.example.examplemod.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record WispJarData(String attunement, int resonance, int upgradeLevel) {
    public static final WispJarData EMPTY = new WispJarData("", 0, 0);
    public static final Codec<WispJarData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("attunement", "").forGetter(WispJarData::attunement),
            Codec.INT.optionalFieldOf("resonance", 0).forGetter(WispJarData::resonance),
            Codec.INT.optionalFieldOf("upgrade_level", 0).forGetter(WispJarData::upgradeLevel)
    ).apply(instance, WispJarData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, WispJarData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WispJarData::attunement,
            ByteBufCodecs.VAR_INT, WispJarData::resonance,
            ByteBufCodecs.VAR_INT, WispJarData::upgradeLevel,
            WispJarData::new);

    public WispJarData withAttunement(WispAttunement newAttunement, int newResonance) {
        return new WispJarData(newAttunement.id(), newResonance, this.upgradeLevel);
    }

    public WispJarData withResonance(int newResonance) {
        return new WispJarData(this.attunement, newResonance, this.upgradeLevel);
    }
}
