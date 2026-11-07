package io.github.nenwam.villagerhuts.entity.goal;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Farmer: out of seeds, with empty farmland to plant and nothing ripe to harvest? Walk to the
 * hut's storage and take seeds (up to a stack, and no more than there are empty spots).
 * This is how a new farm gets started: put seeds in the hut chest.
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
                && !this.worker.hasSeeds()
                && this.worker.getEmptyFarmland() > 0
                && this.worker.getRipeCrops() == 0; // harvesting first gives seeds anyway
    }

    @Override
    protected boolean isUseful(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (WorkerEntity.isPlantableSeed(container.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected int useStorage(Container container) {
        int wanted = Math.min(MAX_SEEDS, this.worker.getEmptyFarmland());
        int taken = 0;
        for (int i = 0; i < container.getContainerSize() && taken < wanted; i++) {
            ItemStack stack = container.getItem(i);
            if (!WorkerEntity.isPlantableSeed(stack)) {
                continue;
            }
            ItemStack part = container.removeItem(i, Math.min(stack.getCount(), wanted - taken));
            int partCount = part.getCount();
            ItemStack rest = this.worker.getInventory().addItem(part);
            taken += partCount - rest.getCount();
            if (!rest.isEmpty()) { // pockets full: put the rest back and stop
                ItemStack inSlot = container.getItem(i);
                if (inSlot.isEmpty()) {
                    container.setItem(i, rest);
                } else {
                    inSlot.grow(rest.getCount());
                }
                break;
            }
        }
        container.setChanged();
        if (taken > 0) {
            this.worker.markWorked(); // so it doesn't count as idle and put the seeds straight back
        }
        return taken > 0 ? 20 : CHECK_TICKS;
    }

    @Override
    protected int retryTicks() {
        return CHECK_TICKS;
    }
}
