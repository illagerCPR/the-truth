package io.github.illagercpr.thetruth.entity;

import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import io.github.illagercpr.thetruth.registry.TheTruthEntities;
import io.github.illagercpr.thetruth.uncertainty.StackUncertainty;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Last Measurer (M6 boss, docs/00 section 7). Stationary machine eye
 * above the deep observatory; three phases driven by health:
 *
 * <ol>
 * <li><b>Scan</b> — periodic measurement sweeps; a completed sweep determines
 *     a player (heavy damage + partial "solidification" of carried items).
 *     Shadow and grid gaps stall the sweep (see {@link SurveyorEntity#isTargetHidden}).</li>
 * <li><b>Summon &amp; delete</b> — calls Residue and Surveyors, and deletes
 *     terrain columns to carve fresh grid gaps into the arena.</li>
 * <li><b>Overload</b> — exposes data ports; players siphon its measurement
 *     data through AE2 import; enough extracted data overloads it to death.</li>
 * </ol>
 */
public class LastMeasurerEntity extends Monster {

    public static final float PHASE_TWO_HEALTH = 180.0F;
    public static final float PHASE_THREE_HEALTH = 90.0F;
    public static final int SCAN_INTERVAL_TICKS = 200;
    public static final int SCAN_DURATION_TICKS = 100;
    public static final int SCAN_RANGE = 24;
    static final float SCAN_DAMAGE = 12.0F;
    static final int SOLIDIFY_ONE_IN = 3;
    public static final int SUMMON_INTERVAL_TICKS = 160;
    static final int MAX_MINIONS = 6;
    public static final int DELETE_INTERVAL_TICKS = 400;
    static final int DELETE_COLUMN_DEPTH = 3;
    public static final int PORT_INTERVAL_TICKS = 100;
    static final int MAX_ACTIVE_PORTS = 3;
    public static final int PORT_SCAN_CHUNKS = 2;
    public static final int OVERLOAD_THRESHOLD = 16;

    private static final EntityDataAccessor<Integer> DATA_SCAN_TICKS =
        SynchedEntityData.defineId(LastMeasurerEntity.class, EntityDataSerializers.INT);

    private final net.minecraft.server.level.ServerBossEvent bossEvent =
        new net.minecraft.server.level.ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private int phaseTimer;
    private int scanTicks;
    private int overloadProgress;
    private ServerPlayer scanTarget;

    public LastMeasurerEntity(final EntityType<? extends LastMeasurerEntity> type, final Level level) {
        super(type, level);
        this.xpReward = 100;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 300.0D)
            .add(Attributes.ATTACK_DAMAGE, 8.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.3D)
            .add(Attributes.FOLLOW_RANGE, 48.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.ARMOR, 8.0D);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SCAN_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        // Stationary: it measures, it does not chase.
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, SCAN_RANGE));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ phases

    /** The current phase; derived from health so it is stateless and re-entrant. */
    public MeasurerPhase phase() {
        if (this.getHealth() > PHASE_TWO_HEALTH) {
            return MeasurerPhase.SCAN;
        }
        return this.getHealth() > PHASE_THREE_HEALTH ? MeasurerPhase.SUMMON : MeasurerPhase.OVERLOAD;
    }

    public enum MeasurerPhase {
        SCAN,
        SUMMON,
        OVERLOAD
    }

    /** Extracted measurement data units credited towards the overload death. */
    public int overloadProgress() {
        return this.overloadProgress;
    }

    /** Current scan sweep progress in ticks (0 while between sweeps). */
    public int scanProgress() {
        return this.entityData.get(DATA_SCAN_TICKS);
    }

    /** Called by a data port when its stored measurement data gets siphoned away. */
    public void registerOverload() {
        this.overloadProgress++;
        if (this.overloadProgress >= OVERLOAD_THRESHOLD && this.isAlive()) {
            this.hurt(this.damageSources().magic(), Float.MAX_VALUE);
        }
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final MeasurerPhase phase = phase();
        this.bossEvent.setColor(switch (phase) {
            case SCAN -> BossEvent.BossBarColor.PURPLE;
            case SUMMON -> BossEvent.BossBarColor.RED;
            case OVERLOAD -> BossEvent.BossBarColor.GREEN;
        });
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.announcePhasePeriodically(phase);

        switch (phase) {
            case SCAN -> tickScan(serverLevel);
            case SUMMON -> tickSummonAndDelete(serverLevel);
            case OVERLOAD -> {
                tickSummonAndDelete(serverLevel);
                tickDataPorts(serverLevel);
            }
        }
    }

    private void announcePhasePeriodically(final MeasurerPhase phase) {
        if (this.tickCount % 200 != 0) {
            return;
        }
        final Component banner = Component.translatable(switch (phase) {
            case SCAN -> "thetruth.message.measurer_phase_scan";
            case SUMMON -> "thetruth.message.measurer_phase_summon";
            case OVERLOAD -> "thetruth.message.measurer_phase_overload";
        });
        for (final Player player : this.level().players()) {
            if (player.distanceToSqr(this) <= SCAN_RANGE * SCAN_RANGE) {
                player.displayClientMessage(banner, true);
            }
        }
    }

    // ------------------------------------------------------- phase 1: scanning

    private void tickScan(final ServerLevel level) {
        this.phaseTimer++;
        if (this.phaseTimer < SCAN_INTERVAL_TICKS) {
            return;
        }
        if (this.scanTarget == null) {
            if (this.level().getNearestPlayer(this, SCAN_RANGE) instanceof ServerPlayer player) {
                this.scanTarget = player;
            }
            return;
        }
        if (!this.scanTarget.isAlive()
            || this.scanTarget.distanceToSqr(this) > SCAN_RANGE * SCAN_RANGE
            || this.scanTarget.isCreative() || this.scanTarget.isSpectator()) {
            this.scanTarget = null;
            return;
        }
        if (SurveyorEntity.isTargetHidden(level, this.scanTarget)) {
            // The target slipped into the unobserved: the sweep unreads itself.
            this.scanTicks = Math.max(0, this.scanTicks - 2);
            this.entityData.set(DATA_SCAN_TICKS, this.scanTicks);
            return;
        }
        this.scanTicks++;
        this.entityData.set(DATA_SCAN_TICKS, this.scanTicks);
        this.getLookControl().setLookAt(this.scanTarget);
        if (this.scanTicks >= SCAN_DURATION_TICKS) {
            determinePlayer(this.scanTarget);
            this.scanTicks = 0;
            this.phaseTimer = 0;
            this.scanTarget = null;
        }
    }

    /** A completed sweep: heavy damage, partial solidification, and a converge call. */
    private void determinePlayer(final ServerPlayer player) {
        player.hurt(this.damageSources().mobAttack(this), SCAN_DAMAGE);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
        final Inventory inventory = player.getInventory();
        final var random = this.getRandom();
        solidifyList(inventory.items, random);
        solidifyList(inventory.offhand, random);
        for (final Monster minion : this.level().getEntitiesOfClass(Monster.class,
                this.getBoundingBox().inflate(48.0D),
                mob -> mob instanceof ResidueEntity || mob instanceof SurveyorEntity)) {
            minion.setTarget(player);
        }
        player.displayClientMessage(Component.translatable("thetruth.message.measurer_determined"), true);
        this.level().playSound(null, player.blockPosition(),
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0F, 1.4F);
    }

    private static void solidifyList(final List<ItemStack> slots,
                                     final net.minecraft.util.RandomSource random) {
        for (int i = 0; i < slots.size(); i++) {
            final ItemStack stack = slots.get(i);
            if (stack.isEmpty() || !StackUncertainty.isConvertible(stack) || random.nextInt(SOLIDIFY_ONE_IN) != 0) {
                continue;
            }
            slots.set(i, StackUncertainty.toUnformed(stack));
        }
    }

    // --------------------------------------- phase 2: summon & delete terrain

    private void tickSummonAndDelete(final ServerLevel level) {
        this.phaseTimer++;
        if (this.phaseTimer % SUMMON_INTERVAL_TICKS == 0) {
            summonMinions(level);
        }
        if (this.phaseTimer % DELETE_INTERVAL_TICKS == 0) {
            deleteTerrainColumn(level);
        }
    }

    private void summonMinions(final ServerLevel level) {
        final long minionCount = level.getEntitiesOfClass(Monster.class,
            this.getBoundingBox().inflate(24.0D),
            mob -> mob instanceof ResidueEntity || mob instanceof SurveyorEntity).size();
        if (minionCount >= MAX_MINIONS) {
            return;
        }
        final int residueToSpawn = 2;
        for (int i = 0; i < residueToSpawn + 1; i++) {
            final BlockPos spot = findGroundSpot(level, 4, 10);
            if (spot == null) {
                continue;
            }
            final var type = i < residueToSpawn
                ? TheTruthEntities.RESIDUE.get()
                : TheTruthEntities.SURVEYOR.get();
            final var minion = type.create(level);
            if (minion == null) {
                continue;
            }
            minion.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, this.getYRot(), 0.0F);
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null);
            level.addFreshEntity(minion);
        }
        this.level().playSound(null, this.blockPosition(),
            SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
    }

    /** Carves a tidy vertical gap — deletion in this world is always grid-shaped. */
    private void deleteTerrainColumn(final ServerLevel level) {
        final var random = this.getRandom();
        final int dx = random.nextIntBetweenInclusive(-12, 12);
        final int dz = random.nextIntBetweenInclusive(-12, 12);
        final int baseY = this.blockPosition().getY() + random.nextIntBetweenInclusive(-4, 4);
        for (int i = 0; i < DELETE_COLUMN_DEPTH; i++) {
            final BlockPos pos = new BlockPos(this.blockPosition().getX() + dx, baseY + i,
                this.blockPosition().getZ() + dz);
            final BlockState state = level.getBlockState(pos);
            if (state.is(TheTruthBlocks.CERTUS_STONE.get())) {
                level.removeBlock(pos, false);
            }
        }
        this.level().playSound(null, this.blockPosition(),
            SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.5F, 0.2F);
    }

    // ------------------------------------------- phase 3: data ports & overload

    private void tickDataPorts(final ServerLevel level) {
        if (this.phaseTimer % PORT_INTERVAL_TICKS != 0) {
            return;
        }
        if (countOwnedPorts(level) >= MAX_ACTIVE_PORTS) {
            return;
        }
        final BlockPos spot = findGroundSpot(level, 3, 7);
        if (spot == null) {
            return;
        }
        level.setBlockAndUpdate(spot, TheTruthBlocks.DATA_PORT.get().defaultBlockState());
        if (level.getBlockEntity(spot)
            instanceof io.github.illagercpr.thetruth.blockentity.DataPortBlockEntity port) {
            port.bindBoss(this.getUUID());
        }
    }

    private long countOwnedPorts(final ServerLevel level) {
        long count = 0;
        for (final var entry : loadedPortEntries(level).entrySet()) {
            if (entry.getValue()
                instanceof io.github.illagercpr.thetruth.blockentity.DataPortBlockEntity port
                && port.ownsBoss(this.getUUID())) {
                count++;
            }
        }
        return count;
    }

    /** Boss death: the arena's data ports are pulled back into the unread void. */
    @Override
    public void die(final DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            for (final var entry : loadedPortEntries(level).entrySet()) {
                if (entry.getValue()
                    instanceof io.github.illagercpr.thetruth.blockentity.DataPortBlockEntity port
                    && port.ownsBoss(this.getUUID())) {
                    level.removeBlock(entry.getKey(), false);
                }
            }
        }
    }

    private java.util.Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> loadedPortEntries(
            final ServerLevel level) {
        final var result = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.entity.BlockEntity>();
        final int minX = (this.blockPosition().getX() - 24) >> 4;
        final int maxX = (this.blockPosition().getX() + 24) >> 4;
        final int minZ = (this.blockPosition().getZ() - 24) >> 4;
        final int maxZ = (this.blockPosition().getZ() + 24) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz)
                    instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                    result.putAll(chunk.getBlockEntities());
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ misc

    @Override
    public void startSeenByPlayer(final ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(final ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    private BlockPos findGroundSpot(final ServerLevel level, final int minRadius, final int maxRadius) {
        final var random = this.getRandom();
        for (int attempt = 0; attempt < 12; attempt++) {
            final int dx = random.nextIntBetweenInclusive(-maxRadius, maxRadius);
            final int dz = random.nextIntBetweenInclusive(-maxRadius, maxRadius);
            if (Math.abs(dx) < minRadius && Math.abs(dz) < minRadius) {
                continue;
            }
            final BlockPos ground = this.blockPosition().offset(dx, -2, dz);
            final BlockPos above = ground.above();
            if (!level.getBlockState(ground).isAir() && level.getBlockState(above).isAir()
                && level.getBlockState(above.above()).isAir()) {
                return above;
            }
        }
        return null;
    }

    @Override
    public boolean removeWhenFarAway(final double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(final CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("overload_progress", this.overloadProgress);
    }

    @Override
    public void readAdditionalSaveData(final CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.overloadProgress = tag.getInt("overload_progress");
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    @Override
    public void setCustomName(final Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

}
