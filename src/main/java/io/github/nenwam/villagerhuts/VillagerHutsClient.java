package io.github.nenwam.villagerhuts;

import io.github.nenwam.villagerhuts.client.AssignPreview;
import io.github.nenwam.villagerhuts.client.WorkerModel;
import io.github.nenwam.villagerhuts.client.WorkerRenderer;
import io.github.nenwam.villagerhuts.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// Client-only entry point: this class never loads on a dedicated server,
// so client code (rendering, screens) is safe here. Its events are the mod bus's (registration).
@Mod(value = VillagerHuts.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = VillagerHuts.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class VillagerHutsClient {
    public VillagerHutsClient(ModContainer container) {
        // In-game settings screen: Mods > Villager Huts > Config.
        // Labels for each setting come from assets/villagerhuts/lang/en_us.json.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WORKER.get(), WorkerRenderer::new);
    }

    @SubscribeEvent
    static void registerModels(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WorkerModel.LAYER, WorkerModel::createBodyLayer);
    }

    // The hint above the hotbar while you're giving a worker its farm, chest or bed (see AssignPreview).
    @SubscribeEvent
    static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.OVERLAY_MESSAGE, AssignPreview.HINT_LAYER, AssignPreview::drawHint);
    }
}
