package io.github.nenwam.villagerhuts.entity.goal;

import java.util.EnumSet;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Done for the day (it's harvested its daily limit): stay around its bed until the next morning, taking a few
 * steps now and then. At night ShelterAtNightGoal puts it to bed. With no bed, it stays by its chest instead.
 * (DepositItemsGoal comes first, so it puts everything away before it rests.)
 */
public class RestGoal extends Goal implements DescribesActivity {
    private static final double NEAR = 4.0;        // this close to its bed counts as resting by it
    private static final int WANDER_RADIUS = 3;
    private static final int WANDER_TICKS = 160;   // a few steps every 8 to 12 seconds
    private static final int REPATH_TICKS = 20;

    private final WorkerEntity worker;
    private final double speed;
    private boolean near;
    private int nextMoveTick;

    public RestGoal(WorkerEntity worker, double speed) {
        this.worker = worker;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.worker.getOrder() == WorkerEntity.Order.WORK && this.worker.getJob() == WorkerJob.FARMER
                && this.worker.isDoneForToday() && this.restSpot() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.near = false;
        this.nextMoveTick = 0;
    }

    @Override
    public void stop() {
        this.worker.getNavigation().stop();
    }

    @Override
    public Component describeActivity() {
        String key = this.near ? "activity.villagerhuts.resting" : "activity.villagerhuts.going_to_rest";
        return Component.translatable(key, this.worker.getHarvestedToday(), this.worker.getDailyLimit());
    }

    @Override
    public void tick() {
        BlockPos spot = this.restSpot();
        if (spot == null) {
            return;
        }
        this.near = this.worker.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) <= NEAR * NEAR;
        if (this.worker.tickCount < this.nextMoveTick || !this.worker.getNavigation().isDone()) {
            return;
        }
        if (!this.near) {
            this.worker.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, this.speed);
            this.nextMoveTick = this.worker.tickCount + REPATH_TICKS;
            return;
        }
        // By the bed: now and then, a few steps to somewhere else beside it.
        for (int i = 0; i < 6; i++) {
            BlockPos step = spot.offset(this.worker.getRandom().nextIntBetweenInclusive(-WANDER_RADIUS, WANDER_RADIUS), 0,
                    this.worker.getRandom().nextIntBetweenInclusive(-WANDER_RADIUS, WANDER_RADIUS));
            if (WorkReach.isStandable(this.worker.level(), step)) {
                this.worker.getNavigation().moveTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, this.speed);
                break;
            }
        }
        this.nextMoveTick = this.worker.tickCount + WANDER_TICKS + this.worker.getRandom().nextInt(80);
    }

    // Its bed if it has one, otherwise its chest.
    private @Nullable BlockPos restSpot() {
        return this.worker.hasBed() ? this.worker.getBedPos() : this.worker.getStoragePos();
    }
}
