package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.network.OrderAllPayload;
import io.github.nenwam.villagerhuts.network.PanelNoticePayload;
import io.github.nenwam.villagerhuts.network.RenameWorkerPayload;
import io.github.nenwam.villagerhuts.network.RequestWorkersPayload;
import io.github.nenwam.villagerhuts.network.ShareWorkerPayload;
import io.github.nenwam.villagerhuts.network.WorkerCommandPayload;
import io.github.nenwam.villagerhuts.network.WorkerInfo;
import io.github.nenwam.villagerhuts.network.WorkerListPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The client's copy of your worker list (from the server: your workers, then the ones shared with you or your party),
 * which areas are highlighted, worker colors, the panel's latest notice, and the "open worker management" key.
 */
public final class ClientWorkers {
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "main"));
    public static final KeyMapping OPEN_SCREEN_KEY = new KeyMapping("key.villagerhuts.workers", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, KEY_CATEGORY);

    // One color per worker number (repeats after 12).
    private static final int[] COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835, 0xFF8E24AA, 0xFFFB8C00,
            0xFF00ACC1, 0xFFD81B60, 0xFF7CB342, 0xFF8D6E63, 0xFF3949AB, 0xFF26A69A,
    };
    private static final int PANEL_REFRESH_TICKS = 20;
    private static final int HIGHLIGHT_REFRESH_TICKS = 100;
    private static final long NOTICE_MILLIS = 5000;

    private static List<WorkerInfo> workers = List.of();
    private static final Set<UUID> highlighted = new HashSet<>();
    private static boolean showAll;
    private static @Nullable Runnable listener;
    private static int ticks;
    private static @Nullable Component notice;
    private static long noticeUntil;

    public static List<WorkerInfo> workers() {
        return workers;
    }

    public static int color(int number) {
        return COLORS[Math.floorMod(number - 1, COLORS.length)];
    }

    public static void request() {
        ClientPlayNetworking.send(RequestWorkersPayload.INSTANCE);
    }

    /** Asks the server to carry out a worker panel button for one of your workers. It replies with a new list. */
    public static void command(UUID worker, WorkerCommandPayload.Command command) {
        ClientPlayNetworking.send(new WorkerCommandPayload(worker, command));
    }

    /** Asks the server to rename one of your workers (an empty name removes its name). It replies with a new list. */
    public static void rename(UUID worker, String name) {
        ClientPlayNetworking.send(new RenameWorkerPayload(worker, name));
    }

    /** Asks the server to share a worker with a player, stop sharing it, or give it to them. It replies with a new list. */
    public static void share(UUID worker, UUID player, ShareWorkerPayload.Action action) {
        ClientPlayNetworking.send(new ShareWorkerPayload(worker, player, action));
    }

    /** Asks the server to give all your own workers this order (WORK, FOLLOW or STAY). It replies with what happened. */
    public static void orderAll(WorkerCommandPayload.Command order) {
        ClientPlayNetworking.send(new OrderAllPayload(order));
    }

    /** Asks the server where a worker is (in chat), with a map marker if Xaero's minimap or world map is installed. */
    public static void locate(UUID worker) {
        command(worker, hasXaero() ? WorkerCommandPayload.Command.LOCATE_ON_MAP : WorkerCommandPayload.Command.LOCATE);
    }

    public static boolean hasXaero() {
        return FabricLoader.getInstance().isModLoaded("xaerominimap") || FabricLoader.getInstance().isModLoaded("xaeroworldmap");
    }

    /** The panel's latest notice (why a button did nothing, or what it did), for a few seconds after it came; else null. */
    public static @Nullable Component notice() {
        return notice != null && Util.getMillis() < noticeUntil ? notice : null;
    }

    public static void receiveNotice(PanelNoticePayload payload) {
        notice = payload.message();
        noticeUntil = Util.getMillis() + NOTICE_MILLIS;
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

    /** Hooks the client's side into the game (from VillagerHutsClient). */
    public static void register() {
        KeyMappingHelper.registerKeyMapping(OPEN_SCREEN_KEY);
        ClientPlayNetworking.registerGlobalReceiver(WorkerListPayload.TYPE, (payload, context) -> receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(PanelNoticePayload.TYPE, (payload, context) -> receiveNotice(payload));
        ClientTickEvents.END_CLIENT_TICK.register(ClientWorkers::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((connection, minecraft) -> {
            workers = List.of();
            highlighted.clear();
            showAll = false;
            notice = null;
        });
    }

    /** A new list from the server (on the client's main thread), in the server's order: yours first. */
    static void receive(WorkerListPayload payload) {
        List<WorkerInfo> sorted = new ArrayList<>(payload.workers());
        workers = List.copyOf(sorted);
        // Workers that died (no longer listed) lose their highlight.
        highlighted.removeIf(id -> sorted.stream().noneMatch(w -> w.id().equals(id)));
        if (listener != null) {
            listener.run();
        }
    }

    private static void onClientTick(Minecraft minecraft) {
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

    private ClientWorkers() {}
}
