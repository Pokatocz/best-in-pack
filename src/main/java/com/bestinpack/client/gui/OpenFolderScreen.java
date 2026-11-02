package com.bestinpack.client.gui;

import com.bestinpack.config.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What the mod list's "Config" button opens: there is no settings screen – the JSON file is the settings, like JEI –
 * so this opens config/bestinpack/ on its first tick and goes straight back to the mod list.
 */
public final class OpenFolderScreen extends Screen {
    private final Screen parent;
    private boolean done;

    public OpenFolderScreen(Screen parent) {
        super(Component.translatable("bestinpack.button.config"));
        this.parent = parent;
    }

    @Override
    public void tick() {
        if (done || this.minecraft == null) return;
        done = true;
        Config.get().save(); // make sure the folder and both files exist
        Gfx.openFolder(Config.dir());
        com.bestinpack.client.Screens.set(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (parent != null) parent.render(graphics, -1, -1, delta);
    }
}
