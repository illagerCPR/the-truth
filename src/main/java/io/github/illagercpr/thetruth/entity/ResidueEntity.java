package io.github.illagercpr.thetruth.entity;

import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.level.Level;

/**
 * The Residue (M6, docs/00 section 7): matter that was not deleted cleanly.
 * Amorphous by lore; mechanically a ground stalker that <b>agitates</b> when a
 * player comes close — faster, and hitting harder — before it settles back
 * once the pressure is gone. Active in the middle and unobserved layers.
 */
public class ResidueEntity extends Monster {

    /** Distance at which the residue shifts into its agitated form. */
    public static final double AGITATION_RANGE = 6.0;
    static final double CALM_SPEED = 0.25D;
    static final double AGITATED_SPEED = 0.36D;
    static final double BASE_DAMAGE = 3.0D;
    static final double AGITATED_BONUS_DAMAGE = 2.0D;

    private boolean agitated;

    public ResidueEntity(final EntityType<? extends ResidueEntity> type, final Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 16.0D)
            .add(Attributes.ATTACK_DAMAGE, BASE_DAMAGE)
            .add(Attributes.MOVEMENT_SPEED, CALM_SPEED)
            .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** True while a player is close enough to bend this residue's form. */
    public boolean isAgitated() {
        return this.agitated;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        final boolean wasAgitated = this.agitated;
        this.agitated = nearestPlayerDistanceSq() <= AGITATION_RANGE * AGITATION_RANGE;
        if (this.agitated != wasAgitated) {
            applyAgitation();
        }
    }

    private double nearestPlayerDistanceSq() {
        double best = Double.MAX_VALUE;
        for (final Player player : this.level().players()) {
            best = Math.min(best, this.distanceToSqr(player));
        }
        return best;
    }

    private void applyAgitation() {
        final var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(this.agitated ? AGITATED_SPEED : CALM_SPEED);
        }
        final var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(this.agitated ? BASE_DAMAGE + AGITATED_BONUS_DAMAGE : BASE_DAMAGE);
        }
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }
}
