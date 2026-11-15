package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.nenwam.villagerhuts.network.ShareWorkerPayload;
import io.github.nenwam.villagerhuts.network.WorkerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The sharing page of the worker panel (the player-head button on a worker's details page): who owns the worker,
 * who it's shared with, and, for its owner, the other players online it could be shared with.
 *  - Its owner can share it with a player (+), stop sharing it (×), or give it to a player it's shared with
 *    (the golden helmet; click it twice). They keep using it after giving it away, as a shared player.
 *  - A player it's shared with can stop using it (× on their own row).
 * The server checks all of this again (see ModNetwork.shareWorker).
 */
class WorkerSharing extends PanelList<WorkerSharing.Row> {
    private static final int PLAYER_ROW = 24;
    private static final int TEXT_ROW = 13;
    private static final int ADD_COLOR = 0xFF55FF55;
    private static final int REMOVE_COLOR = 0xFFFF5555;
    private static final int CONFIRM_COLOR = 0xFFE0B040;

    private @Nullable String shown;           // what the rows were built from, to rebuild only when it changes
    private @Nullable UUID confirmingGive;    // the player whose "give" button was clicked once

    WorkerSharing(Minecraft minecraft) {
        super(minecraft, PLAYER_ROW);
    }

    /** Rebuilds the rows if the worker or the players online changed. */
    void refresh(@Nullable WorkerInfo info) {
        List<PlayerInfo> online = onlinePlayers();
        String now = info == null ? "" : info.id() + "|" + info.owner() + "|" + info.shared() + "|" + info.yours() + "|"
                + online.stream().map(p -> p.getProfile().id().toString()).toList() + "|" + this.confirmingGive;
        if (now.equals(this.shown)) {
            return;
        }
        this.shown = now;
        double scroll = this.scrollAmount();
        this.clearEntries();
        if (info != null) {
            this.build(info, online);
        }
        this.setScrollAmount(scroll);
    }

    private void build(WorkerInfo info, List<PlayerInfo> online) {
        UUID me = this.minecraft.player != null ? this.minecraft.player.getUUID() : null;

        this.addEntry(new TextRow(Component.translatable("gui.villagerhuts.sharing.owner"), NativeStyle.LABEL), TEXT_ROW);
        this.addEntry(new PlayerRow(info.owner().id(), info.owner().name(),
                info.yours() ? Component.translatable("gui.villagerhuts.sharing.you") : null, List.of()), PLAYER_ROW);

        this.addEntry(new TextRow(Component.translatable("gui.villagerhuts.sharing.shared_with"), NativeStyle.LABEL), TEXT_ROW);
        if (info.shared().isEmpty()) {
            this.addEntry(new TextRow(Component.translatable("gui.villagerhuts.sharing.nobody"), NativeStyle.LABEL_SOFT), TEXT_ROW);
        }
        for (WorkerInfo.PlayerRef player : info.shared()) {
            List<Button> buttons = new ArrayList<>();
            if (info.yours()) {
                buttons.add(this.giveButton(info, player));
                buttons.add(new SymbolButton("×", REMOVE_COLOR, Component.translatable("gui.villagerhuts.sharing.unshare", player.name()),
                        button -> ClientWorkers.share(info.id(), player.id(), ShareWorkerPayload.Action.UNSHARE)));
            } else if (player.id().equals(me)) {
                buttons.add(new SymbolButton("×", REMOVE_COLOR, Component.translatable("gui.villagerhuts.sharing.leave"),
                        button -> ClientWorkers.share(info.id(), player.id(), ShareWorkerPayload.Action.UNSHARE)));
            }
            this.addEntry(new PlayerRow(player.id(), player.name(),
                    player.id().equals(me) ? Component.translatable("gui.villagerhuts.sharing.you") : null, buttons), PLAYER_ROW);
        }

        if (!info.yours()) {
            return;
        }
        this.addEntry(new TextRow(Component.translatable("gui.villagerhuts.sharing.online"), NativeStyle.LABEL), TEXT_ROW);
        int candidates = 0;
        for (PlayerInfo player : online) {
            UUID id = player.getProfile().id();
            if (id.equals(info.owner().id()) || info.shared().stream().anyMatch(shared -> shared.id().equals(id))) {
                continue;
            }
            String name = player.getProfile().name();
            candidates++;
            this.addEntry(new PlayerRow(id, name, null, List.of(new SymbolButton("+", ADD_COLOR,
                    Component.translatable("gui.villagerhuts.sharing.share", name),
                    button -> ClientWorkers.share(info.id(), id, ShareWorkerPayload.Action.SHARE)))), PLAYER_ROW);
        }
        if (candidates == 0) {
            this.addEntry(new TextRow(Component.translatable("gui.villagerhuts.sharing.nobody_online"), NativeStyle.LABEL_SOFT), TEXT_ROW);
        }
    }

    // Giving a worker away takes two clicks: the first turns the button gold and asks again.
    private Button giveButton(WorkerInfo info, WorkerInfo.PlayerRef player) {
        boolean confirming = player.id().equals(this.confirmingGive);
        Component hint = Component.translatable(confirming ? "gui.villagerhuts.sharing.give_confirm" : "gui.villagerhuts.sharing.give",
                WorkerDetails.name(info), player.name());
        SlotButton button = new SlotButton(new ItemStack(Items.GOLDEN_HELMET), hint, pressed -> {
            if (player.id().equals(this.confirmingGive)) {
                this.confirmingGive = null;
                ClientWorkers.share(info.id(), player.id(), ShareWorkerPayload.Action.GIVE);
            } else {
                this.confirmingGive = player.id();
            }
        }) {
            @Override
            protected int fillColor() {
                return confirming ? CONFIRM_COLOR : NativeStyle.SLOT_FILL;
            }
        };
        button.setTooltip(Tooltip.create(hint));
        return button;
    }

    /** Everyone online, by name (your own game's list of players, as the server sends it). */
    private List<PlayerInfo> onlinePlayers() {
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection == null) {
            return List.of();
        }
        List<PlayerInfo> players = new ArrayList<>(connection.getOnlinePlayers());
        players.sort(Comparator.comparing(player -> player.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        return players;
    }

    private PlayerSkin skin(UUID player) {
        ClientPacketListener connection = this.minecraft.getConnection();
        PlayerInfo info = connection != null ? connection.getPlayerInfo(player) : null;
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(player);
    }

    // ---------------------------------------------------------------- rows

    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    /** A heading, or a note like "nobody yet". */
    private final class TextRow extends Row {
        private final Component text;
        private final int color;

        TextRow(Component text, int color) {
            this.text = text;
            this.color = color;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = WorkerSharing.this.minecraft.font;
            FormattedCharSequence line = WorkerList.fit(font, this.text, this.getContentRight() - this.getContentX());
            NativeStyle.text(graphics, font, line, this.getContentX(), this.getContentY() + 2, this.color);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** A player: their face, their name (and "you"), and the buttons for them, on the right. */
    private final class PlayerRow extends Row {
        private final UUID player;
        private final String name;
        private final @Nullable Component note;
        private final List<Button> buttons;

        PlayerRow(UUID player, String name, @Nullable Component note, List<Button> buttons) {
            this.player = player;
            this.name = name;
            this.note = note;
            this.buttons = buttons;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = WorkerSharing.this.minecraft.font;
            int x = this.getContentX();
            int y = this.getContentY();
            int right = this.getContentRight();
            for (int i = 0; i < this.buttons.size(); i++) {
                Button button = this.buttons.get(this.buttons.size() - 1 - i);
                button.setPosition(right - 18 - i * 20, y);
                button.extractRenderState(graphics, mouseX, mouseY, a);
            }
            int textRight = right - this.buttons.size() * 20 - 2;
            NativeStyle.slot(graphics, x, y);
            PlayerFaceExtractor.extractRenderState(graphics, WorkerSharing.this.skin(this.player), x + 1, y + 1, 16);
            Component label = this.note == null ? Component.literal(this.name)
                    : Component.literal(this.name + " ").append(this.note.copy().withColor(NativeStyle.LABEL_SOFT & 0xFFFFFF));
            NativeStyle.text(graphics, font, WorkerList.fit(font, label, textRight - x - 21), x + 21, y + 5, NativeStyle.LABEL);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.buttons;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.buttons.stream().map(button -> (AbstractWidget) button).toList();
        }
    }
}
