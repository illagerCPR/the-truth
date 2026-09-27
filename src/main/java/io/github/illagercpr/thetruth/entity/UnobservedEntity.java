package io.github.illagercpr.thetruth.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Unobserved (M6): it only moves when nobody is looking at it. Locked in
 * a player's direct gaze (a narrow cone plus an unobstructed line of sight)
 * it turns to stone — immobile, including knockback — while the gaze holds.
 * Spawns only during the unobserved phase of the cycle.
 */
public class UnobservedEntity extends Monster {

    /** Half-angle of the "direct gaze" cone, as a dot product threshold. */
    public static final double GAZE_DOT_THRESHOLD = 0.95D;
    public static final double GAZE_RANGE = 24.0D;

    public UnobservedEntity(final EntityType<? extends UnobservedEntity> type, final Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 30.0D)
            .add(Attributes.ATTACK_DAMAGE, 5.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.32D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, false));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // While observed, the target lock itself fades — it cannot hunt a gaze.
        if (this.isObserved() && this.getTarget() != null) {
            this.setTarget(null);
        }
    }

    /** True while any non-spectating player holds this creature in their gaze. */
    public boolean isObserved() {
        for (final Player player : this.level().players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (isLookedAtBy(player, this)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The pure gaze test: within range, inside the gaze cone, and with an
     * unobstructed line of sight. Static so GameTests can drive it directly.
     */
    public static boolean isLookedAtBy(final Player player, final LivingEntity target) {
        if (player.distanceToSqr(target) > GAZE_RANGE * GAZE_RANGE) {
            return false;
        }
        final Vec3 gaze = player.getViewVector(1.0F).normalize();
        final Vec3 toTarget = target.getEyePosition().subtract(player.getEyePosition());
        if (toTarget.lengthSqr() < 1.0E-4D) {
            return true;
        }
        if (gaze.dot(toTarget.normalize()) < GAZE_DOT_THRESHOLD) {
            return false;
        }
        final HitResult hit = player.level().clip(new ClipContext(player.getEyePosition(),
            target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.isObserved();
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }
}
