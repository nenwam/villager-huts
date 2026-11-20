package io.github.nenwam.villagerhuts.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.nenwam.villagerhuts.Config;
import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.compat.Teams;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.Weapons;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.Nullable;

/**
 * Registers the mod's network messages and answers the worker panel's requests (list, rename, orders, tool,
 * sharing, finding a worker). The server checks every request: a player can only use workers they own, that are
 * shared with them, or that belong to their party (FTB Teams), and only an owner can take a worker's tool, share
 * it, or give it away. A request it turns down gets a line in the panel saying why.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("5");
        registrar.playToServer(RequestWorkersPayload.TYPE, RequestWorkersPayload.STREAM_CODEC, ModNetwork::sendWorkerList);
        registrar.playToServer(RenameWorkerPayload.TYPE, RenameWorkerPayload.STREAM_CODEC, ModNetwork::handleRename);
        registrar.playToServer(WorkerCommandPayload.TYPE, WorkerCommandPayload.STREAM_CODEC, ModNetwork::handleCommand);
        registrar.playToServer(ShareWorkerPayload.TYPE, ShareWorkerPayload.STREAM_CODEC, ModNetwork::handleShare);
        registrar.playToServer(OrderAllPayload.TYPE, OrderAllPayload.STREAM_CODEC, ModNetwork::handleOrderAll);
        // Handled by the client classes (only ever called in a player's game, so a server never loads them).
        registrar.playToClient(WorkerListPayload.TYPE, WorkerListPayload.STREAM_CODEC,
                (payload, context) -> io.github.nenwam.villagerhuts.client.ClientWorkers.receive(payload));
        registrar.playToClient(AssignModePayload.TYPE, AssignModePayload.STREAM_CODEC,
                (payload, context) -> io.github.nenwam.villagerhuts.client.AssignPreview.receive(payload));
        registrar.playToClient(PanelNoticePayload.TYPE, PanelNoticePayload.STREAM_CODEC,
                (payload, context) -> io.github.nenwam.villagerhuts.client.ClientWorkers.receiveNotice(payload));
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
                    tool, live != null ? (int) Math.ceil(live.getHealth()) : -1, maxHealth, ammo, entry.defeated(),
                    entry.progress().field(), Teams.partyOf(entry.owner()).map(Teams.Party::name)));
        }
        return workers;
    }

    private static WorkerInfo.PlayerRef playerRef(MinecraftServer server, UUID player) {
        return new WorkerInfo.PlayerRef(player, WorkerRoster.playerName(server, player).getString());
    }

    private static void handleRename(RenameWorkerPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (renameWorker(server, player.getUUID(), payload.worker(), payload.name())) {
            sendWorkerList(RequestWorkersPayload.INSTANCE, context); // show the new name right away
        } else {
            notice(player, accessRefusal(server, player, payload.worker()));
        }
    }

    private static void handleCommand(WorkerCommandPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (commandWorker(server, player, payload.worker(), payload.command())) {
            sendWorkerList(RequestWorkersPayload.INSTANCE, context); // show the change right away
        } else {
            notice(player, commandRefusal(server, player, payload.worker(), payload.command()));
        }
    }

    private static void handleShare(ShareWorkerPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (shareWorker(server, player, payload.worker(), payload.player(), payload.action())) {
            sendWorkerList(RequestWorkersPayload.INSTANCE, context); // show the change right away
        } else {
            notice(player, shareRefusal(server, player, payload.worker(), payload.player(), payload.action()));
        }
    }

    private static void handleOrderAll(OrderAllPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            notice(player, orderAll(player.level().getServer(), player, payload.order()));
            sendWorkerList(RequestWorkersPayload.INSTANCE, context);
        }
    }

    private static void notice(ServerPlayer player, @Nullable Component message) {
        if (message != null) {
            PacketDistributor.sendToPlayer(player, new PanelNoticePayload(message));
        }
    }

    /**
     * Carries out a worker panel button: an order (work, follow, stay), clearing a farmer's field or starting it
     * farming again, taking the worker's tool back, or saying where it is. Its owner and the players who can use it
     * can do all but taking its tool (Follow follows whoever pressed it); only its owner can take its tool.
     * @return false if the player can't, the worker isn't loaded (too far away; saying where it is works anyway),
     *         or it has no tool to give back
     */
    public static boolean commandWorker(MinecraftServer server, Player player, UUID workerId, WorkerCommandPayload.Command command) {
        WorkerRoster roster = WorkerRoster.get(server);
        WorkerRoster.Entry entry = roster.entry(workerId).filter(e -> e.canCommand(player.getUUID())).orElse(null);
        WorkerEntity worker = entry == null ? null : WorkerRoster.loaded(server, workerId, entry);
        if (entry != null && (command == WorkerCommandPayload.Command.LOCATE || command == WorkerCommandPayload.Command.LOCATE_ON_MAP)) {
            locationMessages(entry, worker, command == WorkerCommandPayload.Command.LOCATE_ON_MAP).forEach(player::sendSystemMessage);
            return true;
        }
        if (worker == null) {
            return false;
        }
        switch (command) {
            case WORK -> worker.setOrder(WorkerEntity.Order.WORK);
            case FOLLOW -> worker.setOrder(WorkerEntity.Order.FOLLOW, player.getUUID());
            case STAY -> worker.setOrder(WorkerEntity.Order.STAY);
            case CLEAR_FIELD -> {
                if (!worker.clearField()) {
                    return false;
                }
            }
            case START_FARMING -> {
                if (!worker.startFarming()) {
                    return false;
                }
            }
            case TAKE_TOOL -> {
                if (!entry.owner().equals(player.getUUID()) || !worker.getJob().hasTool()) {
                    return false;
                }
                boolean fighter = worker.getJob().isFighter();
                worker.returnTool(player);
                player.displayClientMessage(Component.translatable(fighter ? "message.villagerhuts.weapon_removed" : "message.villagerhuts.job_removed"), true);
            }
            case LOCATE, LOCATE_ON_MAP -> { } // handled above
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

    /**
     * Why a worker panel request about this worker would be turned down: it's gone, or the player can't use it.
     * @return null if neither
     */
    public static @Nullable Component accessRefusal(MinecraftServer server, Player player, UUID workerId) {
        WorkerRoster.Entry entry = WorkerRoster.get(server).entry(workerId).orElse(null);
        if (entry == null) {
            return Component.translatable("message.villagerhuts.assign_worker_missing");
        }
        if (!entry.canCommand(player.getUUID())) {
            return Component.translatable("message.villagerhuts.not_owner", workerName(entry), WorkerRoster.playerName(server, entry.owner()));
        }
        return null;
    }

    /** Why commandWorker turned this down, for the panel (null: nothing worth saying). */
    public static @Nullable Component commandRefusal(MinecraftServer server, Player player, UUID workerId, WorkerCommandPayload.Command command) {
        Component refused = accessRefusal(server, player, workerId);
        if (refused != null) {
            return refused;
        }
        WorkerRoster.Entry entry = WorkerRoster.get(server).entry(workerId).orElseThrow();
        if (WorkerRoster.loaded(server, workerId, entry) == null) {
            return Component.translatable("message.villagerhuts.too_far", workerName(entry));
        }
        if (command == WorkerCommandPayload.Command.TAKE_TOOL && !entry.owner().equals(player.getUUID())) {
            return Component.translatable("message.villagerhuts.owner_takes_tool", workerName(entry));
        }
        return null;
    }

    /** Why shareWorker turned this down, for the panel (null: nothing worth saying, e.g. it was already done). */
    public static @Nullable Component shareRefusal(MinecraftServer server, Player actor, UUID workerId, UUID target, ShareWorkerPayload.Action action) {
        Component refused = accessRefusal(server, actor, workerId);
        if (refused != null) {
            return refused;
        }
        WorkerRoster.Entry entry = WorkerRoster.get(server).entry(workerId).orElseThrow();
        boolean byOwner = entry.owner().equals(actor.getUUID());
        if (!byOwner && !(action == ShareWorkerPayload.Action.UNSHARE && target.equals(actor.getUUID()))) {
            return Component.translatable("message.villagerhuts.owner_shares", workerName(entry));
        }
        if (action == ShareWorkerPayload.Action.SHARE && server.getPlayerList().getPlayer(target) == null) {
            return Component.translatable("message.villagerhuts.not_online", WorkerRoster.playerName(server, target));
        }
        return null;
    }

    /**
     * Gives all of the player's own workers the same order (WORK, FOLLOW or STAY): the ones that are loaded (near
     * enough to a player); the rest are far away. Workers shared with them, or their party's, are left alone.
     * @return what happened, for the panel
     */
    public static Component orderAll(MinecraftServer server, Player player, WorkerCommandPayload.Command order) {
        if (order != WorkerCommandPayload.Command.WORK && order != WorkerCommandPayload.Command.FOLLOW && order != WorkerCommandPayload.Command.STAY) {
            return Component.empty();
        }
        WorkerRoster roster = WorkerRoster.get(server);
        int done = 0;
        int far = 0;
        for (Map.Entry<UUID, WorkerRoster.Entry> row : roster.forPlayer(player.getUUID())) {
            if (!row.getValue().owner().equals(player.getUUID())) {
                continue;
            }
            WorkerEntity worker = WorkerRoster.loaded(server, row.getKey(), row.getValue());
            if (worker == null) {
                far++;
                continue;
            }
            switch (order) {
                case WORK -> worker.setOrder(WorkerEntity.Order.WORK);
                case FOLLOW -> worker.setOrder(WorkerEntity.Order.FOLLOW, player.getUUID());
                default -> worker.setOrder(WorkerEntity.Order.STAY);
            }
            roster.update(worker);
            done++;
        }
        Component result = Component.translatable("message.villagerhuts.ordered_all." + order.name().toLowerCase(java.util.Locale.ROOT), done);
        return far == 0 ? result : Component.translatable("message.villagerhuts.ordered_all_far", result, far);
    }

    /**
     * Where a worker is, for chat: where it is now if it's loaded, otherwise where it was last (it doesn't move while
     * far away). With `onMap`, also a waypoint line in Xaero's minimap format: Xaero's shows it as a link that
     * adds a map marker.
     */
    public static List<Component> locationMessages(WorkerRoster.Entry entry, @Nullable WorkerEntity loaded, boolean onMap) {
        BlockPos pos = loaded != null ? loaded.blockPosition() : entry.position()
                .or(() -> entry.farm().map(BoundingBox::getCenter)).or(entry::storage).or(entry::bed).orElse(null);
        if (pos == null) {
            return List.of(Component.translatable("message.villagerhuts.location_unknown", workerName(entry)));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(entry.dimension() == Level.OVERWORLD
                ? Component.translatable("message.villagerhuts.location", workerName(entry), pos.getX(), pos.getY(), pos.getZ())
                : Component.translatable("message.villagerhuts.location_in", workerName(entry), pos.getX(), pos.getY(), pos.getZ(),
                        entry.dimension().location().toString()));
        if (onMap) {
            String name = entry.customName().orElse("Worker " + entry.number()).replace(':', ' ');
            lines.add(Component.literal("xaero-waypoint:" + name + ":" + name.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + ":"
                    + pos.getX() + ":" + pos.getY() + ":" + pos.getZ() + ":" + Math.floorMod(entry.number() - 1, 16) + ":false:0:Internal-"
                    + xaeroDimension(entry.dimension()) + "-waypoints").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    // How Xaero's minimap names a dimension in its waypoint lines.
    private static String xaeroDimension(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return "overworld";
        }
        if (dimension == Level.NETHER) {
            return "the-nether";
        }
        if (dimension == Level.END) {
            return "the-end";
        }
        return "dim%" + dimension.location().getNamespace() + "$" + dimension.location().getPath().replace('/', '%');
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
