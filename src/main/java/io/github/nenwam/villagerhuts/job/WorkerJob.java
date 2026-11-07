package io.github.nenwam.villagerhuts.job;

import io.github.nenwam.villagerhuts.registry.ModTags;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The jobs a worker can have. To add a job: add an entry here (its id, the vanilla outfit it wears,
 * and the tool tag that assigns it), then add a goal for its work in WorkerEntity.registerGoals().
 */
public enum WorkerJob {
    NONE("none", VillagerProfession.NONE, null),
    FARMER("farmer", VillagerProfession.FARMER, ModTags.FARMER_TOOLS);

    private final String id;
    private final ResourceKey<VillagerProfession> look;
    private final @Nullable TagKey<Item> tools;

    WorkerJob(String id, ResourceKey<VillagerProfession> look, @Nullable TagKey<Item> tools) {
        this.id = id;
        this.look = look;
        this.tools = tools;
    }

    public String id() {
        return this.id;
    }

    /** Which vanilla villager outfit this job wears. */
    public ResourceKey<VillagerProfession> look() {
        return this.look;
    }

    public Component displayName() {
        return Component.translatable("job.villagerhuts." + this.id);
    }

    public static WorkerJob byId(String id) {
        for (WorkerJob job : values()) {
            if (job.id.equals(id)) {
                return job;
            }
        }
        return NONE;
    }

    /** The job you get by handing a worker this item, or null if it isn't a job tool. */
    public static @Nullable WorkerJob forTool(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (WorkerJob job : values()) {
            if (job.tools != null && stack.is(job.tools)) {
                return job;
            }
        }
        return null;
    }
}
