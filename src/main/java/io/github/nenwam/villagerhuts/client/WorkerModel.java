package io.github.nenwam.villagerhuts.client;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * A worker's body: Minecraft's villager, plus a pair of free arms like an illager's. Farmers (and fighters with no
 * weapon) fold their arms like villagers, with their tool in their hands (HeldToolLayer). Warriors and rangers
 * unfold them and hold their weapon in their right hand (ItemInHandLayer): swinging it at a foe, drawing a bow,
 * aiming or loading a crossbow, or raising a trident to throw (WorkerRenderer.State.Arms).
 * The free arms use the texture at (40, 46), like illagers'; the villager's hat brim, which shares that spot, is left out
 * (no worker wears one).
 */
public class WorkerModel extends EntityModel<WorkerRenderer.State> implements ArmedModel<WorkerRenderer.State>, HeadedModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "worker"), "main");

    private final ModelPart head;
    private final ModelPart foldedArms;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public WorkerModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.foldedArms = root.getChild("arms");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = VillagerModel.createBodyModel();
        PartDefinition root = mesh.getRoot();
        root.getChild("head").getChild("hat").clearChild("hat_rim");
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 46).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 46).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WorkerRenderer.State state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        float walk = state.walkAnimationPos * 0.6662F;
        float speed = state.walkAnimationSpeed;
        this.rightLeg.xRot = Mth.cos(walk) * 1.4F * speed * 0.5F;
        this.leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.4F * speed * 0.5F;

        boolean folded = state.arms == WorkerRenderer.State.Arms.FOLDED;
        this.foldedArms.visible = folded;
        this.rightArm.visible = !folded;
        this.leftArm.visible = !folded;
        if (folded) {
            return;
        }
        // Swinging as it walks, like anyone's arms.
        this.rightArm.xRot = Mth.cos(walk + Mth.PI) * speed;
        this.leftArm.xRot = Mth.cos(walk) * speed;
        this.rightArm.yRot = 0.0F;
        this.leftArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;
        this.leftArm.zRot = 0.0F;
        switch (state.arms) {
            case FIGHTING -> AnimationUtils.swingWeaponDown(this.rightArm, this.leftArm, HumanoidArm.RIGHT, state.swingAnimation, state.ageInTicks);
            case DRAWING_BOW -> {
                this.rightArm.yRot = -0.1F + this.head.yRot;
                this.rightArm.xRot = -Mth.HALF_PI + this.head.xRot;
                this.leftArm.yRot = 0.1F + this.head.yRot + 0.4F;
                this.leftArm.xRot = -Mth.HALF_PI + this.head.xRot;
            }
            case AIMING_CROSSBOW -> AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, true);
            case LOADING_CROSSBOW -> AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, state.crossbowChargeTicks, state.ticksUsingItem, true);
            case THROWING -> {
                this.rightArm.xRot = this.rightArm.xRot * 0.5F - Mth.PI;
                this.rightArm.yRot = 0.0F;
            }
            default -> {
            }
        }
    }

    @Override
    public ModelPart getHead() {
        return this.head;
    }

    /** To the folded arms (for the tool a farmer holds in them: HeldToolLayer). */
    public void translateToFoldedArms(PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        this.foldedArms.translateAndRotate(poseStack);
    }

    @Override
    public void translateToHand(WorkerRenderer.State state, HumanoidArm arm, PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        (arm == HumanoidArm.LEFT ? this.leftArm : this.rightArm).translateAndRotate(poseStack);
    }
}
