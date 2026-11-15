package io.github.nenwam.villagerhuts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a farmer's tool (or anything a worker with folded arms holds) gripped in its folded hands: handle upright
 * at its right side, head at the top pointing outward. Replaces vanilla's crossed-arms item layer, which shows
 * items half-size floating in front of the arms. (Warriors and rangers unfold their arms and hold their weapon
 * in their hand instead: vanilla's ItemInHandLayer, see WorkerRenderer.)
 *
 * Draws the item's flat art, as in the inventory (ItemDisplayContext.GUI: no display transform for vanilla's flat
 * items, and tridents pick their flat art for it rather than their 3D in-hand model), centered on the origin.
 */
public class HeldToolLayer extends RenderLayer<WorkerEntity, WorkerModel> {
    // Where the hands grip the handle, measured from the arms' pivot with the body upright, in blocks.
    // x: negative = the worker's right; y: positive = down; z: negative = forward.
    private static final float GRIP_X = -0.16F;
    private static final float GRIP_Y = 0.18F;
    private static final float GRIP_Z = -0.19F;
    private static final float LEAN = (float) Math.toRadians(-8.0); // top of the handle tips outward
    private static final float SCALE = 1.1F;

    private final ItemRenderer itemRenderer;

    public HeldToolLayer(RenderLayerParent<WorkerEntity, WorkerModel> renderer, ItemRenderer itemRenderer) {
        super(renderer);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, WorkerEntity worker, float limbSwing,
                       float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack tool = worker.getMainHandItem();
        if (tool.isEmpty() || WorkerModel.armsOf(worker) != WorkerModel.Arms.FOLDED) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().translateToFoldedArms(poseStack);
        poseStack.mulPose(Axis.XP.rotation(0.75F));               // undo the folded arms' forward tilt
        poseStack.translate(GRIP_X, GRIP_Y, GRIP_Z);              // into the right side of the folded hands
        poseStack.mulPose(Axis.ZP.rotation(LEAN));
        poseStack.mulPose(Axis.XP.rotation((float) Math.PI));     // item art is y-up; entity models are y-down
        poseStack.mulPose(Axis.ZP.rotationDegrees(45));           // tool art is diagonal: stand the handle up
        poseStack.scale(SCALE, SCALE, SCALE);
        // Vanilla tool art has the handle running through the image's center, so that's where it's held.
        this.itemRenderer.renderStatic(worker, tool, ItemDisplayContext.GUI, false, poseStack, buffer, worker.level(), packedLight,
                OverlayTexture.NO_OVERLAY, worker.getId());
        poseStack.popPose();
    }
}
