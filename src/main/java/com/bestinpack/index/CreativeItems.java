package com.bestinpack.index;

import com.bestinpack.BestInPack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The items a player can find in the creative inventory – the same list JEI starts from. Items outside it (debug and
 * technical items with no recipe, such as Iron's Spells' Gold Crown) cannot be obtained, so they are not ranked.
 */
public final class CreativeItems {
    private CreativeItems() {}

    /** Fewer items than this means the tabs did not build properly – then nothing is filtered. */
    private static final int MIN_PLAUSIBLE = 100;

    /** The creative-inventory items of this world, or null when they cannot be read (then nothing is filtered). */
    public static Set<Item> collect(Level world) {
        if (world == null) return null;
        try {
            // Without operator items: the op tab only holds command blocks and other technical blocks. The creative
            // screen rebuilds the tabs with its own settings when it opens, so this leaves nothing behind.
            CreativeModeTabs.tryRebuildTabContents(world.enabledFeatures(), false, world.registryAccess());
            Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
                for (ItemStack s : tab.getDisplayItems()) items.add(s.getItem());
                for (ItemStack s : tab.getSearchTabDisplayItems()) items.add(s.getItem());
            }
            if (items.size() < MIN_PLAUSIBLE) {
                BestInPack.LOGGER.warn("Only {} items found in the creative tabs – ranking every item instead", items.size());
                return null;
            }
            return items;
        } catch (Throwable t) {
            // A broken creative tab of some mod must not hide everything: rank all items as before.
            BestInPack.LOGGER.warn("Could not read the creative tabs – ranking every item instead", t);
            return null;
        }
    }
}
