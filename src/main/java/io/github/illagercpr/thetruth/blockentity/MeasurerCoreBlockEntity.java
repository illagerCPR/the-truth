package io.github.illagercpr.thetruth.blockentity;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.registry.TheTruthBlockEntities;
import io.github.illagercpr.thetruth.registry.TheTruthEntities;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Measurer Core (M6): the heart of the deep observatory. When a non-spectator
 * player comes close it wakes The Last Measurer once; the core remembers the
 * boss until it dies, then goes back to sleep and can be re-challenged.
 */
public class MeasurerCoreBlockEntity extends BlockEntity {

    public static final double WAKE_RANGE = 12.0D;
    public static final int CHECK_INTERVAL_TICKS = 40;
    /** Vertical offset between the core block and the boss spawn point. */
    public static final int BOSS_SPAWN_Y_OFFSET = 4;

    @Nullable
    private UUID bossId;

    public MeasurerCoreBlockEntity(final BlockPos pos, final BlockState state) {
        super(TheTruthBlockEntities.MEASURER_CORE.get(), pos, state);
    }

    @Nullable
    public UUID bossId() {
        return this.bossId;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final MeasurerCoreBlockEntity core) {
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        core.tickWake(level, pos);
    }

    /**
     * Wakes the boss when a player is near and no live boss is recorded.
     * Public so GameTests can drive it directly.
     */
    public void tickWake(final Level level, final BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.bossId != null) {
            final Entity boss = serverLevel.getEntity(this.bossId);
            if (boss != null && boss.isAlive()) {
                return;
            }
            this.bossId = null; // the previous measurer is gone: allow a rematch
        }
        boolean playerNear = false;
        for (final Player player : serverLevel.players()) {
            if (!player.isCreative() && !player.isSpectator()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                    <= WAKE_RANGE * WAKE_RANGE) {
                playerNear = true;
                break;
            }
        }
        if (!playerNear) {
            return;
        }
        final var boss = TheTruthEntities.LAST_MEASURER.get().create(serverLevel);
        if (boss == null) {
            return;
        }
        final BlockPos spawnPos = pos.above(BOSS_SPAWN_Y_OFFSET);
        boss.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        serverLevel.addFreshEntity(boss);
        this.bossId = boss.getUUID();
        this.setChanged();
        serverLevel.playSound(null, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.6F);
        TheTruth.LOGGER.info("The Last Measurer awakened at {}", spawnPos.toShortString());
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.bossId != null) {
            tag.putUUID("boss_id", this.bossId);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.bossId = tag.hasUUID("boss_id") ? tag.getUUID("boss_id") : null;
    }

    /** Idle sparkle so the core reads as "live" in the arena. */
    public static void clientTick(final Level level, final BlockPos pos) {
        if (level.getGameTime() % 10 == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.6D,
                pos.getY() + 1.1D,
                pos.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.6D,
                0.0D, 0.02D, 0.0D);
        }
    }
}
