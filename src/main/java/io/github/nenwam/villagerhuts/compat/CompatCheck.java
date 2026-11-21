package io.github.nenwam.villagerhuts.compat;

import java.util.Locale;

import com.mojang.logging.LogUtils;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * When the server starts, says in its log which of the mods the workers work with are switched on (and checks
 * that asking them works), so a modpack maker can see it at a glance.
 */
public final class CompatCheck {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Hooks the check into server start (from VillagerHuts.onInitialize). */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(CompatCheck::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (FabricLoader.getInstance().isModLoaded("sereneseasons")) {
            boolean wheat = Seasons.canGrow(overworld, Blocks.WHEAT, overworld.getRespawnData().pos().above());
            LOGGER.info("Serene Seasons: farmers plant only crops in season (wheat at spawn: {})", wheat ? "in season" : "out of season");
        }
        WorkerEntity probe = FabricLoader.getInstance().isModLoaded("ad_astra") ? ModEntities.WORKER.create(overworld, EntitySpawnReason.LOAD) : null;
        if (probe != null) { // a worker that's never added to the world, to ask Ad Astra about
            ServerLevel moon = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("ad_astra", "moon")));
            LOGGER.info("Ad Astra: workers need air and a bearable temperature, like other mobs (at spawn: {}; on the Moon: {})",
                    describe(Space.dangerFor(probe, overworld, overworld.getRespawnData().pos().above())),
                    moon == null ? "no Moon" : describe(Space.dangerFor(probe, moon, new BlockPos(0, 100, 0))));
        }
    }

    private static String describe(Space.@Nullable Danger danger) {
        return danger == null ? "fine" : danger.name().toLowerCase(Locale.ROOT);
    }

    private CompatCheck() {}
}
