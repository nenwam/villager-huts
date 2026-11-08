package io.github.nenwam.villagerhuts.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.nenwam.villagerhuts.Config;
import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.farm.FarmArea;
import io.github.nenwam.villagerhuts.farm.FarmPlan;
import io.github.nenwam.villagerhuts.network.AssignModePayload;
import io.github.nenwam.villagerhuts.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Giving a worker its farm and its chest. Right-click your worker with an empty hand to select it,
 * then right-click the farm (any block on or beside its farmland) or the chest (or barrel).
 * Or shift + right-click it, then right-click the ground: it makes a new farm there (see FarmPlan).
 * While a worker is selected, the client previews what you're looking at (client/AssignPreview), and
 * your next right-click on a block goes here instead of to the block, so the chest doesn't open.
 * Right-click the worker again to cancel; the selection also runs out after a minute.
 *
 * Limits (Config screen): a farm up to farmerMaxFarmSize blocks across (a new one, farmerNewFarmSize),
 * within farmerMaxFarmDistance blocks of the worker; a chest within farmerMaxFarmDistance blocks of the
 * worker's farm (or of the worker, if it doesn't have a farm yet). Giving it any of these sets it working.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID)
public final class WorkerAssigning {
    public static final int TIMEOUT_TICKS = 1200;

    private record Selection(UUID worker, boolean newFarm, long expiresAt) {}

    private static final Map<UUID, Selection> SELECTED = new HashMap<>(); // by player

    /** What right-clicking a block did: whether the worker got it, and what to tell the player. */
    public record Outcome(boolean assigned, Component message) {}

    /**
     * The player right-clicked one of their workers: select it (to give it a farm or chest, or with newFarm, to
     * make a new farm), or cancel if it was already selected that way.
     */
    public static void toggle(ServerPlayer player, WorkerEntity worker, boolean newFarm) {
        Selection current = selection(player);
        if (current != null && current.worker().equals(worker.getUUID()) && current.newFarm() == newFarm) {
            clear(player);
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.assign_cancelled"));
            return;
        }
        SELECTED.put(player.getUUID(), new Selection(worker.getUUID(), newFarm, player.level().getGameTime() + TIMEOUT_TICKS));
        Component name = worker.hasCustomName() ? worker.getCustomName() : worker.getJob().displayName();
        PacketDistributor.sendToPlayer(player, new AssignModePayload(Optional.of(new AssignModePayload.Selected(
                worker.getUUID(), name, newFarm, Optional.ofNullable(worker.getFarmArea()), Config.FARMER_MAX_FARM_SIZE.getAsInt(),
                Config.FARMER_NEW_FARM_SIZE.getAsInt(), Config.FARMER_MAX_FARM_DISTANCE.getAsInt(), TIMEOUT_TICKS))));
        player.level().playSound(null, worker.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** Gives the worker the chest at this block, or else the farm around it. Changes nothing if it can't have it. */
    public static Outcome assign(ServerLevel level, WorkerEntity worker, BlockPos clicked) {
        int maxDistance = Config.FARMER_MAX_FARM_DISTANCE.getAsInt();
        if (isStorage(level, clicked)) {
            if (!isWithin(chestAnchor(worker.getFarmArea(), worker.blockPosition()), clicked, maxDistance)) {
                return new Outcome(false, Component.translatable("message.villagerhuts.chest_too_far", maxDistance));
            }
            worker.assignStorage(clicked);
            return new Outcome(true, Component.translatable("message.villagerhuts.chest_assigned"));
        }
        FarmArea area = FarmArea.find(level, clicked, Config.FARMER_MAX_FARM_SIZE.getAsInt());
        BoundingBox box = area.box();
        if (box == null) {
            return new Outcome(false, Component.translatable("message.villagerhuts.no_farmland"));
        }
        if (!area.isNear(worker.blockPosition(), maxDistance)) {
            return new Outcome(false, Component.translatable("message.villagerhuts.farm_too_far", maxDistance));
        }
        worker.assignFarm(box);
        return new Outcome(true, Component.translatable("message.villagerhuts.farm_assigned",
                box.getXSpan(), box.getZSpan(), area.farmland().size()));
    }

    /** Plans a new farm around this block (see FarmPlan) and has the worker make it. Changes nothing if there's nothing to farm. */
    public static Outcome createFarm(ServerLevel level, WorkerEntity worker, BlockPos clicked) {
        int maxDistance = Config.FARMER_MAX_FARM_DISTANCE.getAsInt();
        FarmPlan plan = FarmPlan.find(level, clicked, Config.FARMER_NEW_FARM_SIZE.getAsInt());
        BoundingBox box = plan.box();
        if (box == null) {
            return new Outcome(false, Component.translatable("message.villagerhuts.nothing_to_farm"));
        }
        if (!isWithin(worker.blockPosition(), box.getCenter(), maxDistance)) {
            return new Outcome(false, Component.translatable("message.villagerhuts.farm_too_far", maxDistance));
        }
        worker.createFarm(box, plan.water());
        return new Outcome(true, Component.translatable("message.villagerhuts.farm_created",
                box.getXSpan(), box.getZSpan(), plan.till().size(), plan.water().size()));
    }

    /** A block a worker can keep things in: in the worker_storage tag (chests and barrels) and with an inventory. */
    public static boolean isStorage(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModTags.WORKER_STORAGE) && HopperBlockEntity.getContainerAt(level, pos) != null;
    }

    /** Where a chest's distance is measured from: the middle of the worker's farm, or the worker if it has none. */
    public static BlockPos chestAnchor(@Nullable BoundingBox farm, BlockPos worker) {
        return farm != null ? farm.getCenter() : worker;
    }

    /** Within maxDistance blocks, measured flat (up and down don't count). */
    public static boolean isWithin(BlockPos from, BlockPos to, int maxDistance) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return dx * dx + dz * dz <= (double) maxDistance * maxDistance;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return; // the client cancels its side itself (client/AssignPreview), so it doesn't predict opening the chest
        }
        Selection selection = selection(player);
        if (selection == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        WorkerEntity worker = level.getEntity(selection.worker()) instanceof WorkerEntity found && found.isAlive() ? found : null;
        if (worker == null) {
            clear(player);
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.assign_worker_missing"));
            return;
        }
        Outcome outcome = selection.newFarm() ? createFarm(level, worker, event.getPos()) : assign(level, worker, event.getPos());
        player.sendOverlayMessage(outcome.message());
        if (outcome.assigned()) {
            clear(player);
            level.playSound(null, event.getPos(), SoundEvents.VILLAGER_WORK_FARMER, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        // Not assigned: stay selected, so the player can try another spot.
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SELECTED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SELECTED.clear();
    }

    private static @Nullable Selection selection(ServerPlayer player) {
        Selection selection = SELECTED.get(player.getUUID());
        if (selection != null && player.level().getGameTime() >= selection.expiresAt()) {
            SELECTED.remove(player.getUUID()); // ran out (the client times it out too)
            return null;
        }
        return selection;
    }

    private static void clear(ServerPlayer player) {
        if (SELECTED.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayer(player, AssignModePayload.NONE);
        }
    }

    private WorkerAssigning() {}
}
