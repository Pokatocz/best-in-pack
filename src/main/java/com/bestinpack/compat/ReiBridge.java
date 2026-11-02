package com.bestinpack.compat;

import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Roughly Enough Items click-through. Loaded by name only when REI is installed. */
public final class ReiBridge implements Viewer {
    private static java.util.Set<net.minecraft.world.item.Item> visible;
    private static int visibleSize = -1;

    @Override
    public String name() {
        return "REI";
    }

    @Override
    public boolean ready() {
        try {
            return me.shedaniel.rei.api.client.registry.entry.EntryRegistry.getInstance().getPreFilteredList() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when REI's filtered list does not contain the item (hidden by the pack or the player). */
    @Override
    public boolean isHidden(ItemStack stack) {
        java.util.List<EntryStack<?>> list = me.shedaniel.rei.api.client.registry.entry.EntryRegistry.getInstance().getPreFilteredList();
        if (list == null) return false;
        if (visible == null || visibleSize != list.size()) {
            java.util.Set<net.minecraft.world.item.Item> set = new java.util.HashSet<>();
            for (EntryStack<?> e : list) if (e.getValue() instanceof ItemStack s) set.add(s.getItem());
            visible = set;
            visibleSize = list.size();
        }
        return !visible.contains(stack.getItem());
    }

    /** REI's recipe / usage keys, read reflectively so cloth-config is not needed at compile time. */
    private static boolean matchesKey(boolean uses, int keyCode, int scanCode) {
        try {
            Object cfg = me.shedaniel.rei.api.client.config.ConfigObject.getInstance();
            Object kc = cfg.getClass().getMethod(uses ? "getUsageKeybind" : "getRecipeKeybind").invoke(cfg);
            return kc != null && (Boolean) kc.getClass().getMethod("matchesKey", int.class, int.class).invoke(kc, keyCode, scanCode);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean matchesRecipeKey(InputConstants.Key key, int keyCode, int scanCode) {
        return matchesKey(false, keyCode, scanCode);
    }

    @Override
    public boolean matchesUsesKey(InputConstants.Key key, int keyCode, int scanCode) {
        return matchesKey(true, keyCode, scanCode);
    }

    @Override
    public Component recipeKeyName() {
        return null;
    }

    @Override
    public Component usesKeyName() {
        return null;
    }

    @Override
    public ItemStack hovered() {
        var overlay = me.shedaniel.rei.api.client.REIRuntime.getInstance().getOverlay();
        if (overlay.isEmpty()) return null;
        EntryStack<?> focused = overlay.get().getEntryList().getFocusedStack();
        if (focused == null || focused.isEmpty()) return null;
        return focused.getValue() instanceof ItemStack s ? s : null;
    }

    @Override
    public boolean typing() {
        var field = me.shedaniel.rei.api.client.REIRuntime.getInstance().getSearchTextField();
        //#if MC >= 1.20.2
        return field != null && field.isFocused();
        //#else
        //$$ // REI 12 (1.20.1) for Forge does not expose isFocused() on its TextField; the widget behind it is a vanilla listener.
        //$$ return field instanceof net.minecraft.client.gui.components.events.GuiEventListener l && l.isFocused();
        //#endif
    }

    @Override
    public boolean show(ItemStack stack, boolean usages) {
        EntryStack<ItemStack> entry = EntryStacks.of(stack);
        ViewSearchBuilder builder = ViewSearchBuilder.builder();
        if (usages) builder.addUsagesFor(entry);
        else builder.addRecipesFor(entry);
        return builder.open();
    }
}
