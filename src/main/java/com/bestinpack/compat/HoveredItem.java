package com.bestinpack.compat;

import com.bestinpack.BestInPack;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** What the mouse is over on the current screen: a slot, or an ingredient in JEI/REI/EMI. */
public final class HoveredItem {
    private HoveredItem() {}

    /** The item under the mouse, or an empty stack. */
    public static ItemStack get(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> handled) {
            Slot slot = com.bestinpack.platform.Platform.get().hoveredSlot(handled);
            if (slot != null && slot.hasItem()) return slot.getItem();
        }
        try {
            Viewer v = RecipeViewers.get();
            if (v != null) {
                ItemStack s = v.hovered();
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Throwable t) {
            BestInPack.LOGGER.debug("Could not read the hovered ingredient", t);
        }
        return ItemStack.EMPTY;
    }

    private static java.lang.reflect.Field hoveredSlotField;

    /** The protected hoveredSlot field (Mojang names at runtime) – for loaders without a getter. */
    public static Slot hoveredSlotByField(AbstractContainerScreen<?> screen) {
        try {
            if (hoveredSlotField == null) {
                hoveredSlotField = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
                hoveredSlotField.setAccessible(true);
            }
            return (Slot) hoveredSlotField.get(screen);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** True while the player is typing somewhere (chat, search boxes), so the open key must stay a letter. */
    public static boolean typing(Screen screen) {
        // Walk down nested widget containers to the leaf that really has the focus.
        net.minecraft.client.gui.components.events.GuiEventListener focused = screen.getFocused();
        while (focused instanceof net.minecraft.client.gui.components.events.ContainerEventHandler c && c.getFocused() != null) {
            focused = c.getFocused();
        }
        if (focused instanceof EditBox field && field.isFocused()) return true;
        // Text fields that take the keys without being the screen's focused widget: the search box of the creative
        // inventory (issue #3) and the one in the recipe book.
        try {
            if (typingIn(screen.children(), 0)) return true;
        } catch (Throwable t) {
            BestInPack.LOGGER.debug("Could not look for a focused text field", t);
        }
        try {
            Viewer v = RecipeViewers.get();
            if (v != null && v.typing()) return true;
        } catch (Throwable t) {
            BestInPack.LOGGER.debug("Could not read the recipe viewer's focus", t);
        }
        return false;
    }

    private static boolean typingIn(java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> widgets, int depth) {
        for (net.minecraft.client.gui.components.events.GuiEventListener w : widgets) {
            if (w instanceof EditBox box) {
                if (box.canConsumeInput()) return true;   // visible, focused and editable
            } else if (w instanceof net.minecraft.client.gui.screens.recipebook.RecipeBookComponent) {
                if (typingInRecipeBook(w)) return true;
            } else if (depth < 3 && w instanceof net.minecraft.client.gui.components.events.ContainerEventHandler c) {
                if (typingIn(c.children(), depth + 1)) return true;
            }
        }
        return false;
    }

    private static java.lang.reflect.Field recipeBookSearch;
    private static boolean recipeBookSearched;

    /**
     * The recipe book keeps its search box to itself (a private field, found by its type so the name it has on each
     * loader does not matter). The box keeps its focus while the book is closed, so only an open book counts.
     */
    @SuppressWarnings("rawtypes")
    private static boolean typingInRecipeBook(Object book) {
        try {
            if (!((net.minecraft.client.gui.screens.recipebook.RecipeBookComponent) book).isVisible()) return false;
            if (!recipeBookSearched) {
                recipeBookSearched = true;
                for (java.lang.reflect.Field f : net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.class.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == EditBox.class) {
                        f.setAccessible(true);
                        recipeBookSearch = f;
                        break;
                    }
                }
                if (recipeBookSearch == null) BestInPack.LOGGER.debug("The recipe book's search box was not found");
            }
            return recipeBookSearch != null && recipeBookSearch.get(book) instanceof EditBox box && box.canConsumeInput();
        } catch (Throwable t) {
            recipeBookSearch = null;
            BestInPack.LOGGER.debug("Could not read the recipe book's search box", t);
            return false;
        }
    }
}
