//#if MC < 1.19.3
package com.bestinpack.client.gui.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The vanilla image button of Minecraft 1.18 – 1.19.2 with a tooltip object, like buttons have from 1.19.3 on. */
public class ImageButton extends net.minecraft.client.gui.components.ImageButton {
    private Tooltip tooltip;

    public ImageButton(int x, int y, int width, int height, int xTexStart, int yTexStart, int yDiffTex, ResourceLocation texture,
                       int textureWidth, int textureHeight, OnPress onPress, Component message) {
        super(x, y, width, height, xTexStart, yTexStart, yDiffTex, texture, textureWidth, textureHeight, onPress, message);
    }

    public void setTooltip(Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    @Override
    public void renderToolTip(PoseStack pose, int mouseX, int mouseY) {
        if (this.isHovered) Tooltip.show(tooltip, pose, mouseX, mouseY);
    }
}
//#endif
