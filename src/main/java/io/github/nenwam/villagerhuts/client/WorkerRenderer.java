package io.github.nenwam.villagerhuts.client;

import java.util.EnumMap;
import java.util.Map;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.entity.WorkerLook;
import io.github.nenwam.villagerhuts.job.Weapons;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws workers with WorkerModel (a villager with free arms, like an illager), in their own look (see WorkerLook):
 * one texture holds the whole worker, clothes and headwear included (headwear sits on the model's hat layer).
 * Farmers hold their tool in their folded arms (HeldToolLayer); warriors and rangers hold their weapon in their
 * hand (vanilla's ItemInHandLayer), posed for what they're doing.
 */
public class WorkerRenderer extends MobRenderer<WorkerEntity, WorkerRenderer.State, WorkerModel> {
    private static final Map<WorkerLook, Identifier> TEXTURES = new EnumMap<>(WorkerLook.class);

    static {
        for (WorkerLook look : WorkerLook.values()) {
            TEXTURES.put(look, Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "textures/entity/worker/" + look.id() + ".png"));
        }
    }

    /** What to draw: an armed entity's state (the hand items, the swing), plus the look, the arms' pose and the folded-arms tool. */
    public static class State extends ArmedEntityRenderState {
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

        WorkerLook look = WorkerLook.PAGRI_KURTA;
        Arms arms = Arms.FOLDED;
        final ItemStackRenderState toolInFoldedArms = new ItemStackRenderState();
        int crossbowChargeTicks;
        float ticksUsingItem;
    }

    public WorkerRenderer(EntityRendererProvider.Context context) {
        super(context, new WorkerModel(context.bakeLayer(WorkerModel.LAYER)), 0.5F);
        this.addLayer(new HeldToolLayer(this));   // a farmer's tool, gripped in the folded hands
        this.addLayer(new ItemInHandLayer<>(this)); // a fighter's weapon, in its hand
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
        state.look = entity.getLook();
        state.arms = arms(entity);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, partialTicks);
        state.ticksUsingItem = entity.getTicksUsingItem(partialTicks);
        state.crossbowChargeTicks = state.arms == State.Arms.LOADING_CROSSBOW ? CrossbowItem.getChargeDuration(entity.getUseItem(), entity) : 0;
        if (state.arms == State.Arms.FOLDED) {
            // The item's flat art, as in the inventory (no display transform for vanilla's flat items, and tridents and
            // spears pick their flat art for it rather than their 3D in-hand model); HeldToolLayer positions it.
            this.itemModelResolver.updateForLiving(state.toolInFoldedArms, entity.getMainHandItem(), ItemDisplayContext.GUI, entity);
            state.rightHandItemState.clear();
            state.leftHandItemState.clear();
        } else {
            state.toolInFoldedArms.clear();
        }
    }

    private static State.Arms arms(WorkerEntity worker) {
        WorkerJob job = worker.getJob();
        if (!job.isArmed()) {
            return State.Arms.FOLDED;
        }
        ItemStack weapon = worker.getMainHandItem();
        boolean fighting = worker.isAggressive();
        if (job == WorkerJob.WARRIOR) {
            return fighting ? State.Arms.FIGHTING : State.Arms.DOWN;
        }
        if (Weapons.isCrossbow(weapon)) {
            if (worker.isUsingItem()) {
                return State.Arms.LOADING_CROSSBOW;
            }
            return fighting || CrossbowItem.isCharged(weapon) ? State.Arms.AIMING_CROSSBOW : State.Arms.DOWN;
        }
        if (Weapons.isThrown(weapon)) {
            return fighting ? State.Arms.THROWING : State.Arms.DOWN;
        }
        return fighting ? State.Arms.DRAWING_BOW : State.Arms.DOWN;
    }
}
