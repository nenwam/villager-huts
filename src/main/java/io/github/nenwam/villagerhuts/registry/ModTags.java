package io.github.nenwam.villagerhuts.registry;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Tags let players and modpack makers change worker behavior with a datapack, no code needed.
 * Defaults live in src/main/resources/data/villagerhuts/tags/.
 */
public final class ModTags {
    /** Items you hand a worker to make them a farmer (default: any hoe). */
    public static final TagKey<Item> FARMER_TOOLS = item("farmer_tools");
    /**
     * Items you hand a fighter to make them a warrior (default: everything tagged a melee weapon: swords, axes, spears,
     * maces...). Modded weapons in c:tools/melee_weapon count too. See job/Weapons for the rest of the rules.
     */
    public static final TagKey<Item> WARRIOR_WEAPONS = item("warrior_weapons");
    /** Items you hand a fighter to make them a ranger (default: bows, crossbows, tridents, and c:tools/ranged_weapon). */
    public static final TagKey<Item> RANGER_WEAPONS = item("ranger_weapons");
    /** Seeds a farmer will plant (default: the same ones vanilla farmer villagers plant). */
    public static final TagKey<Item> FARMER_SEEDS = item("farmer_seeds");
    /** Ground a farmer tills into farmland for a new farm (default: grass, dirt and dirt paths, as a hoe tills). */
    public static final TagKey<Block> TILLABLE = block("tillable");
    /**
     * Blocks workers put their harvest into (default: chests and barrels, Storage Drawers' drawers and Tom's Simple Storage's
     * inventory connector and interface). They need NeoForge's item storage too (see entity/WorkerStorage).
     */
    public static final TagKey<Block> WORKER_STORAGE = block("worker_storage");
    /** Beds a worker can sleep in (default: beds, and Comforts' sleeping bags and hammocks). They must be a bed block. */
    public static final TagKey<Block> WORKER_BEDS = block("worker_beds");
    /** Mobs fighters treat as friends, like villagers: never fought or hurt (default: Guard Villagers' guards). */
    public static final TagKey<EntityType<?>> FRIENDS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "friends"));

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, name));
    }

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, name));
    }

    private ModTags() {}
}
