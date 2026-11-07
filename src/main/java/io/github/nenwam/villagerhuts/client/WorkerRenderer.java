package io.github.nenwam.villagerhuts.client;

import java.util.EnumMap;
import java.util.Map;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.BabyVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerType;

/**
 * Draws workers with Minecraft's own villager model and textures.
 * The outfit comes from the worker's job (see WorkerJob.look()), using vanilla's profession outfits.
 */
public class WorkerRenderer extends MobRenderer<WorkerEntity, VillagerRenderState, VillagerModel> {
    private static final Identifier BASE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

    private final Map<WorkerJob, VillagerData> looks = new EnumMap<>(WorkerJob.class);

    public WorkerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        for (WorkerJob job : WorkerJob.values()) {
            this.looks.put(job, new VillagerData(
                    BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS),
                    BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(job.look()),
                    1));
        }
        // Biome clothing + profession outfit, same as vanilla villagers.
        this.addLayer(new VillagerProfessionLayer<>(
                this,
                context.getResourceManager(),
                "villager",
                new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT)),
                new BabyVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY_NO_HAT))));
        // The job tool, gripped in the folded hands.
        this.addLayer(new HeldToolLayer(this));
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        return BASE_TEXTURE;
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    @Override
    public void extractRenderState(WorkerEntity entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // NONE = the item's plain art; HeldToolLayer positions it in the hands itself.
        this.itemModelResolver.updateForLiving(state.heldItem, entity.getMainHandItem(), ItemDisplayContext.NONE, entity);
        state.villagerData = this.looks.get(entity.getJob());
    }
}
