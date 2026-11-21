package io.github.nenwam.villagerhuts.registry;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.item.FarmerItem;
import io.github.nenwam.villagerhuts.item.FighterItem;
import io.github.nenwam.villagerhuts.item.UpgradeItem;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    // Use on the ground to hire a farmer (a worker with an iron hoe).
    public static final FarmerItem FARMER = register("farmer", FarmerItem::new, props -> props.stacksTo(16));

    // Use on the ground to hire a fighter (unarmed, until you hand them a weapon).
    public static final FighterItem FIGHTER = register("fighter", FighterItem::new, props -> props.stacksTo(16));

    // A carrot in eight diamonds: makes a worker Apprentice, then Journeyman.
    public static final UpgradeItem DIAMOND_CARROT = register("diamond_carrot", UpgradeItem::new, props -> props.rarity(Rarity.RARE));

    // A carrot in eight diamond blocks: makes a worker Expert, then Master.
    public static final UpgradeItem FLAWLESS_DIAMOND_CARROT = register("flawless_diamond_carrot", UpgradeItem::new,
            props -> props.rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    /** Registers the items (the fields above, the first time this class is used). */
    public static void register() {
    }

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, UnaryOperator<Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VillagerHuts.MODID, name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.apply(new Item.Properties().setId(key))));
    }

    private ModItems() {}
}
