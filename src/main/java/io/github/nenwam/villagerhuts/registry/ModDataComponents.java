package io.github.nenwam.villagerhuts.registry;

import com.mojang.serialization.Codec;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.item.WorkerLink;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Extra data stored on item stacks. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, VillagerHuts.MODID);

    /** Extra quarter turns (0-3, clockwise) applied to a hut item's design when placed. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HUT_ROTATION =
            COMPONENTS.registerComponentType("hut_rotation",
                    builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Which worker a Work Area Marker is linked to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WorkerLink>> WORKER_LINK =
            COMPONENTS.registerComponentType("worker_link",
                    builder -> builder.persistent(WorkerLink.CODEC).networkSynchronized(WorkerLink.STREAM_CODEC));

    private ModDataComponents() {}
}
