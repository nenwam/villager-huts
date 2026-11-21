package io.github.nenwam.villagerhuts.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Seasons (Serene Seasons, when it's installed): whether a crop grows where it's planted, this season. Asks Serene
 * Seasons itself, so its crop seasons, greenhouses, biomes, dimensions and settings all count. Without it, every
 * crop grows all year round.
 */
public final class Seasons {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean looked;
    private static @Nullable MethodHandle isCropFertile; // sereneseasons.init.ModFertility.isCropFertile(String, Level, BlockPos)
    private static final List<TestRule> TEST_RULES = new ArrayList<>(); // see addRuleForTests

    private record TestRule(Level level, BoundingBox area, Predicate<Block> grows) {}

    /** Whether this crop grows at this spot now. */
    public static boolean canGrow(Level level, Block crop, BlockPos pos) {
        for (TestRule rule : TEST_RULES) {
            if (rule.level() == level && rule.area().isInside(pos)) {
                return rule.grows().test(crop);
            }
        }
        MethodHandle check = fertilityCheck();
        if (check == null) {
            return true;
        }
        try {
            return (boolean) check.invokeExact(BuiltInRegistries.BLOCK.getKey(crop).toString(), level, pos);
        } catch (Throwable e) {
            LOGGER.warn("Serene Seasons' crop check failed: farmers plant every crop all year from now on", e);
            isCropFertile = null;
            return true;
        }
    }

    /**
     * For the game tests (where Serene Seasons isn't installed): in this area, only crops that pass `grows` grow.
     * Run what it returns to take the rule away again (a later test may use the same spot).
     */
    public static Runnable addRuleForTests(Level level, BoundingBox area, Predicate<Block> grows) {
        TestRule rule = new TestRule(level, area, grows);
        TEST_RULES.add(rule);
        return () -> TEST_RULES.remove(rule);
    }

    private static @Nullable MethodHandle fertilityCheck() {
        if (!looked) {
            looked = true;
            if (FabricLoader.getInstance().isModLoaded("sereneseasons")) {
                try {
                    Class<?> fertility = Class.forName("sereneseasons.init.ModFertility", true, Seasons.class.getClassLoader());
                    isCropFertile = MethodHandles.publicLookup().findStatic(fertility, "isCropFertile",
                            MethodType.methodType(boolean.class, String.class, Level.class, BlockPos.class));
                } catch (ReflectiveOperationException | LinkageError e) {
                    LOGGER.warn("Serene Seasons is installed, but its crop check wasn't found: farmers plant every crop all year", e);
                }
            }
        }
        return isCropFertile;
    }

    private Seasons() {}
}
