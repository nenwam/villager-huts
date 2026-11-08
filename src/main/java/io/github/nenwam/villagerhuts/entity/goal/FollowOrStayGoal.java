package io.github.nenwam.villagerhuts.entity.goal;

import java.util.EnumSet;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Orders from the worker panel (a new worker starts on Follow).
 *  - Follow: keep close to the owner, catching up by teleporting if it falls far behind (like a pet).
 *    If the owner isn't in this world, it waits where it is.
 *  - Stay: stand still, walking back to the spot if it gets pushed or chased away.
 * While it runs it holds the legs, so work, wandering and sheltering don't move the worker.
 * (Running from monsters and panicking come first.)
 */
public class FollowOrStayGoal extends Goal {
    private static final double START_FOLLOWING = 6.0;  // blocks from the owner
    private static final double STOP_FOLLOWING = 3.0;
    private static final double TELEPORT_DISTANCE = 24.0;
    private static final double STAY_SLACK_SQR = 2.25;  // within 1.5 blocks of its spot counts as there
    private static final int REPATH_TICKS = 10;

    private final WorkerEntity worker;
    private final double speed;
    private int nextPathTick;

    public FollowOrStayGoal(WorkerEntity worker, double speed) {
        this.worker = worker;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.worker.getOrder() != WorkerEntity.Order.WORK;
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
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.worker.getOrder() == WorkerEntity.Order.FOLLOW) {
            this.follow();
        } else {
            this.stay();
        }
    }

    private void follow() {
        Player owner = this.worker.getOwner();
        if (owner == null || owner.isSpectator()) {
            this.worker.getNavigation().stop();
            return;
        }
        this.worker.getLookControl().setLookAt(owner, 10.0F, this.worker.getMaxHeadXRot());
        double distance = this.worker.distanceTo(owner);
        if (distance > TELEPORT_DISTANCE && owner.onGround()) {
            this.teleportNear(owner);
        } else if (distance > START_FOLLOWING || (distance > STOP_FOLLOWING && !this.worker.getNavigation().isDone())) {
            if (this.worker.tickCount >= this.nextPathTick) {
                this.worker.getNavigation().moveTo(owner, this.speed);
                this.nextPathTick = this.worker.tickCount + REPATH_TICKS;
            }
        } else {
            this.worker.getNavigation().stop();
        }
    }

    private void stay() {
        BlockPos spot = this.worker.getStayPos();
        if (spot == null || this.worker.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) <= STAY_SLACK_SQR) {
            this.worker.getNavigation().stop();
            return;
        }
        if (this.worker.tickCount >= this.nextPathTick && this.worker.getNavigation().isDone()) {
            this.worker.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, this.speed);
            this.nextPathTick = this.worker.tickCount + REPATH_TICKS * 2;
        }
    }

    // A free spot to stand within a few blocks of the owner (not right on top of them).
    private void teleportNear(Player owner) {
        BlockPos center = owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int dx = this.worker.getRandom().nextIntBetweenInclusive(-3, 3);
            int dz = this.worker.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            BlockPos spot = center.offset(dx, 0, dz);
            Vec3 at = Vec3.atBottomCenterOf(spot);
            if (WorkReach.isStandable(this.worker.level(), spot)
                    && this.worker.level().noCollision(this.worker, this.worker.getBoundingBox().move(at.subtract(this.worker.position())))) {
                this.worker.snapTo(at.x, at.y, at.z, this.worker.getYRot(), this.worker.getXRot());
                this.worker.getNavigation().stop();
                return;
            }
        }
    }
}
