package com.example.examplemod.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class SurveyWispParticle extends SingleQuadParticle {
    private SurveyWispParticle(ClientLevel level, double x, double y, double z,
            float size, float opacity, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.quadSize = Mth.clamp(size, 0.02F, 0.24F);
        this.alpha = Mth.clamp(opacity, 0.0F, 1.0F);
        this.rCol = 0.92F;
        this.gCol = 0.97F;
        this.bCol = 1.0F;
        this.lifetime = 1;
        this.hasPhysics = false;
        this.gravity = 0.0F;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double size, double opacity, double ignored, RandomSource random) {
            return new SurveyWispParticle(level, x, y, z, (float) size, (float) opacity, this.sprites);
        }
    }
}
