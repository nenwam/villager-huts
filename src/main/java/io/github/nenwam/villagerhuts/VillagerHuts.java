package io.github.nenwam.villagerhuts;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import io.github.nenwam.villagerhuts.compat.CompatCheck;
import io.github.nenwam.villagerhuts.entity.WorkerAssigning;
import io.github.nenwam.villagerhuts.entity.WorkerCombat;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.entity.WorkerHunting;
import io.github.nenwam.villagerhuts.network.ModNetwork;
import io.github.nenwam.villagerhuts.registry.ModCreativeTabs;
import io.github.nenwam.villagerhuts.registry.ModEntities;
import io.github.nenwam.villagerhuts.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.neoforged.fml.config.ModConfig;

// The mod's id must match "id" in fabric.mod.json.
public class VillagerHuts implements ModInitializer {
    public static final String MODID = "villagerhuts";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Runs first when the mod loads (on both sides).
    @Override
    public void onInitialize() {
        ModItems.register();
        ModEntities.register();
        ModCreativeTabs.register();
        // Every living entity needs base stats (health, speed, ...) registered here.
        FabricDefaultAttributeRegistry.register(ModEntities.WORKER, WorkerEntity.createAttributes());

        // Forge Config API Port creates and loads config/villagerhuts-common.toml from this spec.
        ConfigRegistry.INSTANCE.register(MODID, ModConfig.Type.COMMON, Config.SPEC);

        ModNetwork.register();
        WorkerAssigning.register();
        WorkerCombat.register();
        WorkerHunting.register();
        CompatCheck.register();
    }
}
