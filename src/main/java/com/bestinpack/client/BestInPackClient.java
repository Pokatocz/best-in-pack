package com.bestinpack.client;

import com.bestinpack.client.gui.BestInPackScreen;
import com.bestinpack.compat.HoveredItem;
import com.bestinpack.config.Config;
import com.bestinpack.index.ItemIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Everything Best in Pack does on the client, shared by all loaders. Each loader's entry point creates the key
 * mapping, registers it, and forwards its events to the {@code on…} methods here.
 */
public final class BestInPackClient {
    private BestInPackClient() {}

    /** The open key ("Open Best in Pack", B by default). Set by the loader before the first tick. */
    public static KeyMapping openKey;

    public static final String KEY_NAME = "key.bestinpack.open";
    public static final String KEY_CATEGORY = "key.category.bestinpack.general";

    /** Ticks to wait after joining before the index is built, so the join itself is not slowed down. */
    private static final int BUILD_DELAY_TICKS = 40;
    private static int buildCountdown = -1;

    /**
     * Opens Best in Pack from another screen: on the hovered item's row when there is one, otherwise as usual.
     * Does nothing while the player is typing, or on screens that are neither inventories nor recipe viewers.
     */
    /** The default open key: "openKey" from the config (B unless a modpack changed it). */
    public static com.mojang.blaze3d.platform.InputConstants.Key defaultOpenKey() {
        try {
            return com.mojang.blaze3d.platform.InputConstants.getKey(com.bestinpack.config.Config.get().openKey);
        } catch (Throwable t) {
            com.bestinpack.BestInPack.LOGGER.warn("Unknown openKey in the config – using B", t);
            return com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.b");
        }
    }

    public static boolean jumpFromScreen(Minecraft client, Screen screen) {
        if (client.level == null || HoveredItem.typing(screen)) return false;
        ItemStack hovered = HoveredItem.get(screen);
        if (hovered.isEmpty() && !(screen instanceof AbstractContainerScreen<?>)) return false;
        com.bestinpack.client.Screens.set(hovered.isEmpty() ? new BestInPackScreen(screen) : BestInPackScreen.openAt(screen, hovered.getItem()));
        return !hovered.isEmpty();
    }

    // ------------------------------------------------------------------ events (called by the loader)

    public static void onClientTick(Minecraft client) {
        Discovery.tick(client);
        if (openKey != null) {
            while (openKey.consumeClick()) {
                if (com.bestinpack.client.Screens.current() == null) {
                    com.bestinpack.client.Screens.set(new BestInPackScreen(null));
                }
            }
        }
        // Build the index shortly after joining, so the first Shift-tooltip or screen opening is instant.
        if (buildCountdown > 0 && client.level != null && client.player != null) {
            if (--buildCountdown == 0) {
                if (!ItemIndex.isBuilt()) ItemIndex.ensureBuilt(client.level, client.player);
                JoinMessages.afterJoin(client);
            }
        }
    }

    /** The index depends on the world (enabled features, mod hooks), so it is rebuilt after each join. */
    public static void onJoin() {
        Config.reload(); // settings edited while playing (e.g. the chat messages) apply from the next join
        ItemIndex.markDirty();
        buildCountdown = BUILD_DELAY_TICKS;
        Discovery.onJoin(Minecraft.getInstance());
    }

    public static void onLeave() {
        ItemIndex.clear();
        buildCountdown = -1;
        Discovery.onDisconnect();
    }

    /**
     * A key pressed while some screen is open. The open key also works inside inventories and recipe viewers:
     * over an item it jumps straight to that item's row, otherwise it just opens the screen. Returns true when the
     * key was used (the loader then stops the screen from also handling it).
     */
    public static boolean onScreenKey(Screen screen, int keyCode, int scanCode) {
        if (screen instanceof BestInPackScreen || openKey == null) return false;
        if (!Keys.matches(openKey, keyCode, scanCode) || !Config.get().openKeyInMenus) return false;
        Minecraft client = Minecraft.getInstance();
        Screen before = com.bestinpack.client.Screens.current();
        jumpFromScreen(client, screen);
        return com.bestinpack.client.Screens.current() != before;
    }

    /** Small button above the survival inventory, so the screen is reachable without remembering the key. */
    public static void onScreenInit(Screen screen, Consumer<AbstractWidget> addWidget) {
        if (!(screen instanceof InventoryScreen inv) || !Config.get().inventoryButton) return;
        int left = (screen.width - 176) / 2;
        int top = (screen.height - 166) / 2;
        int dx = Config.get().inventoryButtonX, dy = Config.get().inventoryButtonY;
        Button button = Button.builder(Component.translatable("bestinpack.button.inventory"),
                        btn -> com.bestinpack.client.Screens.set(new BestInPackScreen(screen)))
                .bounds(left + 176 - 28 + dx, top - 22 + dy, 28, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable("bestinpack.button.inventory.tooltip")));
        addWidget.accept(button);
        BUTTONS.put(inv, button);
    }

    /**
     * The inventory slides 77 px to the right while the recipe book is open. Vanilla keeps its own recipe-book
     * toggle at (x + 104), so the button follows that one instead of guessing.
     */
    public static void onScreenRender(Screen screen) {
        if (!(screen instanceof InventoryScreen inv)) return;
        Button button = BUTTONS.get(inv);
        if (button == null) return;
        int dx = Config.get().inventoryButtonX;
        for (GuiEventListener w : screen.children()) {
            if (w instanceof ImageButton img && w != button && img.getWidth() == 20 && img.getHeight() == 18) {
                button.setX(img.getX() - 104 + 176 - 28 + dx);
                return;
            }
        }
    }

    /** Adds "Best in Pack: #3 of 54 chestplates" to an item tooltip (while Shift is held, by default). */
    public static void onTooltip(ItemStack stack, List<Component> lines) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || stack.isEmpty()) return;
        Config.TooltipRank mode = Config.get().tooltipRank;
        if (mode == Config.TooltipRank.OFF) return;
        if (mode == Config.TooltipRank.SHIFT && !Keys.shiftDown()) return;
        // Only reads the finished index – a tooltip never triggers the (slow) build itself.
        List<ItemIndex.RankEntry> ranks = ItemIndex.ranksFor(stack.getItem());
        Config cfg = Config.get();
        for (ItemIndex.RankEntry r : ranks) {
            if (!cfg.tooltipShows(r.groupKey())) continue;
            Component group = Component.translatable("bestinpack.group." + r.groupKey());
            Component line = Component.translatable("bestinpack.tooltip.rank", r.rank(), r.total(), group)
                    .withStyle(r.rank() == 1 ? ChatFormatting.GOLD : ChatFormatting.YELLOW);
            lines.add(line);
        }
    }

    /** The BiP button of each open inventory screen (weak: screens come and go). */
    private static final java.util.Map<Screen, Button> BUTTONS = new java.util.WeakHashMap<>();

    public static AbstractWidget buttonOf(Screen screen) {
        return BUTTONS.get(screen);
    }
}
