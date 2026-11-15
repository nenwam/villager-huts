package io.github.nenwam.villagerhuts.registry;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, VillagerHuts.MODID);

    // Same size as a vanilla villager. MISC keeps workers out of natural mob spawning limits.
    public static final DeferredHolder<EntityType<?>, EntityType<WorkerEntity>> WORKER = ENTITY_TYPES.register(
            "worker",
            () -> EntityType.Builder.of(WorkerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10).build("worker"));

    private ModEntities() {}
}
