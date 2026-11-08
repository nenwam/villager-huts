package io.github.nenwam.villagerhuts.registry;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.item.FarmerItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VillagerHuts.MODID);

    // Use on the ground to hire a farmer (a worker with an iron hoe).
    public static final DeferredItem<FarmerItem> FARMER = ITEMS.registerItem(
            "farmer",
            FarmerItem::new,
            props -> props.stacksTo(16));

    private ModItems() {}
}
