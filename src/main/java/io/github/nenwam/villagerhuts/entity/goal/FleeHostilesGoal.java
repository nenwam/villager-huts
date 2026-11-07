package io.github.nenwam.villagerhuts.entity.goal;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Run from hostile mobs (zombies, skeletons, creepers, slimes...) the worker can see within 8 blocks.
 * Speeds up when they get within 7 blocks. Doesn't run while sheltering indoors at night,
 * since the walls and a closed door are safer than running outside.
 */
public class FleeHostilesGoal extends AvoidEntityGoal<Mob> implements DescribesActivity {
    private final WorkerEntity worker;

    public FleeHostilesGoal(WorkerEntity worker) {
        super(worker, Mob.class, entity -> entity instanceof Enemy, 8.0F, 0.7, 0.85, entity -> true);
        this.worker = worker;
    }

    @Override
    public Component describeActivity() {
        return this.toAvoid == null
                ? Component.translatable("activity.villagerhuts.panicking")
                : Component.translatable("activity.villagerhuts.fleeing", this.toAvoid.getName());
    }

    @Override
    public boolean canUse() {
        return !this.worker.isSheltered() && super.canUse();
    }
}
