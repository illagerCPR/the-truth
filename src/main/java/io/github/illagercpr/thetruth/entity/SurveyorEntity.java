package io.github.illagercpr.thetruth.entity;

import io.github.illagercpr.thetruth.worldgen.CertusTerrainShape;
import java.util.EnumSet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Surveyor (M6): a cruising mechanical eye. When a player is in range it
 * locks on and scans; a completed scan <b>determines</b> the player — a
 * marked target the nearby creatures converge on. Hiding in unobserved shadow
 * (raw light &le; {@value #SHADOW_LIGHT}) or inside a grid gap stalls and
 * reverses the scan (docs/00 section 7).
 */
public class SurveyorEntity extends Monster {

    public static final int SHADOW_LIGHT = 4;
    public static final int SCAN_RANGE = 16;
    public static final int SCAN_DURATION_TICKS = 80;
    public static final int SCAN_COOLDOWN_TICKS = 160;
    static final double DETERMINE_DAMAGE = 6.0D;

    private int scanTicks;
    private int cooldownTicks;

    public SurveyorEntity(final EntityType<? extends SurveyorEntity> type, final Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.FLYING_SPEED, 0.4D)
            .add(Attributes.MOVEMENT_SPEED, 0.2D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected PathNavigation createNavigation(final Level level) {
        final FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new ScanGoal(this));
        this.goalSelector.addGoal(4, new RandomFlyWanderGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean causeFallDamage(final float fallDistance, final float multiplier,
                                   final net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(final double y, final boolean onGround,
                                   final net.minecraft.world.level.block.state.BlockState state,
                                   final net.minecraft.core.BlockPos pos) {
        // Intentionally empty: flying machine eye, no fall dust/sounds.
    }

    /**
     * Whether the target is currently hidden from measurement: inside an
     * unobserved shadow (very low raw light) or inside a grid gap — the void
     * strips where the world has never been read out.
     */
    public static boolean isTargetHidden(final Level level, final LivingEntity target) {
        if (CertusTerrainShape.gridGapAt(target.blockPosition().getX(), target.blockPosition().getZ())) {
            return true;
        }
        return level.getMaxLocalRawBrightness(target.blockPosition()) <= SHADOW_LIGHT;
    }

    /** The scan progress of this surveyor in ticks (0 when idle). */
    public int scanTicks() {
        return this.scanTicks;
    }

    /** Marks the player as determined: damage, glow, and a converge call. */
    private void determinePlayer(final ServerPlayer player) {
        player.hurt(this.damageSources().mobAttack(this), (float) DETERMINE_DAMAGE);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
        player.displayClientMessage(Component.translatable("thetruth.message.surveyor_determined"), true);
        for (final Mob mob : this.level().getEntitiesOfClass(Mob.class,
                this.getBoundingBox().inflate(32.0D),
                mob -> mob instanceof ResidueEntity || mob instanceof SurveyorEntity)) {
            mob.setTarget(player);
        }
        this.level().playSound(null, player.blockPosition(),
            SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    /** Scan behaviour: lock on, progress while the target stays visible, stall in shadow. */
    private static class ScanGoal extends Goal {

        private final SurveyorEntity surveyor;

        ScanGoal(final SurveyorEntity surveyor) {
            this.surveyor = surveyor;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return nearestPlayer() != null && this.surveyor.cooldownTicks <= 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.surveyor.cooldownTicks <= 0 && nearestPlayer() != null;
        }

        @Override
        public void start() {
            this.surveyor.scanTicks = 0;
        }

        @Override
        public void stop() {
            this.surveyor.scanTicks = 0;
        }

        @Override
        public void tick() {
            final Player target = nearestPlayer();
            if (target == null || !(this.surveyor.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            this.surveyor.getNavigation().stop();
            this.surveyor.getLookControl().setLookAt(target);
            if (SurveyorEntity.isTargetHidden(serverLevel, target)) {
                // The target slipped into the unobserved: the scan unreads itself.
                this.surveyor.scanTicks = Math.max(0, this.surveyor.scanTicks - 2);
                return;
            }
            this.surveyor.scanTicks++;
            if (this.surveyor.scanTicks >= SCAN_DURATION_TICKS && target instanceof ServerPlayer serverPlayer) {
                this.surveyor.determinePlayer(serverPlayer);
                this.surveyor.scanTicks = 0;
                this.surveyor.cooldownTicks = SCAN_COOLDOWN_TICKS;
            }
        }

        private Player nearestPlayer() {
            return this.surveyor.level().getNearestPlayer(this.surveyor, SCAN_RANGE);
        }
    }
}
