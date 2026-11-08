package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.network.RenameWorkerPayload;
import io.github.nenwam.villagerhuts.network.RequestWorkersPayload;
import io.github.nenwam.villagerhuts.network.WorkerCommandPayload;
import io.github.nenwam.villagerhuts.network.WorkerInfo;
import io.github.nenwam.villagerhuts.network.WorkerListPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * The client's copy of your worker list (from the server), which areas are highlighted,
 * worker colors, and the "open worker management" key.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID, value = Dist.CLIENT)
public final class ClientWorkers {
    public static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "main"));
    public static final KeyMapping OPEN_SCREEN_KEY = new KeyMapping("key.villagerhuts.workers", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, KEY_CATEGORY);

    // One color per worker number (repeats after 12).
    private static final int[] COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835, 0xFF8E24AA, 0xFFFB8C00,
            0xFF00ACC1, 0xFFD81B60, 0xFF7CB342, 0xFF8D6E63, 0xFF3949AB, 0xFF26A69A,
    };
    private static final int PANEL_REFRESH_TICKS = 20;
    private static final int HIGHLIGHT_REFRESH_TICKS = 100;

    private static List<WorkerInfo> workers = List.of();
    private static final Set<UUID> highlighted = new HashSet<>();
    private static boolean showAll;
    private static @Nullable Runnable listener;
    private static int ticks;

    public static List<WorkerInfo> workers() {
        return workers;
    }

    public static int color(int number) {
        return COLORS[Math.floorMod(number - 1, COLORS.length)];
    }

    public static void request() {
        ClientPacketDistributor.sendToServer(RequestWorkersPayload.INSTANCE);
    }

    /** Asks the server to carry out a worker panel button for one of your workers. It replies with a new list. */
    public static void command(UUID worker, WorkerCommandPayload.Command command) {
        ClientPacketDistributor.sendToServer(new WorkerCommandPayload(worker, command));
    }

    /** Asks the server to rename one of your workers (an empty name removes its name). It replies with a new list. */
    public static void rename(UUID worker, String name) {
        ClientPacketDistributor.sendToServer(new RenameWorkerPayload(worker, name));
    }

    /** Called with each new list (the worker panel uses this to refresh). */
    public static void setListener(@Nullable Runnable onUpdate) {
        listener = onUpdate;
    }

    // ---------------------------------------------------------------- highlights

    public static boolean isShown(UUID worker) {
        return showAll || highlighted.contains(worker);
    }

    public static boolean isHighlighted(UUID worker) {
        return highlighted.contains(worker);
    }

    public static boolean anyShown() {
        return showAll || !highlighted.isEmpty();
    }

    public static void toggleHighlight(UUID worker) {
        if (showAll) { // switching one off while "show all" is on: keep the others showing
            showAll = false;
            workers.forEach(w -> highlighted.add(w.id()));
        }
        if (!highlighted.remove(worker)) {
            highlighted.add(worker);
        }
    }

    public static boolean isShowingAll() {
        return showAll;
    }

    public static void toggleShowAll() {
        showAll = !showAll;
        if (!showAll) {
            highlighted.clear(); // "Hide all" hides everything
        }
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    static void registerPayloadHandler(RegisterClientPayloadHandlersEvent event) {
        event.register(WorkerListPayload.TYPE, (payload, context) -> {
            List<WorkerInfo> sorted = new ArrayList<>(payload.workers());
            sorted.sort(Comparator.comparingInt(WorkerInfo::number));
            workers = List.copyOf(sorted);
            // Workers that died (no longer listed) lose their highlight.
            highlighted.removeIf(id -> sorted.stream().noneMatch(w -> w.id().equals(id)));
            if (listener != null) {
                listener.run();
            }
        });
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
        event.register(OPEN_SCREEN_KEY);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // The key opens the inventory with the worker panel showing (works in creative too).
        while (OPEN_SCREEN_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null) {
                WorkerPanel.setOpen(true);
                minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
            }
        }
        if (minecraft.player == null) {
            return;
        }
        // Live updates while the panel is showing; slower refreshes to keep highlights current otherwise.
        ticks++;
        boolean panelShowing = WorkerPanel.isOpen() && minecraft.gui.screen() instanceof InventoryScreen;
        if (panelShowing ? ticks % PANEL_REFRESH_TICKS == 0 : anyShown() && ticks % HIGHLIGHT_REFRESH_TICKS == 0) {
            request();
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        workers = List.of();
        highlighted.clear();
        showAll = false;
    }

    private ClientWorkers() {}
}
