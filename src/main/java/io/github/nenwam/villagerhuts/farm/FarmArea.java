package io.github.nenwam.villagerhuts.farm;

import java.util.ArrayList;
import java.util.List;

import io.github.nenwam.villagerhuts.entity.WorkerAssigning;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * The part of a farm a worker gets when you assign it (see WorkerAssigning): a size-by-size square
 * centered where you aim (a few blocks up and down for terraced farms), trimmed to the farmland inside.
 * Shared by the server (assigning it) and the client (previewing it), so the preview matches.
 *
 * @param window   the full square that was searched
 * @param farmland every farmland block found in it
 * @param box      the farmland's bounds, one block taller so it includes the crops; null if there's no farmland
 */
public record FarmArea(BoundingBox window, List<BlockPos> farmland, @Nullable BoundingBox box) {
    public static final int SEARCH_HEIGHT = 3;

    public static FarmArea find(BlockGetter level, BlockPos aimedAt, int size) {
        BoundingBox window = window(level, aimedAt, size);

        List<BlockPos> farmland = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(window.minX(), window.minY(), window.minZ(), window.maxX(), window.maxY(), window.maxZ())) {
            if (isFarmland(level.getBlockState(pos))) {
                farmland.add(pos.immutable());
            }
        }
        if (farmland.isEmpty()) {
            return new FarmArea(window, farmland, null);
        }
        BoundingBox box = BoundingBox.encapsulatingPositions(farmland).orElseThrow();
        return new FarmArea(window, farmland, new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + 1, box.maxZ()));
    }

    /**
     * The size-by-size square searched around where you aim, SEARCH_HEIGHT blocks up and down from the ground.
     * Aiming at farmland, at a crop on farmland, or at the ground next to a farm all work.
     */
    public static BoundingBox window(BlockGetter level, BlockPos aimedAt, int size) {
        int groundY = isFarmland(level.getBlockState(aimedAt)) ? aimedAt.getY()
                : isFarmland(level.getBlockState(aimedAt.below())) ? aimedAt.getY() - 1
                : aimedAt.getY();
        int minX = aimedAt.getX() - size / 2;
        int minZ = aimedAt.getZ() - size / 2;
        return new BoundingBox(minX, groundY - SEARCH_HEIGHT, minZ, minX + size - 1, groundY + SEARCH_HEIGHT, minZ + size - 1);
    }

    /** Within maxDistance blocks of a spot (the worker), measured flat to the farm's center. */
    public boolean isNear(BlockPos from, int maxDistance) {
        return this.box != null && WorkerAssigning.isWithin(from, this.box.getCenter(), maxDistance);
    }

    public static boolean isFarmland(BlockState state) {
        return state.getBlock() instanceof FarmlandBlock || state.is(Tags.Blocks.VILLAGER_FARMLANDS);
    }
}
