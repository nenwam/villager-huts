package io.github.nenwam.villagerhuts.entity.goal;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Warrior: run at its target and hit it with its weapon (the weapon's damage and enchantments count, like
 * yours). Higher levels hit harder and more often (WorkerEntity.meleeInterval). FighterTargetGoal picks the target.
 */
public class WarriorAttackGoal extends MeleeAttackGoal implements DescribesActivity {
    private final WorkerEntity worker;

    public WarriorAttackGoal(WorkerEntity worker, double speed) {
        super(worker, speed, true);
        this.worker = worker;
    }

    @Override
    public boolean canUse() {
        return this.worker.getJob() == WorkerJob.WARRIOR && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.worker.getJob() == WorkerJob.WARRIOR && super.canContinueToUse();
    }

    @Override
    protected int getAttackInterval() {
        return this.adjustedTickDelay(this.worker.meleeInterval());
    }

    @Override
    public Component describeActivity() {
        LivingEntity target = this.worker.getTarget();
        return target == null
                ? Component.translatable("activity.villagerhuts.fighting_something")
                : Component.translatable("activity.villagerhuts.fighting", target.getName());
    }
}
