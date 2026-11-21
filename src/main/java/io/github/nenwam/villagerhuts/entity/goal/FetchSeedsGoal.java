package io.github.nenwam.villagerhuts.entity.goal;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.entity.WorkerStorage;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.network.chat.Component;

/**
 * Farmer: out of seeds, with empty farmland to plant and nothing ripe to harvest? Walk to its
 * chest and take seeds (up to a stack, and no more than there are empty spots): only seeds it can plant
 * now, so none out of season (with Serene Seasons).
 * This is how a new farm gets started: put seeds in the farmer's chest.
 */
public class FetchSeedsGoal extends StorageTripGoal {
    private static final int MAX_SEEDS = 64;
    private static final int CHECK_TICKS = 100; // look for seeds in storage every 5 seconds

    public FetchSeedsGoal(WorkerEntity worker, double speed) {
        super(worker, speed);
    }

    @Override
    public Component describeActivity() {
        return Component.translatable("activity.villagerhuts.fetching_seeds");
    }

    @Override
    protected boolean needsTrip() {
        return this.worker.getJob() == WorkerJob.FARMER
                && !this.worker.isDoneForToday()
                && !this.worker.isFieldPaused()
                && !this.worker.hasSeeds()
                && this.worker.getEmptyFarmland() > 0
                && this.worker.getRipeCrops() == 0; // harvesting first gives seeds anyway
    }

    @Override
    protected boolean isUseful(WorkerStorage storage) {
        return storage.has(this.worker::canPlantNow);
    }

    @Override
    protected int useStorage(WorkerStorage storage) {
        int taken = this.take(storage, this.worker::canPlantNow, Math.min(MAX_SEEDS, this.worker.getEmptyFarmland()));
        return taken > 0 ? 20 : CHECK_TICKS;
    }

    @Override
    protected int retryTicks() {
        return CHECK_TICKS;
    }
}
