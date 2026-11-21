package io.github.nenwam.villagerhuts.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Space (Ad Astra, when it's installed): whether a worker can live somewhere. Workers breathe like any other mob: on a
 * planet without air they need an oxygen distributor's sealed area, and where it's too hot or too cold they get hurt.
 * This doesn't change that; it lets the worker say so (its status, and a notice), and warns you when you give one a
 * place it can't live.
 */
public final class Space {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** What would hurt a worker somewhere. */
    public enum Danger {
        NO_OXYGEN("no_oxygen"), TOO_HOT("too_hot"), TOO_COLD("too_cold");

        private final String id;

        Danger(String id) {
            this.id = id;
        }

        /** The worker's status while it's in danger. */
        public Component status() {
            return Component.translatable("activity.villagerhuts." + this.id);
        }

        /** The warning when it's given a place (or hired) where this would hurt it. */
        public Component warning() {
            return Component.translatable("message.villagerhuts.danger." + this.id);
        }
    }

    // Ad Astra's own tags for mobs its air and temperature don't hurt (a modpack could add workers to them).
    private static final TagKey<EntityType<?>> LIVES_WITHOUT_OXYGEN = adAstraTag("lives_without_oxygen");
    private static final TagKey<EntityType<?>> SURVIVES_HEAT = adAstraTag("can_survive_extreme_heat");
    private static final TagKey<EntityType<?>> SURVIVES_COLD = adAstraTag("can_survive_extreme_cold");

    private static boolean looked;
    private static @Nullable MethodHandle hasOxygen; // OxygenApi.API.hasOxygen(Level, BlockPos)
    private static @Nullable MethodHandle isHot;     // TemperatureApi.API.isHot(Level, BlockPos)
    private static @Nullable MethodHandle isCold;    // TemperatureApi.API.isCold(Level, BlockPos)
    private static final List<TestRule> TEST_RULES = new ArrayList<>(); // see addRuleForTests

    private record TestRule(Level level, BoundingBox area, Danger danger) {}

    /** What would hurt this worker at this spot (with Ad Astra), or null if nothing would. */
    public static @Nullable Danger dangerFor(Entity worker, Level level, BlockPos pos) {
        for (TestRule rule : TEST_RULES) {
            if (rule.level() == level && rule.area().isInside(pos)) {
                return rule.danger();
            }
        }
        if (!found() || level.isClientSide()) {
            return null;
        }
        Holder<EntityType<?>> type = worker.getType().builtInRegistryHolder();
        try {
            if (!type.is(LIVES_WITHOUT_OXYGEN) && !(boolean) hasOxygen.invoke(level, pos)) {
                return Danger.NO_OXYGEN;
            }
            if (!type.is(SURVIVES_HEAT) && (boolean) isHot.invoke(level, pos)) {
                return Danger.TOO_HOT;
            }
            if (!type.is(SURVIVES_COLD) && (boolean) isCold.invoke(level, pos)) {
                return Danger.TOO_COLD;
            }
        } catch (Throwable e) {
            LOGGER.warn("Ad Astra's oxygen or temperature check failed: workers won't warn about air and heat from now on", e);
            hasOxygen = null;
        }
        return null;
    }

    /**
     * For the game tests (where Ad Astra isn't installed): in this area, workers are in this danger. Run what it returns
     * to take the rule away again (a later test may use the same spot).
     */
    public static Runnable addRuleForTests(Level level, BoundingBox area, Danger danger) {
        TestRule rule = new TestRule(level, area, danger);
        TEST_RULES.add(rule);
        return () -> TEST_RULES.remove(rule);
    }

    private static boolean found() {
        if (!looked) {
            looked = true;
            if (FabricLoader.getInstance().isModLoaded("ad_astra")) {
                try {
                    hasOxygen = check("OxygenApi", "hasOxygen");
                    isHot = check("TemperatureApi", "isHot");
                    isCold = check("TemperatureApi", "isCold");
                } catch (ReflectiveOperationException | LinkageError e) {
                    LOGGER.warn("Ad Astra is installed, but its oxygen and temperature checks weren't found: workers won't warn about them", e);
                    hasOxygen = null;
                }
            }
        }
        return hasOxygen != null && isHot != null && isCold != null;
    }

    // Ad Astra's API.method(Level, BlockPos), bound to its API object.
    private static MethodHandle check(String api, String method) throws ReflectiveOperationException {
        Class<?> type = Class.forName("earth.terrarium.adastra.api.systems." + api, true, Space.class.getClassLoader());
        Object instance = type.getField("API").get(null);
        return MethodHandles.publicLookup().findVirtual(type, method, MethodType.methodType(boolean.class, Level.class, BlockPos.class))
                .bindTo(instance);
    }

    private static TagKey<EntityType<?>> adAstraTag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("ad_astra", path));
    }

    private Space() {}
}
