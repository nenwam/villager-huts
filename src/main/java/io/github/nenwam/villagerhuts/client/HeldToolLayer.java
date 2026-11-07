package io.github.nenwam.villagerhuts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws the worker's tool gripped in its folded hands: handle upright at its right side,
 * head at the top pointing outward. Replaces vanilla's crossed-arms item layer, which shows
 * items half-size floating in front of the arms.
 *
 * Expects state.heldItem to be prepared with ItemDisplayContext.NONE (see WorkerRenderer),
 * which draws the item's flat art centered on the origin.
 */
public class HeldToolLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    // Where the hands grip the handle, measured from the arms' pivot with the body upright, in blocks.
    // x: negative = the worker's right; y: positive = down; z: negative = forward.
    private static final float GRIP_X = -0.16F;
    private static final float GRIP_Y = 0.18F;
    private static final float GRIP_Z = -0.19F;
    private static final float LEAN = (float) Math.toRadians(-8.0); // top of the handle tips outward
    private static final float SCALE = 1.1F;

    public HeldToolLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        ItemStackRenderState item = state.heldItem;
        if (item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().translateToArms(state, poseStack);
        poseStack.rotate(Axis.XP, 0.75F);                      // undo the folded arms' forward tilt
        poseStack.translate(GRIP_X, GRIP_Y, GRIP_Z);           // into the right side of the folded hands
        poseStack.rotate(Axis.ZP, LEAN);
        poseStack.rotate(Axis.XP, (float) Math.PI);            // item art is y-up; entity models are y-down
        poseStack.rotate(Axis.ZP, (float) Math.toRadians(45)); // tool art is diagonal: stand the handle up
        poseStack.scale(SCALE, SCALE, SCALE);
        // Vanilla tool art has the handle running through the image's center, so that's where it's held.
        item.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
