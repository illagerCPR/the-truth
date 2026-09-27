package io.github.illagercpr.thetruth.client;

import io.github.illagercpr.thetruth.registry.TheTruthParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The Certus data-stream particle (M8, docs/00 §8): a small square mote in
 * the cyan/white ingestion language of AE2. It drifts upward, shrinks out of
 * existence and never collides — it is data, not matter.
 */
@OnlyIn(Dist.CLIENT)
public class DataStreamParticle extends TextureSheetParticle {

    DataStreamParticle(final ClientLevel level, final double x, final double y, final double z,
                       final double vx, final double vy, final double vz, final SpriteSet sprites) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.lifetime = this.random.nextInt(20) + 20;
        this.quadSize = 0.08F + this.random.nextFloat() * 0.05F;
        // Slow upward drift with a little lateral scatter; data "rises" back
        // to the ledger rather than falling.
        this.xd = vx * 0.4 + (this.random.nextDouble() - 0.5) * 0.01;
        this.yd = 0.05 + this.random.nextDouble() * 0.04;
        this.zd = vz * 0.4 + (this.random.nextDouble() - 0.5) * 0.01;
        this.hasPhysics = false;
        this.friction = 0.96F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        // Fade and shrink over the second half of the lifetime.
        final float ageFraction = (float) this.age / this.lifetime;
        this.alpha = 1.0F - ageFraction * ageFraction;
    }

    @OnlyIn(Dist.CLIENT)
    public record Provider(SpriteSet sprites) implements ParticleProvider<net.minecraft.core.particles.SimpleParticleType> {

        @Override
        public Particle createParticle(final net.minecraft.core.particles.SimpleParticleType type,
                                       final ClientLevel level, final double x, final double y, final double z,
                                       final double vx, final double vy, final double vz) {
            return new DataStreamParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
