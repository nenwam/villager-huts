package io.github.nenwam.villagerhuts.compat;

import java.util.List;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.client.WorkerPanel;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

/** JEI (when it's installed; JEI finds this itself): keeps its item list out of the worker panel beside the inventory. */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "worker_panel");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(InventoryScreen.class, new IGuiContainerHandler<InventoryScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(InventoryScreen screen) {
                return WorkerPanel.panelArea(screen).map(area -> List.of(new Rect2i(area.left(), area.top(), area.width(), area.height())))
                        .orElse(List.of());
            }
        });
    }
}
