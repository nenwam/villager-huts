package io.github.nenwam.villagerhuts.entity.goal;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.entity.WorkerStorage;
import io.github.nenwam.villagerhuts.job.Weapons;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Fighters: hurt, with no food on them? Walk to the chest and take some (see Weapons.isPlainFood: bread, cooked
 * meat, carrots... not rotten flesh). They eat it to heal (WorkerEntity.tick). Like a farmer's bone meal: put food
 * in a fighter's chest and it heals between fights.
 */
public class FetchFoodGoal extends StorageTripGoal {
    private static final int MAX_FOOD = 8;
    private static final int CHECK_TICKS = 200;

    public FetchFoodGoal(WorkerEntity worker, double speed) {
        super(worker, speed);
    }

    @Override
    public Component describeActivity() {
        return Component.translatable("activity.villagerhuts.fetching_food");
    }

    @Override
    protected boolean needsTrip() {
        return this.worker.getJob().isFighter() && this.worker.getTarget() == null && this.worker.getHealth() < this.worker.getMaxHealth()
                && !this.worker.hasFood() && this.worker.hasFreeSlot();
    }

    @Override
    protected boolean isUseful(IItemHandler storage) {
        return WorkerStorage.has(storage, Weapons::isPlainFood);
    }

    @Override
    protected int useStorage(IItemHandler storage) {
        return this.take(storage, Weapons::isPlainFood, MAX_FOOD) > 0 ? 20 : CHECK_TICKS;
    }

    @Override
    protected int retryTicks() {
        return CHECK_TICKS;
    }
}
