package io.github.nenwam.villagerhuts.client;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.entity.WorkerLook;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws workers with WorkerModel (a villager with free arms, like an illager), in their own look (see WorkerLook):
 * one texture holds the whole worker, clothes and headwear included (headwear sits on the model's hat layer).
 * Farmers hold their tool in their folded arms (HeldToolLayer); warriors and rangers hold their weapon in their
 * hand (vanilla's ItemInHandLayer, only while their arms are unfolded), posed for what they're doing.
 */
public class WorkerRenderer extends MobRenderer<WorkerEntity, WorkerModel> {
    private static final Map<WorkerLook, ResourceLocation> TEXTURES = new EnumMap<>(WorkerLook.class);

    static {
        for (WorkerLook look : WorkerLook.values()) {
            TEXTURES.put(look, ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "textures/entity/worker/" + look.id() + ".png"));
        }
    }

    public WorkerRenderer(EntityRendererProvider.Context context) {
        super(context, new WorkerModel(context.bakeLayer(WorkerModel.LAYER)), 0.5F);
        this.addLayer(new HeldToolLayer(this, context.getItemRenderer())); // a farmer's tool, gripped in the folded hands
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()) {   // a fighter's weapon, in its hand
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, WorkerEntity worker, float limbSwing,
                               float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
                if (WorkerModel.armsOf(worker) != WorkerModel.Arms.FOLDED) {
                    super.render(poseStack, buffer, packedLight, worker, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
                }
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(WorkerEntity worker) {
        return TEXTURES.get(worker.getLook());
    }
}
