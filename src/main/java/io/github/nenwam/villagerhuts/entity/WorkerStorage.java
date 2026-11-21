package io.github.nenwam.villagerhuts.entity;

import java.util.function.Predicate;

import io.github.nenwam.villagerhuts.registry.ModTags;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A worker's chest: a block in the worker_storage tag that holds items through Fabric's item storage (the transfer
 * API), which vanilla chests and barrels have, and so do modded ones (Storage Drawers, Tom's Simple Storage).
 */
public final class WorkerStorage {
    private final Storage<ItemVariant> storage;

    private WorkerStorage(Storage<ItemVariant> storage) {
        this.storage = storage;
    }

    /** The storage at this spot, or null if there's none (or its chunk isn't loaded). */
    public static @Nullable WorkerStorage at(Level level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModTags.WORKER_STORAGE)) {
            return null;
        }
        Storage<ItemVariant> found = ItemStorage.SIDED.find(level, pos, null);
        return found == null ? null : new WorkerStorage(found);
    }

    /** Whether anything in it matches. */
    public boolean has(Predicate<ItemStack> wanted) {
        for (StorageView<ItemVariant> view : this.storage.nonEmptyViews()) {
            if (wanted.test(view.getResource().toStack())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves up to `wanted` matching items into these pockets, as many as fit in them. Returns how many it moved.
     */
    public int take(Predicate<ItemStack> matches, int wanted, SimpleContainer pockets) {
        int taken = 0;
        for (StorageView<ItemVariant> view : this.storage.nonEmptyViews()) {
            if (taken >= wanted) {
                break;
            }
            ItemVariant item = view.getResource();
            ItemStack sample = item.toStack();
            if (!matches.test(sample)) {
                continue;
            }
            int room = roomFor(pockets, sample);
            int amount = (int) Math.min(Math.min(view.getAmount(), wanted - taken), room);
            if (amount <= 0) {
                continue;
            }
            try (Transaction transaction = Transaction.openOuter()) {
                int moved = (int) view.extract(item, amount, transaction);
                transaction.commit();
                if (moved > 0) {
                    pockets.addItem(item.toStack(moved));
                    taken += moved;
                }
            }
        }
        return taken;
    }

    /** Puts as much of this stack in as fits. Returns what's left (empty if it all went in). */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            int stored = (int) this.storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return stack.copyWithCount(stack.getCount() - stored);
        }
    }

    // How many of this item the pockets have room for (in stacks of it, and in empty slots).
    private static int roomFor(SimpleContainer pockets, ItemStack sample) {
        int room = 0;
        for (int i = 0; i < pockets.getContainerSize(); i++) {
            ItemStack inSlot = pockets.getItem(i);
            if (inSlot.isEmpty()) {
                room += sample.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(inSlot, sample)) {
                room += Math.max(0, inSlot.getMaxStackSize() - inSlot.getCount());
            }
        }
        return room;
    }
}
