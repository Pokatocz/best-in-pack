package com.bestinpack.compat;

import com.bestinpack.BestInPack;
import com.bestinpack.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Click-through to whichever recipe viewer is installed. Each viewer lives in its own class, loaded by name only
 * when that mod is present (and only shipped for game versions the viewer exists for).
 */
public final class RecipeViewers {
    private RecipeViewers() {}

    private static Viewer viewer;
    private static boolean resolved;

    /** The installed viewer (JEI preferred), or null. */
    public static Viewer get() {
        if (!resolved) {
            resolved = true;
            viewer = load("jei", "com.bestinpack.compat.JeiBridge");
            if (viewer == null) viewer = load("roughlyenoughitems", "com.bestinpack.compat.ReiBridge");
        }
        return viewer;
    }

    private static Viewer load(String modId, String className) {
        try {
            if (!Platform.get().isModLoaded(modId)) return null;
            return (Viewer) Class.forName(className).getConstructor().newInstance();
        } catch (Throwable t) {
            BestInPack.LOGGER.info("Best in Pack: no {} support in this build ({})", modId, t.toString());
            return null;
        }
    }

    /** Name of the viewer that will handle clicks, or null when none is installed. */
    public static String available() {
        Viewer v = get();
        return v == null ? null : v.name();
    }

    public static boolean hidingReady() {
        Viewer v = get();
        try {
            return v != null && v.ready();
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when the installed viewer hides this item (edit mode, blacklist, KubeJS, pack filters). */
    public static boolean isHidden(ItemStack stack) {
        Viewer v = get();
        try {
            return v != null && v.isHidden(stack);
        } catch (Throwable t) {
            BestInPack.LOGGER.debug("Could not ask the recipe viewer about {}", stack, t);
            return false;
        }
    }

    /** The viewer's "show recipe" key (R unless remapped). Without a viewer this is R. */
    public static boolean isRecipeKey(int keyCode, int scanCode) {
        Viewer v = get();
        try {
            if (v != null) return v.matchesRecipeKey(com.bestinpack.client.Keys.key(keyCode, scanCode), keyCode, scanCode);
        } catch (Throwable ignored) {}
        return keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_R;
    }

    /** The viewer's "show uses" key (U unless remapped). */
    public static boolean isUsesKey(int keyCode, int scanCode) {
        Viewer v = get();
        try {
            if (v != null) return v.matchesUsesKey(com.bestinpack.client.Keys.key(keyCode, scanCode), keyCode, scanCode);
        } catch (Throwable ignored) {}
        return keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_U;
    }

    /** Names of the recipe / uses keys for the help text, e.g. "R" and "U". */
    public static Component recipeKeyName() {
        Viewer v = get();
        try {
            if (v != null) { Component t = v.recipeKeyName(); if (t != null) return t; }
        } catch (Throwable ignored) {}
        return Component.literal("R");
    }

    public static Component usesKeyName() {
        Viewer v = get();
        try {
            if (v != null) { Component t = v.usesKeyName(); if (t != null) return t; }
        } catch (Throwable ignored) {}
        return Component.literal("U");
    }

    /** Opens the recipes (or usages) of the stack. Returns false when no viewer could do it. */
    public static boolean show(ItemStack stack, boolean usages) {
        Viewer v = get();
        try {
            return v != null && v.show(stack, usages);
        } catch (Throwable t) {
            BestInPack.LOGGER.warn("Could not open recipe viewer", t);
            return false;
        }
    }

    /** Self-test only. */
    public static void setHiddenForTest(ItemStack stack, boolean hidden) {
        try {
            if (get() != null && "JEI".equals(get().name())) {
                Class.forName("com.bestinpack.compat.JeiBridge").getMethod("setHiddenForTest", ItemStack.class, boolean.class)
                        .invoke(null, stack, hidden);
            }
        } catch (Throwable t) {
            BestInPack.LOGGER.warn("setHiddenForTest failed", t);
        }
    }
}
