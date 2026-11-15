package io.github.nenwam.villagerhuts.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.nenwam.villagerhuts.Config;
import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.Weapons;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.Nullable;

/**
 * Registers the mod's network messages and answers the worker panel's requests (list, rename, orders, tool,
 * sharing). The server checks every request: a player can only use workers they own or that are shared with
 * them, and only an owner can take a worker's tool, share it, or give it away.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID)
public final class ModNetwork {

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToServer(RequestWorkersPayload.TYPE, RequestWorkersPayload.STREAM_CODEC, ModNetwork::sendWorkerList);
        registrar.playToServer(RenameWorkerPayload.TYPE, RenameWorkerPayload.STREAM_CODEC, ModNetwork::handleRename);
        registrar.playToServer(WorkerCommandPayload.TYPE, WorkerCommandPayload.STREAM_CODEC, ModNetwork::handleCommand);
        registrar.playToServer(ShareWorkerPayload.TYPE, ShareWorkerPayload.STREAM_CODEC, ModNetwork::handleShare);
        // The client-side handlers are registered in client/ClientWorkers and client/AssignPreview.
        registrar.playToClient(WorkerListPayload.TYPE, WorkerListPayload.STREAM_CODEC);
        registrar.playToClient(AssignModePayload.TYPE, AssignModePayload.STREAM_CODEC);
    }

    private static void sendWorkerList(RequestWorkersPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new WorkerListPayload(workerList(player.level().getServer(), player.getUUID())));
        }
    }

    /** The workers a player can use, for their worker panel: live data for workers that are loaded, last known data for the rest. */
    public static List<WorkerInfo> workerList(MinecraftServer server, UUID player) {
        WorkerRoster roster = WorkerRoster.get(server);
        List<WorkerInfo> workers = new ArrayList<>();
        for (Map.Entry<UUID, WorkerRoster.Entry> row : roster.forPlayer(player)) {
            UUID id = row.getKey();
            WorkerEntity live = WorkerRoster.loaded(server, id, row.getValue());
            if (live != null) {
                roster.update(live);
            }
            WorkerRoster.Entry entry = roster.entry(id).orElse(row.getValue()); // refreshed just above if loaded
            Component status = live != null ? live.currentStatus() : Component.translatable("activity.villagerhuts.far_away");
            List<WorkerInfo.PlayerRef> shared = entry.shared().stream().map(other -> playerRef(server, other)).toList();
            int dailyLimit = live != null ? live.getDailyLimit() : Config.FARMER_DAILY_HARVEST.getAsInt() * entry.rank();
            boolean fighter = WorkerJob.byId(entry.job()).isFighter();
            ItemStack tool = live != null ? live.getMainHandItem().copy()
                    : entry.tool().flatMap(BuiltInRegistries.ITEM::getOptional).map(ItemStack::new).orElse(ItemStack.EMPTY);
            int maxHealth = live != null ? (int) live.getMaxHealth() : fighter ? WorkerEntity.fighterHealth(entry.rank()) : WorkerEntity.BASE_HEALTH;
            int ammo = live != null && live.isRanger() && Weapons.usesAmmo(live.getMainHandItem()) ? live.getAmmoCount() : -1;
            workers.add(new WorkerInfo(id, entry.number(), entry.customName(), entry.job(), entry.order(), status, live != null,
                    entry.employedTicks(), entry.dimension(), entry.farm(), entry.storage(), entry.bed(),
                    entry.collected(), live != null ? live.getCarriedCount() : 0, playerRef(server, entry.owner()),
                    entry.owner().equals(player), shared, entry.rank(), entry.harvestedToday(), dailyLimit,
                    tool, live != null ? (int) Math.ceil(live.getHealth()) : -1, maxHealth, ammo, entry.defeated()));
        }
        return workers;
    }

    private static WorkerInfo.PlayerRef playerRef(MinecraftServer server, UUID player) {
        return new WorkerInfo.PlayerRef(player, WorkerRoster.playerName(server, player).getString());
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

    private static void handleShare(ShareWorkerPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && shareWorker(player.level().getServer(), player, payload.worker(), payload.player(), payload.action())) {
            sendWorkerList(RequestWorkersPayload.INSTANCE, context); // show the change right away
        }
    }

    /**
     * Carries out a worker panel button: an order (work, follow, stay), or taking the worker's tool back.
     * Its owner and the players it's shared with can give orders (Follow follows whoever pressed it);
     * only its owner can take its tool.
     * @return false if the player can't, the worker isn't loaded (too far away), or it has no tool to give back
     */
    public static boolean commandWorker(MinecraftServer server, Player player, UUID workerId, WorkerCommandPayload.Command command) {
        WorkerRoster roster = WorkerRoster.get(server);
        WorkerRoster.Entry entry = roster.entry(workerId).filter(e -> e.canCommand(player.getUUID())).orElse(null);
        WorkerEntity worker = entry == null ? null : WorkerRoster.loaded(server, workerId, entry);
        if (worker == null) {
            return false;
        }
        switch (command) {
            case WORK -> worker.setOrder(WorkerEntity.Order.WORK);
            case FOLLOW -> worker.setOrder(WorkerEntity.Order.FOLLOW, player.getUUID());
            case STAY -> worker.setOrder(WorkerEntity.Order.STAY);
            case TAKE_TOOL -> {
                if (!entry.owner().equals(player.getUUID()) || !worker.getJob().hasTool()) {
                    return false;
                }
                boolean fighter = worker.getJob().isFighter();
                worker.returnTool(player);
                player.sendOverlayMessage(Component.translatable(fighter ? "message.villagerhuts.weapon_removed" : "message.villagerhuts.job_removed"));
            }
        }
        roster.update(worker);
        return true;
    }

    /**
     * Renames a worker (from the worker panel): its owner or a player it's shared with can. The name is cleaned up
     * like an anvil rename; an empty name removes it. A worker that's far away gets its new name when it's next loaded.
     * @return false if the player can't (or the worker is gone), or the name is too long
     */
    public static boolean renameWorker(MinecraftServer server, UUID player, UUID workerId, String requested) {
        WorkerRoster roster = WorkerRoster.get(server);
        WorkerRoster.Entry entry = roster.entry(workerId).orElse(null);
        String cleaned = StringUtil.filterText(requested).strip();
        if (entry == null || !entry.canCommand(player) || cleaned.length() > RenameWorkerPayload.MAX_NAME_LENGTH) {
            return false;
        }
        roster.rename(workerId, cleaned.isEmpty() ? Optional.empty() : Optional.of(cleaned));
        WorkerEntity worker = WorkerRoster.loaded(server, workerId, entry);
        if (worker != null) {
            roster.update(worker); // nearby: it takes the name now
        }
        return true;
    }

    /**
     * A button on a worker's sharing page. Works whether the worker is near or far away.
     *  - SHARE: its owner lets another player (who's online) use it too.
     *  - UNSHARE: its owner stops sharing it with a player, or a player it's shared with stops using it (target = themselves).
     *  - GIVE: its owner gives it to a player they share it with; the old owner keeps using it, as a shared player.
     * The other player is told, if they're online.
     * @return false if the player isn't allowed to, or it changes nothing
     */
    public static boolean shareWorker(MinecraftServer server, Player actor, UUID workerId, UUID target, ShareWorkerPayload.Action action) {
        WorkerRoster roster = WorkerRoster.get(server);
        WorkerRoster.Entry entry = roster.entry(workerId).orElse(null);
        if (entry == null) {
            return false;
        }
        UUID owner = entry.owner();
        boolean byOwner = owner.equals(actor.getUUID());
        boolean targetShared = entry.shared().contains(target);
        List<UUID> shared = new ArrayList<>(entry.shared());
        UUID newOwner = owner;
        @Nullable String toTarget;
        switch (action) {
            case SHARE -> {
                if (!byOwner || target.equals(owner) || targetShared || server.getPlayerList().getPlayer(target) == null) {
                    return false;
                }
                shared.add(target);
                toTarget = "message.villagerhuts.shared_with_you";
            }
            case UNSHARE -> {
                boolean leaving = target.equals(actor.getUUID());
                if (!targetShared || !(byOwner || leaving)) {
                    return false;
                }
                shared.remove(target);
                toTarget = leaving ? null : "message.villagerhuts.unshared_with_you";
                if (leaving) {
                    tell(server, owner, Component.translatable("message.villagerhuts.left_worker", actor.getName(), workerName(entry)));
                }
            }
            case GIVE -> {
                if (!byOwner || !targetShared) {
                    return false;
                }
                shared.remove(target);
                shared.add(owner); // the old owner can still use it
                newOwner = target;
                toTarget = "message.villagerhuts.given_to_you";
            }
            default -> {
                return false;
            }
        }
        roster.setOwnership(workerId, newOwner, shared, WorkerRoster.loaded(server, workerId, entry));
        if (toTarget != null) {
            tell(server, target, Component.translatable(toTarget, actor.getName(), workerName(entry)));
        }
        return true;
    }

    private static Component workerName(WorkerRoster.Entry entry) {
        Component job = Component.translatable("job.villagerhuts." + entry.job());
        return entry.customName().<Component>map(Component::literal).orElse(job);
    }

    private static void tell(MinecraftServer server, UUID player, Component message) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.sendSystemMessage(message);
        }
    }

    private ModNetwork() {}
}
