package io.github.nenwam.villagerhuts.entity.goal;

import java.util.EnumSet;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * When it's dark outside (night, or a thunderstorm), stop work until it's light again:
 *  - with a bed: walk to it and sleep in it (if someone else is in it, wait beside it);
 *  - without one: walk to the indoor spot nearest its chest and stay there.
 * Holding the legs (and head) blocks work and wandering in the meantime. Getting hurt, or a monster coming
 * near, wakes it up (those goals come first). No bed and no indoor spot near the chest: it carries on outside,
 * running from monsters. Warriors and rangers don't: they keep watch at night (see FighterRestGoal).
 */
public class ShelterAtNightGoal extends Goal implements DescribesActivity {
    private static final double ARRIVED_DISTANCE_SQR = 1.0;
    private static final double BED_REACH = 2.0; // close enough to get into bed, like villagers
    private static final int REPATH_TICKS = 20;

    private final WorkerEntity worker;
    private final double speed;
    private int nextPathTick;
    private boolean bedTaken;

    public ShelterAtNightGoal(WorkerEntity worker, double speed) {
        this.worker = worker;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !this.worker.getJob().isArmed() && this.worker.level().isNight()
                && (this.worker.hasBed() || this.worker.getShelterPos() != null);
    }

    @Override
    public Component describeActivity() {
        if (this.worker.isSleeping()) {
            return Component.translatable("activity.villagerhuts.sleeping");
        }
        if (this.worker.hasBed()) {
            return Component.translatable(this.bedTaken ? "activity.villagerhuts.bed_taken" : "activity.villagerhuts.going_to_bed");
        }
        return Component.translatable(this.worker.isSheltered() ? "activity.villagerhuts.sheltering" : "activity.villagerhuts.heading_inside");
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
            this.worker.stopSleeping(); // morning (or something woke it)
        }
        this.worker.setSheltered(false);
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.worker.hasBed()) {
            this.goToBed(this.worker.getBedPos());
            return;
        }
        BlockPos spot = this.worker.getShelterPos();
        if (spot == null) {
            return;
        }
        boolean inside = this.worker.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) < ARRIVED_DISTANCE_SQR;
        this.worker.setSheltered(inside);
        if (!inside) {
            this.walkTo(spot);
        }
    }

    private void goToBed(BlockPos bed) {
        if (this.worker.isSleeping()) {
            return;
        }
        BlockState state = this.worker.level().getBlockState(bed);
        this.bedTaken = state.getBlock() instanceof BedBlock && state.getValue(BedBlock.OCCUPIED);
        boolean close = bed.closerToCenterThan(this.worker.position(), BED_REACH) && WorkReach.canTouch(this.worker, bed, BED_REACH);
        if (close && !this.bedTaken) {
            this.worker.getNavigation().stop();
            this.worker.startSleeping(bed);
        } else if (!close) {
            this.walkTo(bed);
        }
    }

    private void walkTo(BlockPos spot) {
        if (this.worker.tickCount < this.nextPathTick || !WorkReach.canPlan(this.worker)) {
            return;
        }
        if (this.worker.getNavigation().isDone()) {
            // A route all the way there (through the door). If there isn't one right now, get as close as possible.
            Path route = WorkReach.exactPath(this.worker, spot);
            if (route != null) {
                this.worker.getNavigation().moveTo(route, this.speed);
            } else {
                this.worker.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, this.speed);
            }
        }
        this.nextPathTick = this.worker.tickCount + REPATH_TICKS;
    }
}
