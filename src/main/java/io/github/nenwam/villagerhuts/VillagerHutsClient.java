package io.github.nenwam.villagerhuts;

import fuzs.forgeconfigapiport.fabric.api.v5.client.ConfigScreenFactoryRegistry;
import io.github.nenwam.villagerhuts.client.AssignPreview;
import io.github.nenwam.villagerhuts.client.ClientWorkers;
import io.github.nenwam.villagerhuts.client.InventoryButton;
import io.github.nenwam.villagerhuts.client.WorkerAreaHighlights;
import io.github.nenwam.villagerhuts.client.WorkerModel;
import io.github.nenwam.villagerhuts.client.WorkerPanel;
import io.github.nenwam.villagerhuts.client.WorkerRenderer;
import io.github.nenwam.villagerhuts.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public class VillagerHutsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // In-game settings screen (Mod Menu > Sanjay Gupta's Best Workers! > Config).
        // Labels for each setting come from assets/villagerhuts/lang/en_us.json.
        ConfigScreenFactoryRegistry.INSTANCE.register(VillagerHuts.MODID, ConfigurationScreen::new);

        EntityRendererRegistry.register(ModEntities.WORKER, WorkerRenderer::new);
        ModelLayerRegistry.registerModelLayer(WorkerModel.LAYER, WorkerModel::createBodyLayer);

        ClientWorkers.register();
        AssignPreview.register();
        InventoryButton.register();
        WorkerPanel.register();
        WorkerAreaHighlights.register();
    }
}
