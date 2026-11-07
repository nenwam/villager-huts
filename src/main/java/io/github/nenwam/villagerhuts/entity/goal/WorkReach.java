package io.github.nenwam.villagerhuts.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Checks that keep workers from working through walls and fences: they plan a route that ends
 * exactly where they need to stand, and only act when nothing solid is in the way.
 */
public final class WorkReach {

    /** Routes can only be planned while standing on something (not mid-jump). */
    public static boolean canPlan(PathfinderMob mob) {
        return mob.onGround() || mob.isInLiquid();
    }

    /** A route that ends exactly at pos, or null if there isn't one (e.g. pos is behind a wall). */
    public static @Nullable Path exactPath(PathfinderMob mob, BlockPos pos) {
        Path path = mob.getNavigation().createPath(pos, 0);
        return path != null && path.canReach() ? path : null;
    }

    /** Close enough to touch the target, with no wall, fence or door between the worker's eyes and it. */
    public static boolean canTouch(PathfinderMob mob, BlockPos target, double reach) {
        return target.closerToCenterThan(mob.position(), reach) && hasClearLine(mob, target);
    }

    public static boolean hasClearLine(PathfinderMob mob, BlockPos target) {
        BlockHitResult hit = mob.level().clip(new ClipContext(
                mob.getEyePosition(), Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    /** Room to stand: nothing solid at feet or head height, and a solid floor. */
    public static boolean isStandable(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private WorkReach() {}
}
