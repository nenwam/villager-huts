package io.github.nenwam.villagerhuts.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the mod's network messages and answers the worker panel's requests (list, rename, orders, tool). */
@EventBusSubscriber(modid = VillagerHuts.MODID)
public final class ModNetwork {

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RequestWorkersPayload.TYPE, RequestWorkersPayload.STREAM_CODEC, ModNetwork::sendWorkerList);
        registrar.playToServer(RenameWorkerPayload.TYPE, RenameWorkerPayload.STREAM_CODEC, ModNetwork::handleRename);
        registrar.playToServer(WorkerCommandPayload.TYPE, WorkerCommandPayload.STREAM_CODEC, ModNetwork::handleCommand);
        // The client-side handlers are registered in client/ClientWorkers and client/AssignPreview.
        registrar.playToClient(WorkerListPayload.TYPE, WorkerListPayload.STREAM_CODEC);
        registrar.playToClient(AssignModePayload.TYPE, AssignModePayload.STREAM_CODEC);
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
            workers.add(new WorkerInfo(id, entry.number(), entry.customName(), entry.job(), entry.order(), status, live != null,
                    entry.employedTicks(), entry.dimension(), entry.farm(), entry.storage(),
                    entry.collected(), live != null ? live.getCarriedCount() : 0));
        }
        PacketDistributor.sendToPlayer(player, new WorkerListPayload(workers));
    }

    private static void handleRename(RenameWorkerPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && renameWorker(player.level().getServer(), player.getUUID(), payload.worker(), payload.name())) {
            sendWorkerList(RequestWorkersPayload.INSTANCE, context); // show the new name right away
        }
    }

    private static void handleCommand(WorkerCommandPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && commandWorker(player.level().getServer(), player, payload.worker(), payload.command())) {
            sendWorkerList(RequestWorkersPayload.INSTANCE, context); // show the change right away
        }
    }

    /**
     * Carries out a worker panel button: an order (work, follow, stay), or taking the worker's tool back.
     * @return false if the worker isn't the player's, isn't loaded (too far away), or has no tool to give back
     */
    public static boolean commandWorker(MinecraftServer server, Player player, UUID workerId, WorkerCommandPayload.Command command) {
        WorkerRoster roster = WorkerRoster.get(server);
        WorkerRoster.Entry entry = roster.entry(workerId).orElse(null);
        ServerLevel level = entry == null || !entry.owner().equals(player.getUUID()) ? null : server.getLevel(entry.dimension());
        if (level == null || !(level.getEntity(workerId) instanceof WorkerEntity worker) || !worker.isAlive()) {
            return false;
        }
        switch (command) {
            case WORK -> worker.setOrder(WorkerEntity.Order.WORK);
            case FOLLOW -> worker.setOrder(WorkerEntity.Order.FOLLOW);
            case STAY -> worker.setOrder(WorkerEntity.Order.STAY);
            case TAKE_TOOL -> {
                if (worker.getJob() == WorkerJob.NONE) {
                    return false;
                }
                worker.returnTool(player);
                player.sendOverlayMessage(Component.translatable("message.villagerhuts.job_removed"));
            }
        }
        roster.update(worker);
        return true;
    }

    /**
     * Renames one of a player's workers (from the worker panel). The name is cleaned up like an anvil
     * rename; an empty name removes it. A worker that's far away gets its new name when it's next loaded.
     * @return false if the worker isn't the player's (or is gone), or the name is too long
     */
    public static boolean renameWorker(MinecraftServer server, UUID player, UUID workerId, String requested) {
        WorkerRoster roster = WorkerRoster.get(server);
        WorkerRoster.Entry entry = roster.entry(workerId).orElse(null);
        String cleaned = StringUtil.filterText(requested).strip();
        if (entry == null || !entry.owner().equals(player) || cleaned.length() > RenameWorkerPayload.MAX_NAME_LENGTH) {
            return false;
        }
        roster.rename(workerId, cleaned.isEmpty() ? Optional.empty() : Optional.of(cleaned));
        ServerLevel level = server.getLevel(entry.dimension());
        if (level != null && level.getEntity(workerId) instanceof WorkerEntity worker && worker.isAlive()) {
            roster.update(worker); // nearby: it takes the name now
        }
        return true;
    }

    private ModNetwork() {}
}
