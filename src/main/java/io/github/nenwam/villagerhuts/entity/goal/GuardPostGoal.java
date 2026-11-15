package io.github.nenwam.villagerhuts.entity.goal;

import java.util.EnumSet;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Warriors and rangers, working: go to their post and guard it, walking a few steps around it now and then.
 * At night they keep watch there (they don't go to bed: that's when monsters come out). FighterTargetGoal
 * picks a fight with anything that comes into the guard zone; then the attack goals take over.
 */
public class GuardPostGoal extends Goal implements DescribesActivity {
    private static final double AT_POST = 4.0;     // this close counts as at its post
    private static final int PATROL_RADIUS = 4;
    private static final int PATROL_TICKS = 200;   // a few steps every 10 to 15 seconds
    private static final int REPATH_TICKS = 20;

    private final WorkerEntity worker;
    private final double speed;
    private boolean atPost;
    private int nextMoveTick;

    public GuardPostGoal(WorkerEntity worker, double speed) {
        this.worker = worker;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.worker.getJob().isArmed() && this.worker.getOrder() == WorkerEntity.Order.WORK
                && this.worker.getGuardPost() != null && this.worker.getTarget() == null;
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
        this.atPost = false;
        this.nextMoveTick = 0;
    }

    @Override
    public void stop() {
        this.worker.getNavigation().stop();
    }

    @Override
    public Component describeActivity() {
        if (this.worker.isRanger() && !this.worker.hasAmmo()) {
            return Component.translatable("activity.villagerhuts.out_of_ammo");
        }
        if (!this.atPost) {
            return Component.translatable("activity.villagerhuts.going_to_post");
        }
        return Component.translatable(this.worker.level().isNight() ? "activity.villagerhuts.keeping_watch" : "activity.villagerhuts.guarding");
    }

    @Override
    public void tick() {
        BlockPos post = this.worker.getGuardPost();
        if (post == null) {
            return;
        }
        this.atPost = this.worker.position().distanceToSqr(Vec3.atBottomCenterOf(post)) <= AT_POST * AT_POST;
        if (this.worker.tickCount < this.nextMoveTick || !this.worker.getNavigation().isDone()) {
            return;
        }
        if (!this.atPost) {
            this.worker.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, this.speed);
            this.nextMoveTick = this.worker.tickCount + REPATH_TICKS;
            return;
        }
        // At its post: now and then, a few steps to another spot near it (fewer at night: it stands watch).
        for (int i = 0; i < 6; i++) {
            BlockPos step = post.offset(this.worker.getRandom().nextIntBetweenInclusive(-PATROL_RADIUS, PATROL_RADIUS), 0,
                    this.worker.getRandom().nextIntBetweenInclusive(-PATROL_RADIUS, PATROL_RADIUS));
            if (WorkReach.isStandable(this.worker.level(), step)) {
                this.worker.getNavigation().moveTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, this.speed);
                break;
            }
        }
        int wait = PATROL_TICKS + this.worker.getRandom().nextInt(100);
        this.nextMoveTick = this.worker.tickCount + (this.worker.level().isNight() ? wait * 2 : wait);
    }
}
