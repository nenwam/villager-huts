package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.network.WorkerInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * For each worker whose area is shown (from the worker panel): outlines of its farm (or guard area), its chest and
 * its bed in the worker's color, its number floating above each, and its number above the worker's head.
 * Numbers face the camera, show through terrain, and grow with distance so they stay readable.
 * Drawn after particles, so the outlines and numbers sit over everything in the world.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID, value = Dist.CLIENT)
public final class WorkerAreaHighlights {
    private static final float AREA_LABEL_HEIGHT = 2.5F;   // blocks above the area's top
    private static final float CHEST_LABEL_HEIGHT = 0.6F;  // blocks above the chest
    private static final float AREA_LABEL_SCALE = 0.05F;   // twice a name tag
    private static final float HEAD_LABEL_SCALE = 0.03F;
    private static final float GROW_DISTANCE = 12F;        // beyond this, numbers grow so they stay readable
    private static final int LABEL_BACKGROUND = 0x99000000;

    private record Box(BoundingBox area, int color) {}

    private record Label(Vec3 pos, int color, Component text, float scale) {}

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !ClientWorkers.anyShown()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<Box> boxes = new ArrayList<>();
        List<Label> labels = new ArrayList<>();
        for (WorkerInfo worker : ClientWorkers.workers()) {
            if (!ClientWorkers.isShown(worker.id()) || !worker.dimension().equals(level.dimension())) {
                continue;
            }
            int color = ClientWorkers.color(worker.number());
            Component number = Component.literal(" " + worker.number() + " ");

            if (worker.farm().isPresent()) {
                BoundingBox area = worker.farm().get();
                boxes.add(new Box(area, color));
                labels.add(new Label(new Vec3(area.minX() + area.getXSpan() / 2.0, area.maxY() + 1 + AREA_LABEL_HEIGHT,
                        area.minZ() + area.getZSpan() / 2.0), color, number, AREA_LABEL_SCALE));
            }
            if (worker.storage().isPresent()) {
                BoundingBox chest = AssignPreview.storageBox(level, worker.storage().get());
                boxes.add(new Box(chest, color));
                labels.add(new Label(new Vec3(chest.minX() + chest.getXSpan() / 2.0, chest.maxY() + 1 + CHEST_LABEL_HEIGHT,
                        chest.minZ() + chest.getZSpan() / 2.0), color, number, HEAD_LABEL_SCALE));
            }
            if (worker.bed().isPresent()) {
                BoundingBox bed = AssignPreview.bedBox(level, worker.bed().get());
                boxes.add(new Box(bed, color));
                labels.add(new Label(new Vec3(bed.minX() + bed.getXSpan() / 2.0, bed.maxY() + 1 + CHEST_LABEL_HEIGHT,
                        bed.minZ() + bed.getZSpan() / 2.0), color, number, HEAD_LABEL_SCALE));
            }

            // Above the worker's head (only while it's near enough to be loaded on your side).
            Entity entity = AssignPreview.findEntity(level, worker.id());
            if (entity instanceof WorkerEntity && entity.isAlive()) {
                double above = entity.getBbHeight() + (entity.hasCustomName() ? 0.85 : 0.55); // clear of a name tag
                labels.add(new Label(entity.getPosition(partialTick).add(0, above, 0), color, number, HEAD_LABEL_SCALE));
            }
        }
        if (boxes.isEmpty() && labels.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Box box : boxes) {
            BoundingBox area = box.area();
            AssignPreview.drawShape(poseStack, lines, Shapes.box(0, 0, 0, area.getXSpan(), area.getYSpan(), area.getZSpan()),
                    area.minX() - cam.x, area.minY() - cam.y, area.minZ() - cam.z, box.color());
        }
        buffers.endBatch(RenderType.lines());

        for (Label label : labels) {
            Vec3 at = label.pos();
            float scale = label.scale() * Math.max(1.0F, (float) at.distanceTo(cam) / GROW_DISTANCE);
            FormattedCharSequence text = label.text().getVisualOrderText();
            poseStack.pushPose();
            poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(scale, -scale, scale);
            font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, label.color(), false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, LABEL_BACKGROUND, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private WorkerAreaHighlights() {}
}
