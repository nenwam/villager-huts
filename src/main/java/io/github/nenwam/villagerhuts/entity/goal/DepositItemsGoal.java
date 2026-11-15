package io.github.nenwam.villagerhuts.entity.goal;

import java.util.HashMap;
import java.util.Map;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.Weapons;
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
 *    in the chest that would change that; or it's done for the day): put everything away, seeds and bone meal too.
 *  - Fighters store their loot once the fighting's over (or their pockets are full), always keeping their ammo
 *    and some food.
 */
public class DepositItemsGoal extends StorageTripGoal {
    private static final int KEEP_SEEDS = 16;         // of each kind of seed
    private static final int KEEP_FOOD = 16;          // fighters: of each kind of food
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
        Map<Item, Integer> kept = new HashMap<>();
        for (ItemStack stack : this.worker.getInventory().getItems()) {
            if (!stack.isEmpty() && amountToStore(stack, kept, everything) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Moves everything (or everything but the seeds and bone meal it keeps) into the container. Returns true if anything was stored. */
    private boolean storeItems(Container container, boolean everything) {
        SimpleContainer inventory = this.worker.getInventory();
        Map<Item, Integer> kept = new HashMap<>();
        boolean storedAny = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int toStore = amountToStore(stack, kept, everything);
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

    /**
     * How much of this stack to put away. Unless it's everything: keep up to KEEP_SEEDS of each seed, and all bone meal.
     * Fighters always keep their ammo and up to KEEP_FOOD of each food.
     */
    private int amountToStore(ItemStack stack, Map<Item, Integer> kept, boolean everything) {
        if (this.worker.getJob().isFighter()) {
            if (Weapons.usesAmmo(this.worker.getMainHandItem()) && Weapons.ammoFor(this.worker.getMainHandItem()).test(stack)) {
                return 0;
            }
            return Weapons.isPlainFood(stack) ? stack.getCount() - keep(stack, kept, KEEP_FOOD) : stack.getCount();
        }
        if (everything) {
            return stack.getCount();
        }
        if (WorkerEntity.isBoneMeal(stack)) {
            return 0;
        }
        if (!WorkerEntity.isPlantableSeed(stack)) {
            return stack.getCount();
        }
        return stack.getCount() - keep(stack, kept, KEEP_SEEDS);
    }

    // Keeps up to `most` of this kind of item over all the stacks looked at so far; returns how many of this stack.
    private static int keep(ItemStack stack, Map<Item, Integer> kept, int most) {
        int alreadyKept = kept.getOrDefault(stack.getItem(), 0);
        int keep = Math.max(0, Math.min(stack.getCount(), most - alreadyKept));
        kept.put(stack.getItem(), alreadyKept + keep);
        return keep;
    }
}
