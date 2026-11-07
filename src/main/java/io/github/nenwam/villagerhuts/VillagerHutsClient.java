package io.github.nenwam.villagerhuts;

import io.github.nenwam.villagerhuts.client.WorkerRenderer;
import io.github.nenwam.villagerhuts.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Client-only entry point: this class never loads on a dedicated server,
// so client code (rendering, screens) is safe here.
@Mod(value = VillagerHuts.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = VillagerHuts.MODID, value = Dist.CLIENT)
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
}
