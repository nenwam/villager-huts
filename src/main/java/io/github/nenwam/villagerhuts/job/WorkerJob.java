package io.github.nenwam.villagerhuts.job;

import java.util.function.Predicate;

import io.github.nenwam.villagerhuts.registry.ModTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The jobs a worker can have. There are two kinds of worker, each with a job for when it has no tool:
 *  - farmers: unemployed (NONE) until handed a hoe, then FARMER;
 *  - fighters: unarmed (FIGHTER) until handed a weapon, then WARRIOR (melee) or RANGER (ranged); see Weapons.
 * Taking the tool back returns it to its kind's job with no tool. A worker never changes kind.
 * To add a job: add an entry here (its id, the job it has without its tool, and which items give it the job),
 * then add goals for its work in WorkerEntity.registerGoals(). (What a worker wears comes from its look,
 * WorkerLook; the tool in its hands shows the job.)
 */
public enum WorkerJob {
    NONE("none", null, null),
    FARMER("farmer", NONE, stack -> stack.is(ModTags.FARMER_TOOLS)),
    FIGHTER("fighter", null, null),
    RANGER("ranger", FIGHTER, Weapons::isRanged),
    WARRIOR("warrior", FIGHTER, Weapons::isMelee);

    private final String id;
    private final @Nullable WorkerJob withoutTool;
    private final @Nullable Predicate<ItemStack> tools;

    WorkerJob(String id, @Nullable WorkerJob withoutTool, @Nullable Predicate<ItemStack> tools) {
        this.id = id;
        this.withoutTool = withoutTool;
        this.tools = tools;
    }

    public String id() {
        return this.id;
    }

    public Component displayName() {
        return Component.translatable("job.villagerhuts." + this.id);
    }

    /** Whether this job comes with a tool (a hoe, a weapon), which the worker holds. */
    public boolean hasTool() {
        return this.withoutTool != null;
    }

    /** The job of the same kind with no tool: unemployed for farmers, unarmed for fighters. */
    public WorkerJob withoutTool() {
        return this.withoutTool != null ? this.withoutTool : this;
    }

    /** Fighters: unarmed, warriors and rangers. */
    public boolean isFighter() {
        return this.withoutTool() == FIGHTER;
    }

    /** Warriors and rangers (fighters with a weapon). */
    public boolean isArmed() {
        return this == WARRIOR || this == RANGER;
    }

    public static WorkerJob byId(String id) {
        for (WorkerJob job : values()) {
            if (job.id.equals(id)) {
                return job;
            }
        }
        return NONE;
    }

    /**
     * The job a worker with this job gets for holding this item: one of its own kind (a hoe for a farmer, a weapon
     * for a fighter), or null if the item isn't a tool for its kind.
     */
    public @Nullable WorkerJob withTool(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (WorkerJob job : values()) {
            if (job.withoutTool == this.withoutTool() && job.tools != null && job.tools.test(stack)) {
                return job;
            }
        }
        return null;
    }
}
