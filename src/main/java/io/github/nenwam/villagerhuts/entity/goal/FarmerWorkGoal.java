package io.github.nenwam.villagerhuts.entity.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.nenwam.villagerhuts.Config;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.farm.FarmArea;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Farmer job: walk to ripe crops in the work area (an assigned farm, or else the hut's own grounds),
 * harvest them into the worker's inventory,
 * and replant. Also plants seeds on any empty farmland. Follows the same rules as vanilla
 * farmer villagers (CropBlock crops on farmland, seeds from a tag).
 *
 * The farmer walks onto the crop's own spot (via a route that really gets there, e.g. through
 * the gap in the fence) and only works when nothing solid is in between, so it never
 * harvests through a fence or wall.
 */
public class FarmerWorkGoal extends Goal implements DescribesActivity {
    private static final int SEARCH_INTERVAL = 20;           // look for work once a second
    private static final int GIVE_UP_TICKS = 300;            // can't reach a crop in 15s? give up on it
    private static final int SKIP_TICKS = 1200;              // ...and ignore that spot for a minute
    private static final double REACH = 1.5;
    private static final int ROUTE_TRIES = 4;              // nearest spots to try planning a route to, per search

    private final WorkerEntity worker;
    private final double speed;
    private final Map<BlockPos, Integer> skipUntil = new HashMap<>();
    private @Nullable BlockPos target;
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
        if (this.worker.getJob() != WorkerJob.FARMER || this.worker.getWorkArea() == null) {
            return false;
        }
        // Pockets full: stop and let DepositItemsGoal empty them first.
        if (this.worker.tickCount < this.nextSearchTick || !this.worker.hasFreeSlot() || !WorkReach.canPlan(this.worker)) {
            return false;
        }
        this.nextSearchTick = this.worker.tickCount + SEARCH_INTERVAL;
        // Nearest first, but only spots it has a full route to.
        int tries = 0;
        for (BlockPos pos : this.findWork()) {
            if (tries++ >= ROUTE_TRIES) {
                break;
            }
            Path route = WorkReach.exactPath(this.worker, pos);
            if (route != null) {
                this.target = pos;
                this.path = route;
                this.worker.setFarmUnreachable(false);
                return true;
            }
            this.skipUntil.put(pos, this.worker.tickCount + SKIP_TICKS); // fenced off from this side, etc.
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
            this.skipUntil.put(this.target, this.worker.tickCount + SKIP_TICKS);
        }
        this.target = null;
        this.path = null;
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos pos = this.target;
        if (pos == null) {
            return;
        }
        this.worker.markWorked(); // walking to a crop is work too (so it isn't "idle" and doesn't put its seeds away)
        this.worker.getLookControl().setLookAt(Vec3.atCenterOf(pos));

        if (!WorkReach.canTouch(this.worker, pos, REACH)) {
            this.ticksOnTarget++; // only time spent walking counts toward giving up
            if (this.ticksOnTarget % 20 == 0 && this.worker.getNavigation().isDone() && WorkReach.canPlan(this.worker)) {
                Path route = WorkReach.exactPath(this.worker, pos); // got knocked off course; plan again
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
        if (this.workOn(level, pos)) {
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
        BlockPos pos = this.target;
        if (pos != null) {
            BlockState state = this.worker.level().getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                return Component.translatable("activity.villagerhuts.harvesting", state.getBlock().getName());
            }
        }
        return Component.translatable("activity.villagerhuts.planting");
    }

    /** Spots in the work area with a ripe crop, or empty farmland if we have seeds; nearest first. */
    private List<BlockPos> findWork() {
        List<BlockPos> found = new ArrayList<>();
        ServerLevel level = (ServerLevel) this.worker.level();
        BoundingBox area = this.worker.getWorkArea();
        if (area == null) {
            return found;
        }
        int now = this.worker.tickCount;
        this.skipUntil.values().removeIf(until -> until < now);
        boolean hasSeeds = this.findSeedSlot(null) >= 0;

        int farmland = 0;
        int empty = 0;
        int ripeCount = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            // Part of an assigned farm can be in a chunk that isn't loaded; reading it would load it.
            if (!level.isLoaded(pos) || !isFarmland(level.getBlockState(pos.below()))) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            boolean ripe = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
            farmland++;
            if (state.isAir()) {
                empty++;
            }
            if (ripe) {
                ripeCount++;
            }
            if (this.skipUntil.containsKey(pos)) {
                continue;
            }
            if (ripe || (hasSeeds && state.isAir())) {
                found.add(pos.immutable());
            }
        }
        this.worker.setFarmCounts(farmland, empty, ripeCount);
        found.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(this.worker.position())));
        return found;
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

    private static boolean isFarmland(BlockState state) {
        return FarmArea.isFarmland(state);
    }
}
