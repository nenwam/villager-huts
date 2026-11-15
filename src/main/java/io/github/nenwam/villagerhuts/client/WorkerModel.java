package io.github.nenwam.villagerhuts.client;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.Weapons;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * A worker's body: Minecraft's villager, plus a pair of free arms like an illager's. Farmers (and fighters with no
 * weapon) fold their arms like villagers, with their tool in their hands (HeldToolLayer). Warriors and rangers
 * unfold them and hold their weapon in their main hand (WorkerRenderer's hand layer): swinging it at a foe, drawing
 * a bow, aiming or loading a crossbow, or raising a trident to throw (Arms).
 * The free arms use the texture at (40, 46), like illagers'; the villager's hat brim, which shares that spot, is left out
 * (no worker wears one).
 */
public class WorkerModel extends HierarchicalModel<WorkerEntity> implements ArmedModel, HeadedModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "worker"), "main");

    /** How the arms are held. */
    public enum Arms {
        FOLDED,            // farmers, and fighters with no weapon
        DOWN,              // a weapon in hand, nobody to fight
        FIGHTING,          // a warrior at a foe, swinging
        DRAWING_BOW,       // a ranger with a bow (or anything else that shoots arrows)
        AIMING_CROSSBOW,
        LOADING_CROSSBOW,
        THROWING           // a ranger with a trident, about to throw it
    }

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart foldedArms;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public WorkerModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.foldedArms = root.getChild("arms");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    /** Vanilla's villager mesh (net.minecraft.client.model.VillagerModel.createBodyModel) without the hat brim, plus the free arms. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F), PartPose.ZERO);
        head.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.51F)), PartPose.ZERO);
        head.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F), PartPose.ZERO);
        body.addOrReplaceChild("jacket",
                CubeListBuilder.create().texOffs(0, 38).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.ZERO);
        root.addOrReplaceChild("arms", CubeListBuilder.create()
                        .texOffs(44, 22).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                        .texOffs(44, 22).addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, true)
                        .texOffs(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, -1.0F, -0.75F, 0.0F, 0.0F));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(-2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 46).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 46).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** How a worker holds its arms right now: folded unless it's armed, and posed for what it's doing if it is. */
    public static Arms armsOf(WorkerEntity worker) {
        WorkerJob job = worker.getJob();
        if (!job.isArmed()) {
            return Arms.FOLDED;
        }
        ItemStack weapon = worker.getMainHandItem();
        boolean fighting = worker.isAggressive();
        if (job == WorkerJob.WARRIOR) {
            return fighting ? Arms.FIGHTING : Arms.DOWN;
        }
        if (Weapons.isCrossbow(weapon)) {
            if (worker.isUsingItem()) {
                return Arms.LOADING_CROSSBOW;
            }
            return fighting || CrossbowItem.isCharged(weapon) ? Arms.AIMING_CROSSBOW : Arms.DOWN;
        }
        if (Weapons.isThrown(weapon)) {
            return fighting ? Arms.THROWING : Arms.DOWN;
        }
        return fighting ? Arms.DRAWING_BOW : Arms.DOWN;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(WorkerEntity worker, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.head.zRot = 0.0F;
        float walk = limbSwing * 0.6662F;
        this.rightLeg.xRot = Mth.cos(walk) * 1.4F * limbSwingAmount * 0.5F;
        this.leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.4F * limbSwingAmount * 0.5F;
        this.rightLeg.yRot = 0.0F;
        this.leftLeg.yRot = 0.0F;

        Arms arms = armsOf(worker);
        boolean folded = arms == Arms.FOLDED;
        this.foldedArms.visible = folded;
        this.rightArm.visible = !folded;
        this.leftArm.visible = !folded;
        if (folded) {
            return;
        }
        // Swinging as it walks, like anyone's arms.
        this.rightArm.xRot = Mth.cos(walk + Mth.PI) * limbSwingAmount;
        this.leftArm.xRot = Mth.cos(walk) * limbSwingAmount;
        this.rightArm.yRot = 0.0F;
        this.leftArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;
        this.leftArm.zRot = 0.0F;
        boolean rightHanded = worker.getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = rightHanded ? this.rightArm : this.leftArm;
        ModelPart off = rightHanded ? this.leftArm : this.rightArm;
        float side = rightHanded ? 1.0F : -1.0F;
        switch (arms) {
            case FIGHTING -> AnimationUtils.swingWeaponDown(this.rightArm, this.leftArm, worker, this.attackTime, ageInTicks);
            case DRAWING_BOW -> {
                main.yRot = -0.1F * side + this.head.yRot;
                main.xRot = -Mth.HALF_PI + this.head.xRot;
                off.yRot = 0.5F * side + this.head.yRot;
                off.xRot = -Mth.HALF_PI + this.head.xRot;
            }
            case AIMING_CROSSBOW -> AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, rightHanded);
            case LOADING_CROSSBOW -> AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, worker, rightHanded);
            case THROWING -> {
                main.xRot = main.xRot * 0.5F - Mth.PI;
                main.yRot = 0.0F;
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
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        (arm == HumanoidArm.LEFT ? this.leftArm : this.rightArm).translateAndRotate(poseStack);
    }
}
