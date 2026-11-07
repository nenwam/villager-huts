package io.github.nenwam.villagerhuts.entity.goal;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Given to hostile mobs (by WorkerHunting) so they hunt workers exactly the way they hunt villagers:
 * a copy of the mob's own villager-targeting rule, aimed at workers instead. That keeps each mob's
 * quirks: whether it needs to see the target, how long it remembers one it lost sight of,
 * and any filters (ravagers ignore babies, for example).
 */
public class HuntWorkersGoal extends NearestAttackableTargetGoal<WorkerEntity> {
    public HuntWorkersGoal(Mob mob, NearestAttackableTargetGoal<?> villagerGoal) {
        super(mob,
                WorkerEntity.class,
                villagerGoal.randomInterval * 2, // stored halved; the constructor halves it again
                villagerGoal.mustSee,
                villagerGoal.mustReach,
                villagerGoal.targetConditions.selector);
        this.setUnseenMemoryTicks(villagerGoal.unseenMemoryTicks);
    }
}
