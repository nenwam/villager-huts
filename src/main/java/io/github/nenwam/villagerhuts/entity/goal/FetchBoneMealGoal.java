package io.github.nenwam.villagerhuts.entity.goal;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;

/**
 * Farmer: crops growing on its farm, no bone meal on it, and some in its chest? Walk over and take some
 * (up to a stack); FarmerWorkGoal uses it on the growing crops. Put bone meal in the chest to speed a farm up.
 */
public class FetchBoneMealGoal extends StorageTripGoal {
    private static final int MAX_BONE_MEAL = 64;
    private static final int CHECK_TICKS = 200; // look for bone meal in the chest every 10 seconds

    public FetchBoneMealGoal(WorkerEntity worker, double speed) {
        super(worker, speed);
    }

    @Override
    public Component describeActivity() {
        return Component.translatable("activity.villagerhuts.fetching_bone_meal");
    }

    @Override
    protected boolean needsTrip() {
        return this.worker.getJob() == WorkerJob.FARMER
                && this.worker.getGrowingCrops() > 0
                && !this.worker.hasBoneMeal()
                && this.worker.hasFreeSlot();
    }

    @Override
    protected boolean isUseful(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (WorkerEntity.isBoneMeal(container.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected int useStorage(Container container) {
        return this.take(container, WorkerEntity::isBoneMeal, MAX_BONE_MEAL) > 0 ? 20 : CHECK_TICKS;
    }

    @Override
    protected int retryTicks() {
        return CHECK_TICKS;
    }
}
