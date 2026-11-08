package io.github.nenwam.villagerhuts.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Draws GUI pieces that look like the vanilla inventory. Panels and slots are cut straight from
 * the inventory texture, so they also follow any resource pack that restyles the inventory.
 */
final class NativeStyle {
    static final int LABEL = 0xFF404040;       // vanilla container label color ("Crafting")
    static final int LABEL_SOFT = 0xFF555555;  // secondary text: vanilla's dark gray, still readable on the panel
    static final int LINE = 10;                 // line spacing for panel text (the font is 9 tall)
    static final int SLOT_FILL = 0xFF8B8B8B;
    static final int SLOT_HOVER = 0x80FFFFFF;

    private static final Identifier TEXTURE = AbstractContainerScreen.INVENTORY_LOCATION;
    private static final int TEXTURE_SIZE = 256;
    private static final int PANEL_WIDTH = 176;  // the inventory panel's size in the texture
    private static final int PANEL_HEIGHT = 166;
    private static final int BORDER = 4;         // its rounded, beveled border
    private static final int SLOT_U = 7;         // a plain slot in the texture (first main-inventory slot)
    private static final int SLOT_V = 83;

    /** A panel of any size with the inventory's border: corners copied, edges and body stretched. */
    static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        int b = BORDER;
        int innerW = width - 2 * b;
        int innerH = height - 2 * b;
        int right = PANEL_WIDTH - b;
        int bottom = PANEL_HEIGHT - b;
        blit(g, x, y, 0, 0, b, b, b, b);                               // corners
        blit(g, x + width - b, y, right, 0, b, b, b, b);
        blit(g, x, y + height - b, 0, bottom, b, b, b, b);
        blit(g, x + width - b, y + height - b, right, bottom, b, b, b, b);
        blit(g, x + b, y, b, 0, innerW, b, 1, b);                       // edges
        blit(g, x + b, y + height - b, b, bottom, innerW, b, 1, b);
        blit(g, x, y + b, 0, b, b, innerH, b, 1);
        blit(g, x + width - b, y + b, right, b, b, innerH, b, 1);
        blit(g, x + b, y + b, b + 1, b + 1, innerW, innerH, 1, 1);      // body
    }

    /** An 18x18 inventory slot. */
    static void slot(GuiGraphicsExtractor g, int x, int y) {
        blit(g, x, y, SLOT_U, SLOT_V, 18, 18, 18, 18);
    }

    /** An 18x18 slot with its inside filled with a color. */
    static void slot(GuiGraphicsExtractor g, int x, int y, int fill) {
        slot(g, x, y);
        if (fill != SLOT_FILL) {
            g.fill(x + 1, y + 1, x + 17, y + 17, fill);
        }
    }

    /** A sunken area of any size, like a slot (dark top-left edge, light bottom-right edge). */
    static void inset(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, SLOT_FILL);
        g.fill(x, y, x + width - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + height - 1, 0xFF373737);
        g.fill(x + 1, y + height - 1, x + width, y + height, 0xFFFFFFFF);
        g.fill(x + width - 1, y + 1, x + width, y + height, 0xFFFFFFFF);
    }

    /** A raised button face in the inventory's colors (light, with a white highlight and gray shadow). */
    static void raised(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF000000);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + width - 2, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + height - 2, 0xFFFFFFFF);
        g.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, 0xFF555555);
        g.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, 0xFF555555);
    }

    /** The same button pushed in: darker, with the shadow on the top-left edge instead. */
    static void pressed(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF000000);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF8B8B8B);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFF373737);
        g.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFF373737);
    }

    /**
     * Panel text: full size, without a shadow (like container labels). Always full size: the pixel font
     * only stays sharp at whole-number scales, so smaller text turns blurry.
     */
    static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    private static void blit(GuiGraphicsExtractor g, int x, int y, int u, int v, int width, int height, int srcWidth, int srcHeight) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, u, v, width, height, srcWidth, srcHeight, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private NativeStyle() {}
}
