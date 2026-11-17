//#if MC < 1.19.3
package com.bestinpack.client.gui.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** The vanilla text field of Minecraft 1.18 – 1.19.2 with the hint (text shown while it is empty) of 1.19.3. */
public class EditBox extends net.minecraft.client.gui.components.EditBox {
    private final Font font;
    private Component hint;

    public EditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.font = font;
    }

    public void setHint(Component hint) {
        this.hint = hint;
    }

    @Override
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
        super.renderButton(pose, mouseX, mouseY, delta);
        if (hint != null && isVisible() && getValue().isEmpty() && !isFocused()) {
            font.drawShadow(pose, hint, this.x + 4, this.y + (this.height - 8) / 2, 0xE0E0E0);
        }
    }
}
//#endif
