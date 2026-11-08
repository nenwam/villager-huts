package io.github.nenwam.villagerhuts.farm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A new farm for a farmer to make where you aim (shift + right-click the farmer, then the ground): a
 * size-by-size square centered there, a few blocks up and down for slopes. It takes in the land a hoe
 * turns into farmland (the turns_into_farmland block tag: grass, dirt, paths) with nothing on top but
 * air or plants like grass, plus any farmland already there, and plans water so all of it stays wet.
 * Shared by the server (making it) and the client (previewing it), so the preview matches.
 *
 * @param till     land to turn into farmland
 * @param farmland farmland that's already there
 * @param water    where to put water (each one keeps the farmland up to 4 blocks around it wet)
 * @param box      the bounds of all of it, one block taller so it includes the crops; null if there's nothing to farm
 */
public record FarmPlan(BoundingBox window, List<BlockPos> till, List<BlockPos> farmland, List<BlockPos> water,
                       @Nullable BoundingBox box) {
    /** How far water reaches to keep farmland wet (vanilla: 4 blocks sideways, at its level or one above). */
    public static final int WATER_REACH = 4;

    public static FarmPlan find(BlockGetter level, BlockPos aimedAt, int size) {
        BoundingBox window = FarmArea.window(level, aimedAt, size);
        List<BlockPos> till = new ArrayList<>();
        List<BlockPos> farmland = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(window.minX(), window.minY(), window.minZ(), window.maxX(), window.maxY(), window.maxZ())) {
            if (FarmArea.isFarmland(level.getBlockState(pos))) {
                farmland.add(pos.immutable());
            } else if (isTillable(level, pos)) {
                till.add(pos.immutable());
            }
        }
        if (till.isEmpty() && farmland.isEmpty()) {
            return new FarmPlan(window, till, farmland, List.of(), null);
        }
        List<BlockPos> water = planWater(level, till, farmland);
        till.removeAll(water);
        farmland.removeAll(water);

        List<BlockPos> all = new ArrayList<>(till);
        all.addAll(farmland);
        all.addAll(water);
        BoundingBox box = BoundingBox.encapsulatingPositions(all).orElseThrow();
        return new FarmPlan(window, till, farmland, water,
                new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + 1, box.maxZ()));
    }

    /** Ground a hoe turns into farmland, with room on top (air, or a plant like grass that can be cleared). */
    public static boolean isTillable(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.TURNS_INTO_FARMLAND) && isClearOnTop(level, pos);
    }

    /** Ground (tillable land, or farmland) that water can go in without running off: solid all round and underneath. */
    public static boolean canHoldWater(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.is(BlockTags.TURNS_INTO_FARMLAND) || FarmArea.isFarmland(state)) || !isClearOnTop(level, pos)
                || !holdsWater(level, pos.below())) {
            return false;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!holdsWater(level, pos.relative(side))) {
                return false;
            }
        }
        return true;
    }

    /** Whether water here would keep this farmland wet. */
    public static boolean wets(BlockPos water, BlockPos farmland) {
        int dy = water.getY() - farmland.getY();
        return Math.abs(water.getX() - farmland.getX()) <= WATER_REACH && Math.abs(water.getZ() - farmland.getZ()) <= WATER_REACH
                && dy >= 0 && dy <= 1;
    }

    // Again and again, put water where it wets the most still-dry land, until it's all wet (or no spot helps).
    private static List<BlockPos> planWater(BlockGetter level, List<BlockPos> till, List<BlockPos> farmland) {
        List<BlockPos> spots = new ArrayList<>(till);
        spots.addAll(farmland);
        List<BlockPos> dry = new ArrayList<>(spots.stream().filter(pos -> !hasWaterNear(level, pos)).toList());
        List<BlockPos> candidates = spots.stream().filter(pos -> canHoldWater(level, pos)).toList();
        List<BlockPos> water = new ArrayList<>();
        while (!dry.isEmpty()) {
            Optional<BlockPos> best = Optional.empty();
            int bestCount = 0;
            for (BlockPos candidate : candidates) {
                if (water.contains(candidate)) {
                    continue;
                }
                int count = 0;
                for (BlockPos pos : dry) {
                    if (!pos.equals(candidate) && wets(candidate, pos)) {
                        count++;
                    }
                }
                if (count > bestCount) { // first best wins, so the client and the server pick the same spots
                    best = Optional.of(candidate);
                    bestCount = count;
                }
            }
            if (best.isEmpty()) {
                break; // what's left can't be reached by water placed on this farm
            }
            BlockPos chosen = best.get();
            water.add(chosen);
            dry.removeIf(pos -> pos.equals(chosen) || wets(chosen, pos));
        }
        return water;
    }

    private static boolean hasWaterNear(BlockGetter level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-WATER_REACH, 0, -WATER_REACH), pos.offset(WATER_REACH, 1, WATER_REACH))) {
            if (level.getFluidState(near).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClearOnTop(BlockGetter level, BlockPos pos) {
        BlockState top = level.getBlockState(pos.above());
        return top.isAir() || (top.canBeReplaced() && top.getFluidState().isEmpty());
    }

    // Solid enough that water next to it (or on it) stays put. Other water is fine too.
    private static boolean holdsWater(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() || state.getFluidState().is(FluidTags.WATER);
    }
}
