package io.github.nenwam.villagerhuts.registry;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.item.FarmerItem;
import io.github.nenwam.villagerhuts.item.FighterItem;
import io.github.nenwam.villagerhuts.item.UpgradeItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VillagerHuts.MODID);

    // Use on the ground to hire a farmer (a worker with an iron hoe).
    public static final DeferredItem<FarmerItem> FARMER = ITEMS.registerItem(
            "farmer",
            FarmerItem::new,
            new Item.Properties().stacksTo(16));

    // Use on the ground to hire a fighter (unarmed, until you hand them a weapon).
    public static final DeferredItem<FighterItem> FIGHTER = ITEMS.registerItem(
            "fighter",
            FighterItem::new,
            new Item.Properties().stacksTo(16));

    // A carrot in eight diamonds: makes a worker Apprentice, then Journeyman.
    public static final DeferredItem<UpgradeItem> DIAMOND_CARROT = ITEMS.registerItem(
            "diamond_carrot",
            UpgradeItem::new,
            new Item.Properties().rarity(Rarity.RARE));

    // A carrot in eight diamond blocks: makes a worker Expert, then Master.
    public static final DeferredItem<UpgradeItem> FLAWLESS_DIAMOND_CARROT = ITEMS.registerItem(
            "flawless_diamond_carrot",
            UpgradeItem::new,
            new Item.Properties().rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    private ModItems() {}
}
