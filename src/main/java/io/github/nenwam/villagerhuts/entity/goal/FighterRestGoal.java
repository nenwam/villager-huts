package io.github.nenwam.villagerhuts.entity.goal;

import java.util.EnumSet;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Warriors and rangers with a bed, working: they keep watch all night, so they sleep in the morning, after
 * sunrise until noon. Badly hurt (under half health), they go to bed to recover, any time, until they're
 * nearly healed. In bed they heal quickly (WorkerEntity.tick). They sleep lightly: a foe coming into their
 * guard zone, or getting hurt, wakes them (FighterTargetGoal gives them a target, which ends this).
 */
public class FighterRestGoal extends Goal implements DescribesActivity {
    private static final double BED_REACH = 2.0;
    private static final int REPATH_TICKS = 20;

    private final WorkerEntity worker;
    private final double speed;
    private int nextPathTick;
    private boolean bedTaken;

    public FighterRestGoal(WorkerEntity worker, double speed) {
        this.worker = worker;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.worker.getJob().isArmed() && this.worker.getOrder() == WorkerEntity.Order.WORK && this.worker.hasBed()
                && this.worker.getTarget() == null && (this.worker.isRecovering() || this.worker.isMorningAfterWatch());
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
        this.nextPathTick = 0;
        this.bedTaken = false;
    }

    @Override
    public void stop() {
        if (this.worker.isSleeping()) {
            this.worker.stopSleeping();
        }
        this.worker.getNavigation().stop();
    }

    @Override
    public Component describeActivity() {
        if (this.worker.isSleeping()) {
            return this.worker.isRecovering()
                    ? Component.translatable("activity.villagerhuts.recovering", (int) this.worker.getHealth(), (int) this.worker.getMaxHealth())
                    : Component.translatable("activity.villagerhuts.sleeping_after_watch");
        }
        return Component.translatable(this.bedTaken ? "activity.villagerhuts.bed_taken" : "activity.villagerhuts.going_to_bed");
    }

    @Override
    public void tick() {
        BlockPos bed = this.worker.getBedPos();
        if (bed == null || this.worker.isSleeping()) {
            return;
        }
        BlockState state = this.worker.level().getBlockState(bed);
        this.bedTaken = state.getBlock() instanceof AbstractBedBlock && state.getValue(AbstractBedBlock.OCCUPIED);
        boolean close = bed.closerToCenterThan(this.worker.position(), BED_REACH) && WorkReach.canTouch(this.worker, bed, BED_REACH);
        if (close && !this.bedTaken) {
            this.worker.getNavigation().stop();
            this.worker.startSleeping(bed);
        } else if (!close && this.worker.tickCount >= this.nextPathTick && WorkReach.canPlan(this.worker)) {
            if (this.worker.getNavigation().isDone()) {
                Path route = WorkReach.exactPath(this.worker, bed);
                if (route != null) {
                    this.worker.getNavigation().moveTo(route, this.speed);
                } else {
                    this.worker.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, this.speed);
                }
            }
            this.nextPathTick = this.worker.tickCount + REPATH_TICKS;
        }
    }
}
