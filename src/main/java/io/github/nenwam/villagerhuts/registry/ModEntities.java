package io.github.nenwam.villagerhuts.registry;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(VillagerHuts.MODID);

    // Same size as a vanilla villager. MISC keeps workers out of natural mob spawning limits.
    public static final DeferredHolder<EntityType<?>, EntityType<WorkerEntity>> WORKER = ENTITY_TYPES.registerEntityType(
            "worker",
            WorkerEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

    private ModEntities() {}
}
