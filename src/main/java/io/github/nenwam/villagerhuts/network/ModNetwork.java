package io.github.nenwam.villagerhuts.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the mod's network messages and answers worker list requests. */
@EventBusSubscriber(modid = VillagerHuts.MODID)
public final class ModNetwork {

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RequestWorkersPayload.TYPE, RequestWorkersPayload.STREAM_CODEC, ModNetwork::sendWorkerList);
        // The client-side handler is registered in client/ClientWorkers.
        registrar.playToClient(WorkerListPayload.TYPE, WorkerListPayload.STREAM_CODEC);
    }

    // Live data for workers that are loaded; last known data (from the roster) for the rest.
    private static void sendWorkerList(RequestWorkersPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        WorkerRoster roster = WorkerRoster.get(server);
        List<WorkerInfo> workers = new ArrayList<>();
        for (Map.Entry<UUID, WorkerRoster.Entry> row : roster.forOwner(player.getUUID())) {
            UUID id = row.getKey();
            ServerLevel level = server.getLevel(row.getValue().dimension());
            WorkerEntity live = level != null && level.getEntity(id) instanceof WorkerEntity worker && worker.isAlive() ? worker : null;
            if (live != null) {
                roster.update(live);
            }
            WorkerRoster.Entry entry = roster.entry(id).orElse(row.getValue()); // refreshed just above if loaded
            Component status = live != null ? live.currentStatus() : Component.translatable("activity.villagerhuts.far_away");
            workers.add(new WorkerInfo(id, entry.number(), entry.customName(), entry.job(), status, live != null,
                    entry.employedTicks(), entry.hutCenter(), entry.dimension(), entry.workArea(), entry.farmAssigned(),
                    entry.collected(), live != null ? live.getCarriedCount() : 0));
        }
        PacketDistributor.sendToPlayer(player, new WorkerListPayload(workers));
    }

    private ModNetwork() {}
}
