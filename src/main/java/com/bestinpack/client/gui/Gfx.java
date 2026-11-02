package com.bestinpack.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.List;

/** Drawing calls whose shape changed between game versions, in one place. */
public final class Gfx {
    private Gfx() {}

    /** Draws a texture region (u/v/size in pixels of a texW × texH texture). */
    public static void blit(GuiGraphics g, Object texture, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        //#if MC >= 1.21.11
        //$$ g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, (net.minecraft.resources.Identifier) texture, x, y, u, v, w, h, texW, texH);
        //#elif MC >= 1.21.6
        //$$ g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, (net.minecraft.resources.ResourceLocation) texture, x, y, u, v, w, h, texW, texH);
        //#elif MC >= 1.21.2
        //$$ g.blit(net.minecraft.client.renderer.RenderType::guiTextured, (net.minecraft.resources.ResourceLocation) texture, x, y, u, v, w, h, texW, texH);
        //#else
        g.blit((net.minecraft.resources.ResourceLocation) texture, x, y, u, v, w, h, texW, texH);
        //#endif
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        //#if MC >= 26.1
        //$$ g.outline(x, y, w, h, color);
        //#elif MC >= 1.21.9 && MC < 1.21.11
        //$$ g.submitOutline(x, y, w, h, color);
        //#else
        g.renderOutline(x, y, w, h, color);
        //#endif
    }

    public static void itemTooltip(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        //#if MC >= 1.21.6
        //$$ g.setTooltipForNextFrame(font, stack, x, y);
        //#else
        g.renderTooltip(font, stack, x, y);
        //#endif
    }

    public static void textTooltip(GuiGraphics g, Font font, List<FormattedCharSequence> lines, int x, int y) {
        //#if MC >= 1.21.6
        //$$ g.setTooltipForNextFrame(font, lines, x, y);
        //#else
        g.renderTooltip(font, lines, x, y);
        //#endif
    }

    public static void openFolder(Path dir) {
        //#if MC >= 26.3
        //$$ com.mojang.blaze3d.Blaze3D.openPath(dir);
        //#elif MC >= 1.21.11
        //$$ net.minecraft.util.Util.getPlatform().openPath(dir);
        //#elif MC >= 1.21
        //$$ net.minecraft.Util.getPlatform().openPath(dir);
        //#else
        net.minecraft.Util.getPlatform().openFile(dir.toFile());
        //#endif
    }
}
