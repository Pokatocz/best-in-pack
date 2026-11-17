//#if MC < 1.19.3
package com.bestinpack.client.gui.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;

/**
 * The vanilla button of Minecraft 1.18 – 1.19.2 with what buttons got in 1.19.3: a builder, a tooltip object and
 * getters for the position. The screens use it under the name Button, so their code is the same for every version.
 */
public class Button extends net.minecraft.client.gui.components.Button {
    private Tooltip tooltip;

    protected Button(int x, int y, int width, int height, Component label, OnPress onPress) {
        super(x, y, width, height, label, onPress);
    }

    public static Builder builder(Component label, OnPress onPress) {
        return new Builder(label, onPress);
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

    public void setX(int x) {
        this.x = x;
    }

    @Override
    public void renderToolTip(PoseStack pose, int mouseX, int mouseY) {
        if (this.isHovered) Tooltip.show(tooltip, pose, mouseX, mouseY);
    }

    public static final class Builder {
        private final Component label;
        private final OnPress onPress;
        private int x, y, width = 150, height = 20;

        private Builder(Component label, OnPress onPress) {
            this.label = label;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Button build() {
            return new Button(x, y, width, height, label, onPress);
        }
    }
}
//#endif
