package io.github.nenwam.villagerhuts;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.gametest.VillagerHutsTests;
import io.github.nenwam.villagerhuts.registry.ModCreativeTabs;
import io.github.nenwam.villagerhuts.registry.ModDataComponents;
import io.github.nenwam.villagerhuts.registry.ModEntities;
import io.github.nenwam.villagerhuts.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

// The value here must match modId in META-INF/neoforge.mods.toml (filled in from gradle.properties).
@Mod(VillagerHuts.MODID)
public class VillagerHuts {
    public static final String MODID = "villagerhuts";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Runs first when the mod loads. NeoForge passes in the mod event bus and container.
    public VillagerHuts(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(VillagerHuts::registerAttributes);
        VillagerHutsTests.register(modEventBus); // automated tests: dev/test runs only

        // NeoForge creates and loads the config file from this spec.
        modContainer.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
    }

    // Every living entity needs base stats (health, speed, ...) registered here.
    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.WORKER.get(), WorkerEntity.createAttributes().build());
    }
}
