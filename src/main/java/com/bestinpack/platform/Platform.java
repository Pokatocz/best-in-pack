package com.bestinpack.platform;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.nio.file.Path;

/**
 * The few things only the mod loader knows. Each loader's entry point installs its implementation with {@link #set}
 * before anything else runs; the rest of the mod is shared by Fabric, Forge and NeoForge.
 */
public interface Platform {
    /** The game's config folder (Best in Pack keeps its own sub-folder in it). */
    Path configDir();

    boolean isModLoaded(String modId);

    /** Display name of a mod ("Applied Energistics 2"), or the id when the loader does not know it. */
    String modName(String modId);

    /** Furnace burn time in ticks, 0 when the item is no fuel. {@code level} may be null before joining. */
    int burnTicks(ItemStack stack, Level level);

    /** True when burn times can be read now (some versions need a world for the fuel table). */
    default boolean hasFuelData(Level level) {
        return true;
    }

    /** Loader hook: where the item is worn when the mod says so itself, otherwise null. */
    default EquipmentSlot equipmentSlot(ItemStack stack) {
        return null;
    }

    /** Loader hook: true/false when the loader knows whether the item blocks like a shield, null otherwise. */
    default Boolean canBlock(ItemStack stack) {
        return null;
    }

    /** Loader hook: true when the item lets the player glide (modded elytras). */
    default boolean canGlide(ItemStack stack, Player player) {
        return false;
    }

    /** Loader hook: enchantability when the loader lets items compute it, -1 otherwise. */
    default int enchantability(ItemStack stack) {
        return -1;
    }

    /** The slot under the mouse in an inventory screen, or null. */
    Slot hoveredSlot(AbstractContainerScreen<?> screen);

    /** "fabric", "forge" or "neoforge". */
    String loader();

    // ------------------------------------------------------------------ holder

    final class Holder {
        private static Platform instance;
    }

    static void set(Platform platform) {
        Holder.instance = platform;
    }

    static Platform get() {
        if (Holder.instance == null) throw new IllegalStateException("Best in Pack: platform not set up yet");
        return Holder.instance;
    }
}
