package io.github.nenwam.villagerhuts.registry;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.item.HutItem;
import io.github.nenwam.villagerhuts.item.WorkAreaMarkerItem;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VillagerHuts.MODID);

    // Hut + fenced farm + worker. Builds data/villagerhuts/structure/farmer_hut.nbt.
    public static final DeferredItem<HutItem> FARMER_HUT = ITEMS.registerItem(
            "farmer_hut",
            props -> new HutItem(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "farmer_hut"), props),
            props -> props.stacksTo(16));

    // Just the hut + worker; assign it a farm with a Work Area Marker. Builds worker_hut.nbt.
    public static final DeferredItem<HutItem> WORKER_HUT = ITEMS.registerItem(
            "worker_hut",
            props -> new HutItem(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "worker_hut"), props),
            props -> props.stacksTo(16));

    public static final DeferredItem<WorkAreaMarkerItem> WORK_AREA_MARKER = ITEMS.registerItem(
            "work_area_marker",
            WorkAreaMarkerItem::new,
            props -> props.stacksTo(1));

    private ModItems() {}
}
