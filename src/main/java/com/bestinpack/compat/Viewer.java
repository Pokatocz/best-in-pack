package com.bestinpack.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What Best in Pack needs from a recipe viewer. JEI and REI each implement it in their own class, which is only
 * loaded (by name) when that viewer is installed – and only built for game versions the viewer exists for.
 */
public interface Viewer {
    String name();

    /** True when the viewer can already tell which items it hides (its lists are built after joining). */
    boolean ready();

    boolean isHidden(ItemStack stack);

    /** The ingredient under the mouse in the viewer's overlay, or null. */
    ItemStack hovered();

    /** True while the viewer's search field has the focus. */
    boolean typing();

    boolean matchesRecipeKey(InputConstants.Key key, int keyCode, int scanCode);

    boolean matchesUsesKey(InputConstants.Key key, int keyCode, int scanCode);

    /** Display names of the recipe / uses keys, or null when the viewer does not expose them. */
    Component recipeKeyName();

    Component usesKeyName();

    /** Opens the recipes (or uses) of the stack; false when the viewer could not. */
    boolean show(ItemStack stack, boolean usages);

    /** True when this screen belongs to the viewer (its recipe window), so the open key may jump from it. */
    default boolean ownsScreen(Screen screen) {
        return false;
    }
}
