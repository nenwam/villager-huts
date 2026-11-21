package io.github.nenwam.villagerhuts.registry;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    private static final ResourceKey<EntityType<?>> WORKER_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "worker"));

    // Same size as a vanilla villager. MISC keeps workers out of natural mob spawning limits.
    public static final EntityType<WorkerEntity> WORKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, WORKER_KEY,
            EntityType.Builder.of(WorkerEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10)
                    .build(WORKER_KEY));

    /** Registers the entity type (the field above, the first time this class is used). */
    public static void register() {
    }

    private ModEntities() {}
}
