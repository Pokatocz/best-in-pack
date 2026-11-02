package com.bestinpack.compat;

import com.bestinpack.BestInPack;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** What the mouse is over on the current screen: a slot, or an ingredient in JEI/REI. */
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
        try {
            Viewer v = RecipeViewers.get();
            if (v != null && v.typing()) return true;
        } catch (Throwable t) {
            BestInPack.LOGGER.debug("Could not read the recipe viewer's focus", t);
        }
        return false;
    }
}
