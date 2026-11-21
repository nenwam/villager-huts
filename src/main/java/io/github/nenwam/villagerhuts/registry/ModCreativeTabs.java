package io.github.nenwam.villagerhuts.registry;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

public final class ModCreativeTabs {
    public static final CreativeModeTab MAIN = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "main"), FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.villagerhuts"))
                    .icon(ModItems.FARMER::getDefaultInstance)
                    .displayItems((params, output) -> {
                        output.accept(ModItems.FARMER);
                        output.accept(ModItems.FIGHTER);
                        output.accept(ModItems.DIAMOND_CARROT);
                        output.accept(ModItems.FLAWLESS_DIAMOND_CARROT);
                    })
                    .build());

    /** Registers the tab (the field above, the first time this class is used). */
    public static void register() {
    }

    private ModCreativeTabs() {}
}
