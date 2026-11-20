package io.github.nenwam.villagerhuts.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import io.github.nenwam.villagerhuts.client.WorkerPanel;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** EMI (when it's installed; EMI finds this itself): keeps its item lists out of the worker panel beside the inventory. */
@EmiEntrypoint
public class EmiCompat implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(InventoryScreen.class, (screen, exclude) -> WorkerPanel.panelArea(screen)
                .ifPresent(area -> exclude.accept(new Bounds(area.left(), area.top(), area.width(), area.height()))));
    }
}
