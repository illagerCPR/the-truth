package io.github.illagercpr.thetruth.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/**
 * Simple flying wander for the Certus machines (1.21.1 ships no shared
 * RandomFlyGoal): every few seconds pick a random reachable spot in the
 * air nearby and drift towards it. Surveyor cruises on this.
 */
public class RandomFlyWanderGoal extends Goal {

    private final Monster mob;
    private int recomputeTicks;

    public RandomFlyWanderGoal(final Monster mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (--this.recomputeTicks > 0) {
            return false;
        }
        this.recomputeTicks = adjustedInterval(this.mob.getRandom());
        final Vec3 target = pickTarget();
        if (target == null) {
            return false;
        }
        this.mob.getNavigation().moveTo(target.x, target.y, target.z, 1.0D);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.getNavigation().isInProgress();
    }

    private Vec3 pickTarget() {
        final RandomSource random = this.mob.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            final int dx = random.nextIntBetweenInclusive(-12, 12);
            final int dy = random.nextIntBetweenInclusive(-4, 6);
            final int dz = random.nextIntBetweenInclusive(-12, 12);
            final BlockPos pos = this.mob.blockPosition().offset(dx, dy, dz);
            if (this.mob.level().isEmptyBlock(pos) && this.mob.level().isEmptyBlock(pos.above())) {
                return Vec3.atCenterOf(pos);
            }
        }
        return null;
    }

    private static int adjustedInterval(final RandomSource random) {
        return 40 + random.nextInt(60);
    }
}
