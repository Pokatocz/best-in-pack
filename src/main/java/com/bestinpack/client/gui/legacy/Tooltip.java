//#if MC < 1.19.3
package com.bestinpack.client.gui.legacy;

import com.bestinpack.client.gui.BestInPackScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Widget tooltips for Minecraft 1.18 – 1.19.2, which has no Tooltip class: the text a {@link Button} or
 * {@link ImageButton} shows while the mouse is over it, wrapped at 170 pixels like the game does from 1.19.3 on.
 */
public final class Tooltip {
    private static final int MAX_WIDTH = 170;
    /** A tooltip of the rankings screen, kept until the table is drawn (see {@link #flush}). */
    private static List<FormattedCharSequence> pending;

    final Component text;

    private Tooltip(Component text) {
        this.text = text;
    }

    public static Tooltip create(Component text) {
        return new Tooltip(text);
    }

    /** Called by the widget while it is hovered. */
    static void show(Tooltip tooltip, PoseStack pose, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        Screen screen = client.screen;
        if (tooltip == null || screen == null) return;
        List<FormattedCharSequence> lines = client.font.split(tooltip.text, MAX_WIDTH);
        // Widgets are drawn before the table here, which would cover the tooltip: the screen draws it at the end.
        if (screen instanceof BestInPackScreen) pending = lines;
        else screen.renderTooltip(pose, lines, mouseX, mouseY);
    }

    /** Draws the tooltip a widget asked for during this frame, on top of everything else. */
    public static void flush(PoseStack pose, Screen screen, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = pending;
        pending = null;
        if (lines != null) screen.renderTooltip(pose, lines, mouseX, mouseY);
    }
}
//#endif
