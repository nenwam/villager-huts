package io.github.nenwam.villagerhuts.entity.goal;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Warriors and rangers: keep away from creepers. Warriors back off from any creeper that comes within 6 blocks
 * (they leave creepers to rangers); rangers shoot them, and only run from one that's about to blow up.
 */
public class AvoidCreepersGoal extends AvoidEntityGoal<Creeper> implements DescribesActivity {
    private final WorkerEntity worker;

    public AvoidCreepersGoal(WorkerEntity worker) {
        super(worker, Creeper.class, creeper -> !worker.isRanger() || ((Creeper) creeper).getSwellDir() > 0,
                6.0F, 0.7, 0.85, creeper -> true);
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
        return this.worker.canFight() && super.canUse();
    }
}
