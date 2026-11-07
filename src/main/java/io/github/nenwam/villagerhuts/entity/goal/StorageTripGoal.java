package io.github.nenwam.villagerhuts.entity.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A trip to a storage block (chest or barrel by default) in the worker's hut: picks the nearest
 * useful one, walks to a floor spot right beside it (so always on the same side of any wall),
 * and uses it once it can reach it with nothing in between. Subclasses decide when a trip is
 * needed and what to do there (store the harvest, fetch seeds, ...).
 */
abstract class StorageTripGoal extends Goal implements DescribesActivity {
    private static final int GIVE_UP_TICKS = 300;
    private static final double REACH = 1.5;

    protected final WorkerEntity worker;
    private final double speed;
    private @Nullable BlockPos storage;
    private @Nullable BlockPos standAt;
    private @Nullable Path path;
    private int ticks;
    private int nextTryTick;

    protected StorageTripGoal(WorkerEntity worker, double speed) {
        this.worker = worker;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** Whether the worker needs to make this trip now. */
    protected abstract boolean needsTrip();

    /** Whether this storage block is worth walking to (e.g. it has seeds in it). */
    protected abstract boolean isUseful(Container container);

    /** Does the job at the storage block. Returns how many ticks to wait before the next trip. */
    protected abstract int useStorage(Container container);

    /** How long to wait before checking again when there's no trip to make (or no way to make it). */
    protected abstract int retryTicks();

    @Override
    public final boolean canUse() {
        if (this.worker.getHutBounds() == null || this.worker.tickCount < this.nextTryTick
                || !WorkReach.canPlan(this.worker) || !this.needsTrip()) {
            return false;
        }
        this.nextTryTick = this.worker.tickCount + this.retryTicks();
        return this.planTrip();
    }

    @Override
    public boolean canContinueToUse() {
        return this.storage != null && this.ticks < GIVE_UP_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.worker.getNavigation().moveTo(this.path, this.speed);
    }

    @Override
    public void stop() {
        this.storage = null;
        this.standAt = null;
        this.path = null;
        this.worker.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos pos = this.storage;
        if (pos == null) {
            return;
        }
        this.ticks++;
        this.worker.getLookControl().setLookAt(Vec3.atCenterOf(pos));

        if (!WorkReach.canTouch(this.worker, pos, REACH)) {
            if (this.ticks % 20 == 0 && this.worker.getNavigation().isDone() && this.standAt != null && WorkReach.canPlan(this.worker)) {
                Path route = WorkReach.exactPath(this.worker, this.standAt); // got knocked off course; plan again
                if (route == null) {
                    this.storage = null;
                } else {
                    this.worker.getNavigation().moveTo(route, this.speed);
                }
            }
            return;
        }

        Container container = HopperBlockEntity.getContainerAt(this.worker.level(), pos);
        int wait = container == null ? this.retryTicks() : this.useStorage(container);
        this.nextTryTick = this.worker.tickCount + wait;
        this.storage = null;
    }

    /** Picks the nearest useful storage block with a reachable spot beside it. Returns false if there's none. */
    private boolean planTrip() {
        ServerLevel level = (ServerLevel) this.worker.level();
        BoundingBox hut = this.worker.getHutBounds();
        if (hut == null) {
            return false;
        }
        List<BlockPos> storages = new ArrayList<>();
        boolean anyStorage = false;
        for (BlockPos pos : BlockPos.betweenClosed(hut.minX(), hut.minY(), hut.minZ(), hut.maxX(), hut.maxY(), hut.maxZ())) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(ModTags.WORKER_STORAGE)) {
                Container container = HopperBlockEntity.getContainerAt(level, pos);
                if (container != null) {
                    anyStorage = true;
                    if (this.isUseful(container)) {
                        storages.add(pos.immutable());
                    }
                }
            }
        }
        if (!anyStorage) {
            this.worker.setStorageState(WorkerEntity.StorageState.NONE);
            return false;
        }
        storages.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(this.worker.position())));

        for (BlockPos storagePos : storages) {
            List<BlockPos> spots = new ArrayList<>();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos spot = storagePos.relative(side);
                if (WorkReach.isStandable(level, spot)) {
                    spots.add(spot);
                }
            }
            spots.sort(Comparator.comparingDouble(spot -> spot.distToCenterSqr(this.worker.position())));
            for (BlockPos spot : spots) {
                Path route = WorkReach.exactPath(this.worker, spot);
                if (route != null) {
                    this.storage = storagePos;
                    this.standAt = spot;
                    this.path = route;
                    this.worker.setStorageState(WorkerEntity.StorageState.OK);
                    return true;
                }
            }
        }
        // Storage exists; if any of it was worth the trip but none could be reached, say so.
        this.worker.setStorageState(storages.isEmpty() ? WorkerEntity.StorageState.OK : WorkerEntity.StorageState.UNREACHABLE);
        return false;
    }
}
