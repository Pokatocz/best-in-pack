package com.bestinpack.compat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * EMI click-through. Loaded by name only when EMI is installed. Recipes, the hovered stack and the search focus go
 * through EMI's public API ({@code dev.emi.emi.api}). Three things the API does not offer are read by name, each with
 * a fallback: the list EMI's item panel shows after hiding (else nothing counts as hidden), whether EMI has finished
 * loading (else: once the index has items) and EMI's own key bindings (else R and U).
 */
public final class EmiBridge implements Viewer {
    private static List<EmiStack> shownSeen;
    private static int shownSize = -1;
    private static Set<Item> visible = Set.of();
    private static java.lang.reflect.Field filteredField;
    private static java.lang.reflect.Method loadedMethod;
    private static boolean looked;

    private static void look() {
        if (looked) return;
        looked = true;
        try {
            filteredField = Class.forName("dev.emi.emi.registry.EmiStackList").getField("filteredStacks");
        } catch (Throwable ignored) {
            // an EMI without that list: nothing counts as hidden
        }
        try {
            loadedMethod = Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("isLoaded");
        } catch (Throwable ignored) {
            // go by the index alone
        }
    }

    /** What EMI's item panel lists: its index without the stacks the pack or the player hid. Null when unknown. */
    @SuppressWarnings("unchecked")
    private static List<EmiStack> shown() {
        look();
        try {
            return filteredField == null ? null : (List<EmiStack>) filteredField.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String name() {
        return "EMI";
    }

    /** EMI fills its index on its own thread after joining: until then nothing can be said about hidden items. */
    @Override
    public boolean ready() {
        look();
        try {
            if (loadedMethod != null && !(Boolean) loadedMethod.invoke(null)) return false;
        } catch (Throwable ignored) {
            // go by the index alone
        }
        if (EmiApi.getIndexStacks().isEmpty()) return false;
        // The player hid or showed something in EMI (or EMI reloaded) since the rankings were built: build them again.
        List<EmiStack> shown = shown();
        if (shownSeen != null && shown != null && (shown != shownSeen || shown.size() != shownSize)) {
            com.bestinpack.index.ItemIndex.markDirty();
        }
        return true;
    }

    /** True when EMI's item panel does not list the item: hidden by the pack or the player. */
    @Override
    public boolean isHidden(ItemStack stack) {
        List<EmiStack> shown = shown();
        if (shown == null || shown.isEmpty()) return false;
        if (shown != shownSeen || shown.size() != shownSize) {
            Set<Item> set = new HashSet<>();
            for (EmiStack s : List.copyOf(shown)) {
                ItemStack item = s.getItemStack();
                if (item != null && !item.isEmpty()) set.add(item.getItem());
            }
            visible = set;
            shownSeen = shown;
            shownSize = shown.size();
        }
        return !visible.contains(stack.getItem());
    }

    /** The stack under the mouse in EMI's panels (index, favorites) or in its recipe screen. */
    @Override
    public ItemStack hovered() {
        EmiStackInteraction hit = EmiApi.getHoveredStack(true);
        if (hit == null || hit.isEmpty()) return null;
        for (EmiStack s : hit.getStack().getEmiStacks()) {
            ItemStack item = s.getItemStack();
            if (item != null && !item.isEmpty()) return item;
        }
        return null;
    }

    @Override
    public boolean typing() {
        return EmiApi.isSearchFocused();
    }

    /** EMI keeps its key bindings in its own config ("view recipes", "view uses"), not in Minecraft's controls. */
    private static Object bind(boolean uses) throws ReflectiveOperationException {
        return Class.forName("dev.emi.emi.config.EmiConfig").getField(uses ? "viewUses" : "viewRecipes").get(null);
    }

    private static boolean matches(boolean uses, int keyCode, int scanCode) {
        try {
            Object bind = bind(uses);
            return (Boolean) bind.getClass().getMethod("matchesKey", int.class, int.class).invoke(bind, keyCode, scanCode);
        } catch (Throwable t) {
            return keyCode == (uses ? InputConstants.KEY_U : InputConstants.KEY_R);
        }
    }

    @Override
    public boolean matchesRecipeKey(InputConstants.Key key, int keyCode, int scanCode) {
        return matches(false, keyCode, scanCode);
    }

    @Override
    public boolean matchesUsesKey(InputConstants.Key key, int keyCode, int scanCode) {
        return matches(true, keyCode, scanCode);
    }

    private static Component keyName(boolean uses) {
        try {
            Object bind = bind(uses);
            return (Component) bind.getClass().getMethod("getBindText").invoke(bind);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Component recipeKeyName() {
        return keyName(false);
    }

    @Override
    public Component usesKeyName() {
        return keyName(true);
    }

    @Override
    public boolean show(ItemStack stack, boolean usages) {
        EmiStack target = EmiStack.of(stack);
        if (usages) EmiApi.displayUses(target);
        else EmiApi.displayRecipes(target);
        return true;
    }

    @Override
    public boolean ownsScreen(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("dev.emi.emi.screen.");
    }
}
