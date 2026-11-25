package com.bestinpack.index;

import com.bestinpack.BestInPack;
import net.minecraft.world.item.CreativeModeTab;
//#if MC >= 1.19.3
import net.minecraft.world.item.CreativeModeTabs;
//#endif
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
    //#if MC < 1.19.3
    //$$ private static Set<Item> legacyItems;
    //#endif
    //#if MC >= 1.21
    /** Self-test only: behave as if the game's record of the last tab build could not be reached. */
    public static boolean testNoCacheAccess;
    /** Self-test only: how often a tab build was handed back by resetting the game's record / by filling its search. */
    public static int tabResets, searchFills;
    private static java.lang.reflect.Field tabCache;
    private static boolean tabCacheSearched;
    //#endif

    /** The creative-inventory items of this world, or null when they cannot be read (then nothing is filtered). */
    public static Set<Item> collect(Level world) {
        if (world == null) return null;
        try {
            //#if MC >= 1.19.3
            // Without operator items: the op tab only holds command blocks and other technical blocks. The creative
            // screen rebuilds the tabs with its own settings when it opens.
            Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
            //#if MC >= 1.21
            // From 1.21 the creative screen fills the game's creative search only when it builds the tabs itself, so
            // a build from here must not count as the game's own: see forgetTabBuild. (An exception in the middle of
            // the build has changed the tabs as well.)
            boolean unchanged = false;
            try {
                unchanged = !CreativeModeTabs.tryRebuildTabContents(world.enabledFeatures(), false, world.registryAccess());
                readTabs(items);
            } finally {
                if (!unchanged) forgetTabBuild(world);
            }
            //#else
            //#if MC >= 1.19.4
            //$$ CreativeModeTabs.tryRebuildTabContents(world.enabledFeatures(), false, world.registryAccess());
            //#else
            //$$ CreativeModeTabs.tryRebuildTabContents(world.enabledFeatures(), false);
            //#endif
            //$$ readTabs(items);
            //#endif
            //#else
            //$$ // Up to 1.19.2 the tabs are filled by asking every item, and they are the same in every world: read
            //$$ // them once. The search tab lists what the items put into any tab; the other tabs add what mods place
            //$$ // there themselves. A tab that throws is skipped. (No operator items: they are in no tab here.)
            //$$ if (legacyItems != null) return legacyItems;
            //$$ Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
            //$$ for (CreativeModeTab tab : CreativeModeTab.TABS) {
            //$$     if (tab == null || tab == CreativeModeTab.TAB_HOTBAR || tab == CreativeModeTab.TAB_INVENTORY) continue;
            //$$     net.minecraft.core.NonNullList<ItemStack> list = net.minecraft.core.NonNullList.create();
            //$$     try {
            //$$         tab.fillItemList(list);
            //$$     } catch (Throwable t) {
            //$$         BestInPack.LOGGER.debug("Could not read the creative tab {}", tab.getRecipeFolderName(), t);
            //$$         continue;
            //$$     }
            //$$     for (ItemStack s : list) items.add(s.getItem());
            //$$ }
            //$$ if (items.size() >= MIN_PLAUSIBLE) legacyItems = items;
            //#endif
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

    //#if MC >= 1.19.3
    private static void readTabs(Set<Item> items) {
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            for (ItemStack s : tab.getDisplayItems()) items.add(s.getItem());
            for (ItemStack s : tab.getSearchTabDisplayItems()) items.add(s.getItem());
        }
    }
    //#endif

    //#if MC >= 1.21
    /**
     * Makes the game forget that the tabs were just built. The game remembers the settings of the last build and the
     * creative screen fills its search (and whatever a mod loader adds to it, such as NeoForge's searches for mod
     * tabs) only when it finds that it has to build the tabs again. After a build from here it would find them up to
     * date, and its search box would find nothing (issue #3). With the record gone, the screen builds the tabs and
     * its search itself the next time it opens or ticks - exactly as it does in a game without this mod.
     */
    private static void forgetTabBuild(Level world) {
        try {
            if (!tabCacheSearched) {
                tabCacheSearched = true;
                // CreativeModeTabs keeps the last settings in its only static field of this type (private; found by
                // its type, so the name it has on each loader does not matter).
                for (java.lang.reflect.Field f : CreativeModeTabs.class.getDeclaredFields()) {
                    int m = f.getModifiers();
                    if (java.lang.reflect.Modifier.isStatic(m) && !java.lang.reflect.Modifier.isFinal(m)
                            && f.getType() == CreativeModeTab.ItemDisplayParameters.class) {
                        f.setAccessible(true);
                        tabCache = f;
                        break;
                    }
                }
                if (tabCache == null) BestInPack.LOGGER.warn("The game's record of the creative tabs was not found – filling its creative search directly");
            }
            if (tabCache != null && !testNoCacheAccess) {
                tabCache.set(null, null);
                tabResets++;
                return;
            }
        } catch (Throwable t) {
            tabCache = null;
            BestInPack.LOGGER.warn("Could not reset the game's record of the creative tabs – filling its creative search directly", t);
        }
        fillCreativeSearch(world);
    }

    /** The second-best way: fill the game's creative search from the tabs as they are now, like the creative screen does. */
    private static void fillCreativeSearch(Level world) {
        try {
            net.minecraft.client.multiplayer.ClientPacketListener connection = net.minecraft.client.Minecraft.getInstance().getConnection();
            if (connection == null) return;
            java.util.List<ItemStack> all = java.util.List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
            connection.searchTrees().updateCreativeTooltips(world.registryAccess(), all);
            connection.searchTrees().updateCreativeTags(all);
            searchFills++;
        } catch (Throwable t) {
            BestInPack.LOGGER.warn("Could not fill the game's creative search", t);
        }
    }
    //#endif
}
