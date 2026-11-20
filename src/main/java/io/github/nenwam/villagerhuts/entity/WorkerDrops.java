package io.github.nenwam.villagerhuts.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * What a block a worker breaks drops, announced the way any broken block's drops are (NeoForge's BlockDropsEvent),
 * so other mods see a worker's harvest like anyone else's: production counters (the Seed to Stars Colony Ledger), mods
 * that change drops. The worker keeps what's left; nothing is dropped on the ground.
 */
public final class WorkerDrops {
    /** Call once the block is gone. Returns the drops for the worker to keep (none if a mod cancelled them). */
    public static List<ItemStack> announce(ServerLevel level, BlockPos pos, BlockState broken, Entity worker, ItemStack tool, List<ItemStack> drops) {
        List<ItemEntity> entities = new ArrayList<>();
        for (ItemStack drop : drops) {
            entities.add(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop)); // never added to the world
        }
        BlockDropsEvent event = NeoForge.EVENT_BUS.post(new BlockDropsEvent(level, pos, broken, null, entities, worker, tool));
        if (event.isCanceled()) {
            return List.of();
        }
        return event.getDrops().stream().map(ItemEntity::getItem).filter(stack -> !stack.isEmpty()).toList();
    }

    private WorkerDrops() {}
}
