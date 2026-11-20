package io.github.nenwam.villagerhuts.entity;

import java.util.function.Predicate;

import io.github.nenwam.villagerhuts.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

/**
 * A worker's chest: a block in the worker_storage tag that holds items through NeoForge's item storage, which vanilla
 * chests and barrels have, and so do modded ones (Sophisticated Storage chests and barrels, Storage Drawers, Tom's
 * Simple Storage).
 */
public final class WorkerStorage {
    /** The storage at this spot, or null if there's none (or its chunk isn't loaded). */
    public static @Nullable IItemHandler at(Level level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModTags.WORKER_STORAGE)) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    }

    /** Whether anything in it matches. */
    public static boolean has(IItemHandler storage, Predicate<ItemStack> wanted) {
        for (int i = 0; i < storage.getSlots(); i++) {
            if (wanted.test(storage.getStackInSlot(i))) {
                return true;
            }
        }
        return false;
    }

    private WorkerStorage() {}
}
