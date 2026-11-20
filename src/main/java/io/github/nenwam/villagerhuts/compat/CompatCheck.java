package io.github.nenwam.villagerhuts.compat;

import java.util.Locale;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * When the server starts, says in its log which of the mods the workers work with are switched on (and checks
 * that asking them works), so a modpack maker can see it at a glance.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID)
public final class CompatCheck {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        if (ModList.get().isLoaded("ftbteams")) {
            Teams.partyOf(UUID.randomUUID()); // asks FTB Teams once (nobody's in a party yet)
            LOGGER.info("FTB Teams: players in a party can use each other's workers (shareWithTeam setting)");
        }
        if (ModList.get().isLoaded("sereneseasons")) {
            boolean wheat = Seasons.canGrow(overworld, Blocks.WHEAT, overworld.getSharedSpawnPos().above());
            LOGGER.info("Serene Seasons: farmers plant only crops in season (wheat at spawn: {})", wheat ? "in season" : "out of season");
        }
        WorkerEntity probe = ModList.get().isLoaded("ad_astra") ? ModEntities.WORKER.get().create(overworld) : null;
        if (probe != null) { // a worker that's never added to the world, to ask Ad Astra about
            ServerLevel moon = event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("ad_astra", "moon")));
            LOGGER.info("Ad Astra: workers need air and a bearable temperature, like other mobs (at spawn: {}; on the Moon: {})",
                    describe(Space.dangerFor(probe, overworld, overworld.getSharedSpawnPos().above())),
                    moon == null ? "no Moon" : describe(Space.dangerFor(probe, moon, new BlockPos(0, 100, 0))));
        }
    }

    private static String describe(Space.@Nullable Danger danger) {
        return danger == null ? "fine" : danger.name().toLowerCase(Locale.ROOT);
    }

    private CompatCheck() {}
}
