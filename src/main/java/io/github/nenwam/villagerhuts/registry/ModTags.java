package io.github.nenwam.villagerhuts.registry;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Tags let players and modpack makers change worker behavior with a datapack, no code needed.
 * Defaults live in src/main/resources/data/villagerhuts/tags/.
 */
public final class ModTags {
    /** Items you hand a worker to make them a farmer (default: any hoe). */
    public static final TagKey<Item> FARMER_TOOLS = item("farmer_tools");
    /** Seeds a farmer will plant (default: the same ones vanilla farmer villagers plant). */
    public static final TagKey<Item> FARMER_SEEDS = item("farmer_seeds");
    /** Blocks workers put their harvest into (default: chests and barrels). */
    public static final TagKey<Block> WORKER_STORAGE = block("worker_storage");

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VillagerHuts.MODID, name));
    }

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(VillagerHuts.MODID, name));
    }

    private ModTags() {}
}
