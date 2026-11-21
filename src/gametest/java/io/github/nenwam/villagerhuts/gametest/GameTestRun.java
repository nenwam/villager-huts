package io.github.nenwam.villagerhuts.gametest;

import java.util.List;

import com.mojang.logging.LogUtils;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestBatchFactory;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Runs every test when the dev server starts with -Dvillagerhuts.gametest (./gradlew runGameTest), the way vanilla's
 * game test server does, then stops the server: exit code 1 if a required test failed. Fabric API has no test runner
 * for 26.3, so this is it.
 */
final class GameTestRun {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos FIRST_TEST = new BlockPos(0, -60, 0); // on the flat world's grass
    private static final int TESTS_PER_ROW = 8;

    private static @Nullable MultipleTestTracker tracker;
    private static boolean failed;

    static void register() {
        if (System.getProperty("villagerhuts.gametest") == null) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(GameTestRun::start);
        ServerTickEvents.END_SERVER_TICK.register(GameTestRun::checkDone);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (failed) {
                Runtime.getRuntime().halt(1); // so the Gradle task fails
            }
        });
    }

    private static void start(MinecraftServer server) {
        GameRules rules = server.overworld().getGameRules();
        rules.set(GameRules.ADVANCE_TIME, false, server); // the tests set the time they need
        rules.set(GameRules.ADVANCE_WEATHER, false, server);
        rules.set(GameRules.SPAWN_MOBS, false, server);
        rules.set(GameRules.SPAWN_MONSTERS, false, server);

        List<Holder.Reference<GameTestInstance>> tests = server.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE).listElements()
                .filter(test -> test.key().identifier().getNamespace().equals(VillagerHuts.MODID))
                .toList();
        LOGGER.info("Running {} game tests", tests.size());
        GameTestRunner runner = GameTestRunner.Builder.fromBatches(GameTestBatchFactory.divideIntoBatches(tests, GameTestBatchFactory.DIRECT, server), server)
                .newStructureSpawner(new StructureGridSpawner(dimension -> FIRST_TEST, TESTS_PER_ROW, false))
                .build();
        tracker = new MultipleTestTracker(runner.getTestInfos());
        runner.start();
    }

    private static void checkDone(MinecraftServer server) {
        MultipleTestTracker running = tracker;
        if (running == null || !running.isDone()) {
            return;
        }
        tracker = null;
        for (GameTestInfo test : running.getFailedRequired()) {
            LOGGER.error("{} failed: {}", test.id(), test.getError() == null ? "?" : test.getError().getMessage());
        }
        failed = running.hasFailedRequired();
        if (failed) {
            LOGGER.error("{} required tests failed :(", running.getFailedRequiredCount());
        } else {
            LOGGER.info("All {} required tests passed :)", running.getTotalCount());
        }
        server.halt(false);
    }

    private GameTestRun() {}
}
