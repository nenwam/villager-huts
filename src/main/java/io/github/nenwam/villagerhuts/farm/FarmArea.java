package io.github.nenwam.villagerhuts.farm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * The part of a farm a worker gets when you point a Work Area Marker at it: a size-by-size square
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
        // Aiming at farmland, at a crop on farmland, or at the ground next to a farm all work.
        int groundY = isFarmland(level.getBlockState(aimedAt)) ? aimedAt.getY()
                : isFarmland(level.getBlockState(aimedAt.below())) ? aimedAt.getY() - 1
                : aimedAt.getY();
        int minX = aimedAt.getX() - size / 2;
        int minZ = aimedAt.getZ() - size / 2;
        BoundingBox window = new BoundingBox(minX, groundY - SEARCH_HEIGHT, minZ,
                minX + size - 1, groundY + SEARCH_HEIGHT, minZ + size - 1);

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

    /** Within maxDistance blocks of the hut, measured flat from the hut's center to the farm's center. */
    public boolean isNear(BlockPos hutCenter, int maxDistance) {
        if (this.box == null) {
            return false;
        }
        BlockPos center = this.box.getCenter();
        double dx = center.getX() - hutCenter.getX();
        double dz = center.getZ() - hutCenter.getZ();
        return dx * dx + dz * dz <= (double) maxDistance * maxDistance;
    }

    public static boolean isFarmland(BlockState state) {
        return state.getBlock() instanceof FarmlandBlock || state.is(Tags.Blocks.VILLAGER_FARMLANDS);
    }
}
