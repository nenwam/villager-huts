package io.github.nenwam.villagerhuts.entity.goal;

import java.util.EnumSet;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * When it's dark outside (night, or a thunderstorm), walk to the indoor spot nearest its chest and
 * stay there until it's light again. Holding the legs blocks work and wandering in the meantime.
 * (No indoor spot near the chest: it carries on outside, running from monsters.)
 */
public class ShelterAtNightGoal extends Goal implements DescribesActivity {
    private static final double ARRIVED_DISTANCE_SQR = 1.0;
    private static final int REPATH_TICKS = 20;

    private final WorkerEntity worker;
    private final double speed;
    private int nextPathTick;

    public ShelterAtNightGoal(WorkerEntity worker, double speed) {
        this.worker = worker;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.worker.level().isDarkOutside() && this.worker.getShelterPos() != null;
    }

    @Override
    public Component describeActivity() {
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
    }

    @Override
    public void stop() {
        this.worker.setSheltered(false);
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos spot = this.worker.getShelterPos();
        if (spot == null) {
            return;
        }
        boolean inside = this.worker.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) < ARRIVED_DISTANCE_SQR;
        this.worker.setSheltered(inside);
        if (!inside && this.worker.tickCount >= this.nextPathTick && WorkReach.canPlan(this.worker)) {
            if (this.worker.getNavigation().isDone()) {
                // A route all the way in (through the door). If there isn't one right now, get as close as possible.
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
}
