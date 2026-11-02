package com.bestinpack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The current screen – a Minecraft field up to 26.1, owned by the Gui from 26.2 on. */
public final class Screens {
    private Screens() {}

    public static Screen current() {
        //#if MC >= 26.2
        //$$ return Minecraft.getInstance().gui.screen();
        //#else
        return Minecraft.getInstance().screen;
        //#endif
    }

    public static void set(Screen screen) {
        //#if MC >= 26.2
        //$$ Minecraft.getInstance().gui.setScreen(screen);
        //#else
        Minecraft.getInstance().setScreen(screen);
        //#endif
    }
}
