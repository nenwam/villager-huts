package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.network.WorkerInfo;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * For each worker whose area is shown (from the worker panel): outlines of its farm, its chest and its bed in
 * the worker's color, its number floating above each, and its number above the worker's head.
 * Numbers face the camera, show through terrain, and grow with distance so they stay readable.
 */
public final class WorkerAreaHighlights {
    private static final float AREA_LABEL_HEIGHT = 2.5F;   // blocks above the area's top
    private static final float CHEST_LABEL_HEIGHT = 0.6F;  // blocks above the chest
    private static final float AREA_LABEL_SCALE = 0.05F;   // twice a name tag
    private static final float HEAD_LABEL_SCALE = 0.03F;
    private static final float GROW_DISTANCE = 12F;        // beyond this, numbers grow so they stay readable
    private static final int LABEL_BACKGROUND = 0x99000000;

    private record Box(BoundingBox area, int color) {}

    private record Label(Vec3 pos, int color, Component text, float scale) {}

    private record Frame(List<Box> boxes, List<Label> labels) {
        static final Frame EMPTY = new Frame(List.of(), List.of());
    }

    // Snapshot taken while the frame is being prepared (extraction), then drawn (submitting).
    private static volatile Frame frame = Frame.EMPTY;

    /** Hooks the highlights into level rendering (from VillagerHutsClient). */
    public static void register() {
        LevelRenderEvents.END_EXTRACTION.register(WorkerAreaHighlights::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(WorkerAreaHighlights::submit);
    }

    private static void extract(LevelExtractionContext context) {
        if (!ClientWorkers.anyShown()) {
            frame = Frame.EMPTY;
            return;
        }
        ClientLevel level = context.level();
        ResourceKey<Level> dimension = level.dimension();
        float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
        List<Box> boxes = new ArrayList<>();
        List<Label> labels = new ArrayList<>();
        for (WorkerInfo worker : ClientWorkers.workers()) {
            if (!ClientWorkers.isShown(worker.id()) || !worker.dimension().equals(dimension)) {
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
            Entity entity = level.getEntity(worker.id());
            if (entity instanceof WorkerEntity && entity.isAlive()) {
                double above = entity.getBbHeight() + (entity.hasCustomName() ? 0.85 : 0.55); // clear of a name tag
                labels.add(new Label(entity.getPosition(partialTick).add(0, above, 0), color, number, HEAD_LABEL_SCALE));
            }
        }
        frame = new Frame(List.copyOf(boxes), List.copyOf(labels));
    }

    private static void submit(LevelRenderContext context) {
        Frame current = frame;
        if (current.boxes().isEmpty() && current.labels().isEmpty()) {
            return;
        }
        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cam = camera.pos;
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        Font font = Minecraft.getInstance().font;
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth() * 2.0F;

        for (Box box : current.boxes()) {
            BoundingBox area = box.area();
            VoxelShape shape = Shapes.box(0, 0, 0, area.getXSpan(), area.getYSpan(), area.getZSpan());
            poseStack.pushPose();
            poseStack.translate(area.minX() - cam.x, area.minY() - cam.y, area.minZ() - cam.z);
            collector.submitShapeOutline(poseStack, shape, RenderTypes.linesTranslucent(), box.color(), lineWidth, false);
            poseStack.popPose();
        }

        for (Label label : current.labels()) {
            Vec3 at = label.pos();
            float scale = label.scale() * Math.max(1.0F, (float) at.distanceTo(cam) / GROW_DISTANCE);
            FormattedCharSequence text = label.text().getVisualOrderText();
            poseStack.pushPose();
            poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            poseStack.rotate(camera.orientation);
            poseStack.scale(scale, -scale, scale);
            collector.submitText(poseStack, -font.width(text) / 2.0F, 0.0F, text, false, Font.DisplayMode.SEE_THROUGH,
                    LightCoordsUtil.FULL_BRIGHT, label.color(), LABEL_BACKGROUND, 0);
            poseStack.popPose();
        }
    }

    private WorkerAreaHighlights() {}
}
