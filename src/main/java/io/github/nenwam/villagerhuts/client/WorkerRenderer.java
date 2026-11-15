package io.github.nenwam.villagerhuts.client;

import java.util.EnumMap;
import java.util.Map;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.entity.WorkerLook;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws workers with Minecraft's villager model, in their own look (see WorkerLook): one texture holds the
 * whole worker, clothes and headwear included (headwear sits on the model's hat layer).
 */
public class WorkerRenderer extends MobRenderer<WorkerEntity, WorkerRenderer.State, VillagerModel> {
    private static final Map<WorkerLook, Identifier> TEXTURES = new EnumMap<>(WorkerLook.class);

    static {
        for (WorkerLook look : WorkerLook.values()) {
            TEXTURES.put(look, Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "textures/entity/worker/" + look.id() + ".png"));
        }
    }

    /** Vanilla's villager render state, plus which look to draw. */
    public static class State extends VillagerRenderState {
        WorkerLook look = WorkerLook.PAGRI_KURTA;
    }

    public WorkerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        // The job tool, gripped in the folded hands.
        this.addLayer(new HeldToolLayer<>(this));
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return TEXTURES.get(state.look);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WorkerEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // The item's flat art, as in the inventory (no display transform for vanilla's flat items, and tridents and
        // spears pick their flat art for it rather than their 3D in-hand model); HeldToolLayer positions it in the hands.
        this.itemModelResolver.updateForLiving(state.heldItem, entity.getMainHandItem(), ItemDisplayContext.GUI, entity);
        state.look = entity.getLook();
    }
}
