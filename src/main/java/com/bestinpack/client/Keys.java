package com.bestinpack.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Key handling that changed between game versions. */
public final class Keys {
    private Keys() {}

    /** True when the key mapping is bound to this key (GLFW key code / scan code). */
    public static boolean matches(KeyMapping key, int keyCode, int scanCode) {
        //#if MC >= 1.21.9
        //$$ return key.matches(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, 0));
        //#else
        return key.matches(keyCode, scanCode);
        //#endif
    }

    public static boolean shiftDown() {
        //#if MC >= 1.21.9
        //$$ return Minecraft.getInstance().hasShiftDown();
        //#else
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
        //#endif
    }

    /** The InputConstants key for a GLFW key code / scan code. */
    public static com.mojang.blaze3d.platform.InputConstants.Key key(int keyCode, int scanCode) {
        //#if MC >= 1.21.9
        //$$ return com.mojang.blaze3d.platform.InputConstants.getKey(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, 0));
        //#else
        return com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode);
        //#endif
    }

    /** Display name of the key mapping's category ("Movement", "Best in Pack"). */
    public static net.minecraft.network.chat.Component categoryName(KeyMapping key) {
        //#if MC >= 1.21.9
        //$$ return key.getCategory().label();
        //#else
        return net.minecraft.network.chat.Component.translatable(key.getCategory());
        //#endif
    }

    /** Self-test: moves the real cursor (window pixels). No-op where the game no longer uses GLFW (26.3+). */
    public static void setCursor(double x, double y) {
        //#if MC >= 26.3
        //#elif MC >= 1.21.9
        //$$ org.lwjgl.glfw.GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().handle(), x, y);
        //#else
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), x, y);
        //#endif
    }

    /** The GLFW code of a key by its vanilla name ("key.keyboard.b") – codes are not the same in every version. */
    public static int code(String keyName) {
        return com.mojang.blaze3d.platform.InputConstants.getKey(keyName).getValue();
    }
}
