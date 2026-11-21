package io.github.nenwam.villagerhuts.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.nenwam.villagerhuts.Config;
import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.compat.Space;
import io.github.nenwam.villagerhuts.farm.FarmArea;
import io.github.nenwam.villagerhuts.farm.FarmPlan;
import io.github.nenwam.villagerhuts.network.AssignModePayload;
import io.github.nenwam.villagerhuts.registry.ModTags;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Giving a worker its farm, its chest and its bed. Right-click your worker with an empty hand to select it,
 * then right-click the farm (any block on or beside its farmland), the chest (or barrel), or a bed.
 * Or shift + right-click a farmer, then right-click the ground: it makes a new farm there (see FarmPlan).
 * A fighter gets a post instead of a farm: right-click any block (that isn't a chest or a bed) and it stands
 * guard there, over Config fighterGuardRadius blocks all round.
 * While a worker is selected, the client previews what you're looking at (client/AssignPreview), and
 * your next right-click on a block goes here instead of to the block, so the chest doesn't open.
 * Right-click the worker again to cancel; the selection also runs out after a minute.
 *
 * Limits (Config screen): a farm up to farmerMaxFarmSize blocks across (a new one, farmerNewFarmSize),
 * within farmerMaxFarmDistance blocks of the worker (so is a post); a chest or bed within farmerMaxFarmDistance
 * blocks of the worker's farm or post (or of the worker, if it has neither yet). A bed can only be one worker's.
 * Giving it any of these sets it working.
 */
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
        boolean fighter = worker.getJob().isFighter();
        newFarm = newFarm && !fighter; // fighters don't farm
        Selection current = selection(player);
        if (current != null && current.worker().equals(worker.getUUID()) && current.newFarm() == newFarm) {
            clear(player);
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.assign_cancelled"));
            return;
        }
        SELECTED.put(player.getUUID(), new Selection(worker.getUUID(), newFarm, player.level().getGameTime() + TIMEOUT_TICKS));
        Component name = worker.hasCustomName() ? worker.getCustomName() : worker.getJob().displayName();
        ServerPlayNetworking.send(player, new AssignModePayload(Optional.of(new AssignModePayload.Selected(
                worker.getUUID(), name, newFarm, Optional.ofNullable(worker.getWorkArea()), Config.FARMER_MAX_FARM_SIZE.getAsInt(),
                Config.FARMER_NEW_FARM_SIZE.getAsInt(), Config.FARMER_MAX_FARM_DISTANCE.getAsInt(), TIMEOUT_TICKS,
                fighter, Config.FIGHTER_GUARD_RADIUS.getAsInt()))));
        player.level().playSound(null, worker.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /**
     * Gives the worker the bed or chest at this block, or else the farm around it (a fighter: a post here).
     * Changes nothing if it can't have it.
     */
    public static Outcome assign(ServerLevel level, WorkerEntity worker, BlockPos clicked) {
        int maxDistance = Config.FARMER_MAX_FARM_DISTANCE.getAsInt();
        if (isBed(level, clicked)) {
            BlockPos head = bedHead(level, clicked);
            if (!isWithin(chestAnchor(worker.getWorkArea(), worker.blockPosition()), head, maxDistance)) {
                return new Outcome(false, Component.translatable("message.villagerhuts.bed_too_far", maxDistance));
            }
            WorkerRoster roster = WorkerRoster.get(level.getServer());
            Optional<UUID> other = roster.bedOwner(level.dimension(), head, worker.getUUID());
            if (other.isPresent()) {
                Component name = roster.entry(other.get()).flatMap(WorkerRoster.Entry::customName).<Component>map(Component::literal)
                        .orElseGet(() -> Component.translatable("gui.villagerhuts.another_worker"));
                return new Outcome(false, Component.translatable("message.villagerhuts.bed_taken", name));
            }
            worker.assignBed(head);
            return warnIfDangerous(level, worker, head.above(), Component.translatable("message.villagerhuts.bed_assigned"));
        }
        if (isStorage(level, clicked)) {
            if (!isWithin(chestAnchor(worker.getWorkArea(), worker.blockPosition()), clicked, maxDistance)) {
                return new Outcome(false, Component.translatable("message.villagerhuts.chest_too_far", maxDistance));
            }
            worker.assignStorage(clicked);
            return warnIfDangerous(level, worker, clicked, Component.translatable(worker.getJob().isFighter()
                    ? "message.villagerhuts.fighter_chest_assigned" : "message.villagerhuts.chest_assigned"));
        }
        if (worker.getJob().isFighter()) {
            BlockPos post = postAt(level, clicked);
            if (!isWithin(worker.blockPosition(), post, maxDistance)) {
                return new Outcome(false, Component.translatable("message.villagerhuts.post_too_far", maxDistance));
            }
            worker.assignGuardPost(post);
            int size = 2 * Config.FIGHTER_GUARD_RADIUS.getAsInt() + 1;
            return warnIfDangerous(level, worker, post.above(), Component.translatable("message.villagerhuts.post_assigned", size, size));
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
        return warnIfDangerous(level, worker, middle(box), Component.translatable("message.villagerhuts.farm_assigned",
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
        return warnIfDangerous(level, worker, middle(box), Component.translatable("message.villagerhuts.farm_created",
                box.getXSpan(), box.getZSpan(), plan.till().size(), plan.water().size()));
    }

    // Given a place it can't live (Ad Astra: no air, too hot or too cold), it has it anyway, with a warning.
    private static Outcome warnIfDangerous(ServerLevel level, WorkerEntity worker, BlockPos at, Component done) {
        Space.Danger danger = Space.dangerFor(worker, level, at);
        return new Outcome(true, danger == null ? done : Component.translatable("message.villagerhuts.assigned_in_danger", done, danger.warning()));
    }

    // The middle of a farm, a block above the ground: where a farmer stands to work it.
    private static BlockPos middle(BoundingBox box) {
        return new BlockPos((box.minX() + box.maxX()) / 2, box.minY(), (box.minZ() + box.maxZ()) / 2).above();
    }

    /** A block a worker can keep things in: in the worker_storage tag (chests, barrels, drawers...) and with item storage. */
    public static boolean isStorage(Level level, BlockPos pos) {
        return WorkerStorage.at(level, pos) != null;
    }

    /** A bed a worker can sleep in: in the worker_beds tag (beds, Comforts' sleeping bags and hammocks), and a bed block. */
    public static boolean isBed(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModTags.WORKER_BEDS) && state.getBlock() instanceof AbstractBedBlock;
    }

    /** The head end of the bed at pos (where a sleeper's head goes, and where beds are tracked). */
    public static BlockPos bedHead(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getValue(AbstractBedBlock.PART) == BedPart.HEAD ? pos : pos.relative(AbstractBedBlock.getConnectedDirection(state));
    }

    /** The post a fighter gets for this clicked block: the spot to stand on it (or the block itself, if it's grass or air). */
    public static BlockPos postAt(BlockGetter level, BlockPos clicked) {
        return level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.above();
    }

    /** Where a chest's (or bed's) distance is measured from: the middle of the worker's farm or guard area, or the worker if it has neither. */
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

    /** Hooks assigning into the game's events (from VillagerHuts.onInitialize). */
    public static void register() {
        UseBlockCallback.EVENT.register(WorkerAssigning::onUseBlock);
        ServerPlayConnectionEvents.DISCONNECT.register((connection, server) -> SELECTED.remove(connection.getPlayer().getUUID()));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> clear(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> clear(newPlayer));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SELECTED.clear());
    }

    // With a worker selected, right-clicking a block gives the worker that block (or the farm around it) instead of using it.
    private static InteractionResult onUseBlock(Player user, Level world, InteractionHand hand, BlockHitResult hit) {
        if (!(user instanceof ServerPlayer player) || !(world instanceof ServerLevel level)) {
            return InteractionResult.PASS; // the client cancels its side itself (client/AssignPreview), so it doesn't predict opening the chest
        }
        Selection selection = selection(player);
        if (selection == null) {
            return InteractionResult.PASS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos clicked = hit.getBlockPos();
        WorkerEntity worker = level.getEntity(selection.worker()) instanceof WorkerEntity found && found.isAlive() ? found : null;
        if (worker != null && !worker.canCommand(player)) { // its owner stopped sharing it with them meanwhile
            clear(player);
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.no_longer_shared"));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (worker == null) {
            clear(player);
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.assign_worker_missing"));
            return InteractionResult.SUCCESS_SERVER;
        }
        Outcome outcome = selection.newFarm() ? createFarm(level, worker, clicked) : assign(level, worker, clicked);
        player.sendOverlayMessage(outcome.message());
        if (outcome.assigned()) {
            clear(player);
            level.playSound(null, clicked, worker.getJob().isFighter() ? SoundEvents.VILLAGER_WORK_WEAPONSMITH
                    : SoundEvents.VILLAGER_WORK_FARMER, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        // Not assigned: stay selected, so the player can try another spot.
        return InteractionResult.SUCCESS_SERVER;
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
            ServerPlayNetworking.send(player, AssignModePayload.NONE);
        }
    }

    private WorkerAssigning() {}
}
