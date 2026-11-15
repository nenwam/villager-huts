package io.github.nenwam.villagerhuts.entity;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Stops players a worker isn't shared with from using it at all: right-clicking it does nothing (no lead, no name
 * tag, no trading items) and tells them whose it is (WorkerEntity.turnsAway). The interaction is stopped before
 * vanilla or the worker sees it, on both sides.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID)
public final class WorkerProtection {

    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof WorkerEntity worker && worker.turnsAway(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    @SubscribeEvent
    static void onEntityInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof WorkerEntity worker && !worker.canCommand(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    private WorkerProtection() {}
}
