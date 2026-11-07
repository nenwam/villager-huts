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
 * Any job: when the worker's pockets are full, or it has run out of work, carry what it has to
 * the nearest storage block in its hut. Keeps some seeds for replanting.
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
        return this.hasItemsToStore() && (!this.worker.hasFreeSlot() || this.worker.isIdle());
    }

    @Override
    protected boolean isUseful(Container container) {
        return true;
    }

    @Override
    protected int useStorage(Container container) {
        return this.storeItems(container) ? RETRY_TICKS : FULL_RETRY_TICKS;
    }

    @Override
    protected int retryTicks() {
        return RETRY_TICKS;
    }

    private boolean hasItemsToStore() {
        Map<Item, Integer> seedsKept = new HashMap<>();
        for (ItemStack stack : this.worker.getInventory().getItems()) {
            if (!stack.isEmpty() && amountToStore(stack, seedsKept) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Moves everything except kept seeds into the container. Returns true if anything was stored. */
    private boolean storeItems(Container container) {
        SimpleContainer inventory = this.worker.getInventory();
        Map<Item, Integer> seedsKept = new HashMap<>();
        boolean storedAny = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int toStore = amountToStore(stack, seedsKept);
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

    /** How much of this stack to put away. Seeds: keep up to KEEP_SEEDS of each kind. */
    private static int amountToStore(ItemStack stack, Map<Item, Integer> seedsKept) {
        if (!WorkerEntity.isPlantableSeed(stack)) {
            return stack.getCount();
        }
        int alreadyKept = seedsKept.getOrDefault(stack.getItem(), 0);
        int keep = Math.min(stack.getCount(), KEEP_SEEDS - alreadyKept);
        seedsKept.put(stack.getItem(), alreadyKept + keep);
        return stack.getCount() - keep;
    }
}
