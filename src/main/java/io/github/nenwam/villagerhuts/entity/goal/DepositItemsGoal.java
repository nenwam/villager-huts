package io.github.nenwam.villagerhuts.entity.goal;

import java.util.HashMap;
import java.util.Map;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Any job: carry what the worker has to its chest.
 *  - Pockets full: store the harvest, keeping some seeds (for replanting) and its bone meal, and get back to work.
 *  - All its work done (WorkerEntity.isWorkDone: nothing left to harvest, plant, till or bone-meal, and nothing
 *    in the chest that would change that): put everything away, seeds and bone meal too.
 */
public class DepositItemsGoal extends StorageTripGoal {
    private static final int KEEP_SEEDS = 16;         // of each kind of seed
    private static final int RETRY_TICKS = 200;       // wait between trips
    private static final int FULL_RETRY_TICKS = 1200; // storage was full: wait a minute

    public DepositItemsGoal(WorkerEntity worker, double speed) {
        super(worker, speed);
    }

    @Override
    public Component describeActivity() {
        return Component.translatable("activity.villagerhuts.storing");
    }

    // Keep working until the pockets are full or there's nothing left to do.
    @Override
    protected boolean needsTrip() {
        if (!this.worker.hasFreeSlot()) {
            return this.hasItemsToStore(false);
        }
        return this.worker.isWorkDone() && this.hasItemsToStore(true);
    }

    @Override
    protected boolean isUseful(Container container) {
        return true;
    }

    @Override
    protected int useStorage(Container container) {
        return this.storeItems(container, this.worker.isWorkDone()) ? RETRY_TICKS : FULL_RETRY_TICKS;
    }

    @Override
    protected int retryTicks() {
        return RETRY_TICKS;
    }

    private boolean hasItemsToStore(boolean everything) {
        Map<Item, Integer> seedsKept = new HashMap<>();
        for (ItemStack stack : this.worker.getInventory().getItems()) {
            if (!stack.isEmpty() && amountToStore(stack, seedsKept, everything) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Moves everything (or everything but the seeds and bone meal it keeps) into the container. Returns true if anything was stored. */
    private boolean storeItems(Container container, boolean everything) {
        SimpleContainer inventory = this.worker.getInventory();
        Map<Item, Integer> seedsKept = new HashMap<>();
        boolean storedAny = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int toStore = amountToStore(stack, seedsKept, everything);
            if (toStore <= 0) {
                continue;
            }
            ItemStack rest = HopperBlockEntity.addItem(null, container, stack.split(toStore), null);
            if (rest.getCount() < toStore) {
                storedAny = true;
            }
            if (!rest.isEmpty()) { // didn't all fit: put the rest back
                if (stack.isEmpty()) {
                    inventory.setItem(i, rest);
                } else {
                    stack.grow(rest.getCount());
                }
            }
        }
        inventory.setChanged();
        return storedAny;
    }

    /** How much of this stack to put away. Unless it's everything: keep up to KEEP_SEEDS of each seed, and all bone meal. */
    private static int amountToStore(ItemStack stack, Map<Item, Integer> seedsKept, boolean everything) {
        if (everything) {
            return stack.getCount();
        }
        if (WorkerEntity.isBoneMeal(stack)) {
            return 0;
        }
        if (!WorkerEntity.isPlantableSeed(stack)) {
            return stack.getCount();
        }
        int alreadyKept = seedsKept.getOrDefault(stack.getItem(), 0);
        int keep = Math.min(stack.getCount(), KEEP_SEEDS - alreadyKept);
        seedsKept.put(stack.getItem(), alreadyKept + keep);
        return stack.getCount() - keep;
    }
}
