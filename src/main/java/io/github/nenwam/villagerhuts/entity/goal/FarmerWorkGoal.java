package io.github.nenwam.villagerhuts.entity.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.github.nenwam.villagerhuts.Config;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.farm.FarmArea;
import io.github.nenwam.villagerhuts.farm.FarmPlan;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Farmer job, on its assigned farm:
 *  - a farm it's making (see FarmPlan): put in the planned water first, then till the land;
 *  - harvest ripe crops into the worker's inventory and replant, and plant seeds on any empty farmland;
 *  - use bone meal (fetched from its chest by FetchBoneMealGoal) on crops that are still growing.
 * Follows the same rules as vanilla farmer villagers (CropBlock crops on farmland, seeds from a tag).
 * After each look over the farm it tells the worker whether all its work is done (DepositItemsGoal then
 * puts everything away).
 *
 * The farmer walks onto the crop's own spot (via a route that really gets there, e.g. through
 * the gap in the fence) and only works when nothing solid is in between, so it never
 * harvests through a fence or wall. For water it stands beside the spot instead.
 */
public class FarmerWorkGoal extends Goal implements DescribesActivity {
    private static final int SEARCH_INTERVAL = 20;           // look for work once a second
    private static final int GIVE_UP_TICKS = 300;            // can't reach a crop in 15s? give up on it
    private static final int SKIP_TICKS = 1200;              // ...and ignore that spot for a minute
    private static final double REACH = 1.5;
    private static final int ROUTE_TRIES = 4;              // nearest spots to try planning a route to, per search

    private enum Task { WATER, TILL, CROP, BONE_MEAL }

    /** Where to stand, the block to work on, and what to do there. Water first, then the nearest. */
    private record Work(BlockPos standAt, BlockPos at, Task task) {}

    private final WorkerEntity worker;
    private final double speed;
    private final Map<BlockPos, Integer> skipUntil = new HashMap<>();
    private @Nullable Work target;
    private @Nullable Path path;
    private int ticksOnTarget;
    private int nextSearchTick;
    private int nextActionTick;

    public FarmerWorkGoal(WorkerEntity worker, double speed) {
        this.worker = worker;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.worker.getJob() != WorkerJob.FARMER || this.worker.getFarmArea() == null) {
            return false;
        }
        // Pockets full: stop and let DepositItemsGoal empty them first.
        if (this.worker.tickCount < this.nextSearchTick || !this.worker.hasFreeSlot() || !WorkReach.canPlan(this.worker)) {
            return false;
        }
        this.nextSearchTick = this.worker.tickCount + SEARCH_INTERVAL;
        // Nearest first, but only spots it has a full route to.
        int tries = 0;
        for (Work work : this.findWork()) {
            if (tries++ >= ROUTE_TRIES) {
                break;
            }
            Path route = WorkReach.exactPath(this.worker, work.standAt());
            if (route != null) {
                this.target = work;
                this.path = route;
                this.worker.setFarmUnreachable(false);
                return true;
            }
            this.skipUntil.put(work.at(), this.worker.tickCount + SKIP_TICKS); // fenced off from this side, etc.
        }
        if (tries > 0) {
            this.worker.setFarmUnreachable(true); // there was work, but no way to walk to any of it
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.worker.getJob() == WorkerJob.FARMER && this.ticksOnTarget < GIVE_UP_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.ticksOnTarget = 0;
        this.worker.getNavigation().moveTo(this.path, this.speed);
    }

    @Override
    public void stop() {
        if (this.target != null && this.ticksOnTarget >= GIVE_UP_TICKS) {
            this.skipUntil.put(this.target.at(), this.worker.tickCount + SKIP_TICKS);
        }
        this.target = null;
        this.path = null;
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        Work work = this.target;
        if (work == null) {
            return;
        }
        BlockPos pos = work.at();
        this.worker.markWorked(); // walking to a crop is work too (so it isn't "idle" and doesn't put its seeds away)
        this.worker.getLookControl().setLookAt(Vec3.atCenterOf(pos));

        if (!WorkReach.canTouch(this.worker, pos, REACH)) {
            this.ticksOnTarget++; // only time spent walking counts toward giving up
            if (this.ticksOnTarget % 20 == 0 && this.worker.getNavigation().isDone() && WorkReach.canPlan(this.worker)) {
                Path route = WorkReach.exactPath(this.worker, work.standAt()); // got knocked off course; plan again
                if (route == null) {
                    this.skipUntil.put(pos, this.worker.tickCount + SKIP_TICKS);
                    this.target = null;
                } else {
                    this.worker.getNavigation().moveTo(route, this.speed);
                }
            }
            return;
        }
        // Wait a moment between crops so it looks like work (and is configurable).
        if (this.worker.tickCount < this.nextActionTick) {
            return;
        }

        ServerLevel level = (ServerLevel) this.worker.level();
        boolean worked = switch (work.task()) {
            case WATER -> this.putWater(level, pos);
            case TILL -> this.till(level, pos);
            case CROP -> this.workOn(level, pos);
            case BONE_MEAL -> this.useBoneMeal(level, pos);
        };
        if (worked) {
            this.worker.markWorked();
        } else {
            this.skipUntil.put(pos, this.worker.tickCount + SKIP_TICKS); // e.g. seeds can't grow here
        }
        this.nextActionTick = this.worker.tickCount + Config.FARMER_WORK_DELAY.getAsInt();
        this.nextSearchTick = 0; // go straight on to the next crop
        this.target = null;
    }

    @Override
    public Component describeActivity() {
        Work work = this.target;
        if (work != null) {
            BlockState state = this.worker.level().getBlockState(work.at());
            switch (work.task()) {
                case WATER -> {
                    return Component.translatable("activity.villagerhuts.watering");
                }
                case TILL -> {
                    return Component.translatable("activity.villagerhuts.tilling");
                }
                case BONE_MEAL -> {
                    return Component.translatable("activity.villagerhuts.bone_meal", state.getBlock().getName());
                }
                case CROP -> {
                    if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                        return Component.translatable("activity.villagerhuts.harvesting", state.getBlock().getName());
                    }
                }
            }
        }
        return Component.translatable("activity.villagerhuts.planting");
    }

    /** Everything it could do on its farm right now: water first, then the rest nearest first. */
    private List<Work> findWork() {
        List<Work> found = new ArrayList<>();
        ServerLevel level = (ServerLevel) this.worker.level();
        BoundingBox area = this.worker.getFarmArea();
        if (area == null) {
            return found;
        }
        int now = this.worker.tickCount;
        this.skipUntil.values().removeIf(until -> until < now);
        boolean hasSeeds = this.findSeedSlot(null) >= 0;
        boolean hasBoneMeal = this.worker.hasBoneMeal();
        boolean tills = this.worker.tillsLand();
        boolean blocked = false; // there's work it's skipping for now (e.g. it can't get to it): not done, just stuck

        if (tills) {
            for (BlockPos water : this.worker.getPlannedWater()) {
                if (!level.isLoaded(water)) {
                    continue;
                }
                if (this.skipUntil.containsKey(water)) {
                    blocked = true;
                    continue;
                }
                if (!FarmPlan.canHoldWater(level, water)) {
                    this.worker.finishPlannedWater(water); // built over or dug out since: drop it
                    continue;
                }
                BlockPos standAt = this.besideForWater(level, water);
                if (standAt != null) {
                    found.add(new Work(standAt, water, Task.WATER));
                }
            }
        }

        // Counts leave out spots it's skipping for now (unreachable, or seeds can't grow there), so a spot it
        // can't use doesn't keep sending it to the chest.
        int farmland = 0;
        int empty = 0;
        int ripeCount = 0;
        int growing = 0;
        int toTill = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            // Part of an assigned farm can be in a chunk that isn't loaded; reading it would load it.
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockPos ground = pos.below();
            boolean skipped = this.skipUntil.containsKey(pos);
            if (!isFarmland(level.getBlockState(ground))) {
                if (tills && FarmPlan.isTillable(level, ground) && !this.worker.isPlannedWater(ground)) {
                    if (skipped) {
                        blocked = true;
                    } else {
                        toTill++;
                        found.add(new Work(pos.immutable(), pos.immutable(), Task.TILL));
                    }
                }
                continue;
            }
            farmland++;
            BlockState state = level.getBlockState(pos);
            boolean crop = state.getBlock() instanceof CropBlock;
            boolean ripe = crop && ((CropBlock) state.getBlock()).isMaxAge(state);
            if (skipped) {
                blocked |= ripe || (hasSeeds && state.isAir()) || (crop && hasBoneMeal);
                continue;
            }
            if (state.isAir()) {
                empty++;
            }
            if (ripe) {
                ripeCount++;
            } else if (crop) {
                growing++;
            }
            if (ripe || (hasSeeds && state.isAir())) {
                found.add(new Work(pos.immutable(), pos.immutable(), Task.CROP));
            } else if (crop && hasBoneMeal) {
                found.add(new Work(pos.immutable(), pos.immutable(), Task.BONE_MEAL));
            }
        }
        // All done: nothing to do here with what it carries (not even work it's stuck on), and nothing in the
        // chest that would give it more.
        boolean done = found.isEmpty() && !blocked
                && !(empty + toTill > 0 && !hasSeeds && this.worker.chestHas(WorkerEntity::isPlantableSeed))
                && !(growing > 0 && !hasBoneMeal && this.worker.chestHas(WorkerEntity::isBoneMeal));
        this.worker.setFarmCounts(farmland + toTill, empty + toTill, ripeCount, growing, done, blocked);
        found.sort(Comparator.<Work>comparingInt(work -> work.task() == Task.WATER ? 0 : 1)
                .thenComparingDouble(work -> work.standAt().distToCenterSqr(this.worker.position())));
        return found;
    }

    // A spot next to the water's hole to stand on while pouring it, nearest the worker.
    private @Nullable BlockPos besideForWater(ServerLevel level, BlockPos water) {
        BlockPos best = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos spot = water.relative(side).above();
            if (WorkReach.isStandable(level, spot)
                    && (best == null || spot.distToCenterSqr(this.worker.position()) < best.distToCenterSqr(this.worker.position()))) {
                best = spot;
            }
        }
        return best;
    }

    /** Harvests a ripe crop at pos (if any), then plants seeds there. Returns true if anything happened. */
    private boolean workOn(ServerLevel level, BlockPos pos) {
        if (!isFarmland(level.getBlockState(pos.below()))) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        boolean worked = false;
        Item replant = null;

        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            replant = state.getCloneItemStack(level, pos, false).getItem(); // e.g. wheat -> wheat seeds
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, this.worker, this.worker.getMainHandItem());
            level.destroyBlock(pos, false, this.worker);
            drops.forEach(drop -> {
                this.worker.recordCollected(drop);
                this.worker.storeOrDrop(level, drop);
            });
            worked = true;
            state = level.getBlockState(pos);
        }

        if (state.isAir()) {
            worked |= this.plant(level, pos, replant);
        }
        return worked;
    }

    /** Turns the ground under pos into farmland, clearing short grass and the like off it first. */
    private boolean till(ServerLevel level, BlockPos pos) {
        BlockPos ground = pos.below();
        if (!FarmPlan.isTillable(level, ground)) {
            return false;
        }
        this.clearPlant(level, pos);
        level.setBlockAndUpdate(ground, Blocks.FARMLAND.defaultBlockState());
        level.gameEvent(GameEvent.BLOCK_CHANGE, ground, GameEvent.Context.of(this.worker, Blocks.FARMLAND.defaultBlockState()));
        level.playSound(null, ground, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Puts a water source in the ground at pos, as planned for the farm it's making. */
    private boolean putWater(ServerLevel level, BlockPos pos) {
        if (!this.worker.isPlannedWater(pos) || !FarmPlan.canHoldWater(level, pos)) {
            this.worker.finishPlannedWater(pos);
            return false;
        }
        this.clearPlant(level, pos.above());
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        level.gameEvent(GameEvent.FLUID_PLACE, pos, GameEvent.Context.of(this.worker, Blocks.WATER.defaultBlockState()));
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.worker.finishPlannedWater(pos);
        return true;
    }

    /** Uses one bone meal on the growing crop at pos. */
    private boolean useBoneMeal(ServerLevel level, BlockPos pos) {
        int slot = this.findSlot(WorkerEntity::isBoneMeal);
        BlockState state = level.getBlockState(pos);
        if (slot < 0 || !(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state)) {
            return false;
        }
        ItemStack boneMeal = this.worker.getInventory().getItem(slot);
        if (!BoneMealItem.applyBonemeal(boneMeal, level, pos, null)) {
            return false;
        }
        level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
        this.worker.getInventory().setChanged();
        return true;
    }

    // Short grass, ferns and the like come off with whatever they drop (grass can drop seeds).
    private void clearPlant(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.canBeReplaced() || !state.getFluidState().isEmpty()) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, this.worker, this.worker.getMainHandItem());
        level.destroyBlock(pos, false, this.worker);
        drops.forEach(drop -> this.worker.storeOrDrop(level, drop));
    }

    private boolean plant(ServerLevel level, BlockPos pos, @Nullable Item preferred) {
        int slot = this.findSeedSlot(preferred);
        if (slot < 0) {
            return false;
        }
        ItemStack seeds = this.worker.getInventory().getItem(slot);
        BlockState plant = ((BlockItem) seeds.getItem()).getBlock().defaultBlockState();
        if (!plant.canSurvive(level, pos)) {
            return false;
        }
        level.setBlockAndUpdate(pos, plant);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.worker, plant));
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        seeds.shrink(1);
        this.worker.getInventory().setChanged();
        return true;
    }

    /** Slot of the preferred seed if the worker has it, otherwise of any plantable seed, or -1. */
    private int findSeedSlot(@Nullable Item preferred) {
        SimpleContainer inventory = this.worker.getInventory();
        int fallback = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!WorkerEntity.isPlantableSeed(stack)) {
                continue;
            }
            if (stack.getItem() == preferred) {
                return i;
            }
            if (fallback < 0) {
                fallback = i;
            }
        }
        return fallback;
    }

    /** Slot of the first stack that matches, or -1. */
    private int findSlot(Predicate<ItemStack> matches) {
        SimpleContainer inventory = this.worker.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (matches.test(inventory.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isFarmland(BlockState state) {
        return FarmArea.isFarmland(state);
    }
}
