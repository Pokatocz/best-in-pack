//#if MC < 1.20
package com.bestinpack.client.gui.legacy;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Minecraft 1.18 – 1.19.4 has no GuiGraphics: screens draw through a PoseStack and static helpers. This class has the
 * methods of the 1.20 GuiGraphics that Best in Pack calls, so the screens are written once for every version.
 */
public final class GuiGraphics {
    private final PoseStack pose;
    private final Screen screen;

    public GuiGraphics(PoseStack pose, Screen screen) {
        this.pose = pose;
        this.screen = screen;
    }

    public PoseStack pose() {
        return pose;
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public void drawCenteredString(Font font, Component text, int centerX, int y, int color) {
        font.drawShadow(pose, text, centerX - font.width(text) / 2f, y, color);
    }

    public void drawCenteredString(Font font, String text, int centerX, int y, int color) {
        font.drawShadow(pose, text, centerX - font.width(text) / 2f, y, color);
    }

    /** The item's icon, as a slot draws it (no count, no durability bar). */
    public void renderItem(ItemStack stack, int x, int y) {
        //#if MC >= 1.19.4
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(pose, stack, x, y);
        //#else
        //$$ Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y);
        //#endif
    }

    /** Clips everything drawn until {@link #disableScissor} to the rectangle (GUI coordinates). */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        RenderSystem.enableScissor((int) (x1 * scale), (int) (window.getHeight() - y2 * scale),
                Math.max(0, (int) ((x2 - x1) * scale)), Math.max(0, (int) ((y2 - y1) * scale)));
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    public void blit(ResourceLocation texture, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GuiComponent.blit(pose, x, y, u, v, w, h, texW, texH);
    }

    public void renderOutline(int x, int y, int w, int h, int color) {
        fill(x, y, x + w, y + 1, color);
        fill(x, y + h - 1, x + w, y + h, color);
        fill(x, y + 1, x + 1, y + h - 1, color);
        fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public void renderTooltip(Font font, ItemStack stack, int x, int y) {
        screen.renderTooltip(pose, screen.getTooltipFromItem(stack), stack.getTooltipImage(), x, y);
    }

    public void renderTooltip(Font font, List<FormattedCharSequence> lines, int x, int y) {
        screen.renderTooltip(pose, lines, x, y);
    }
}
//#endif
