package com.bestinpack.client;

import com.bestinpack.BestInPack;
import com.bestinpack.client.gui.BestInPackScreen;
import com.bestinpack.compat.RecipeViewers;
import com.bestinpack.config.Config;
import com.bestinpack.index.Category;
import com.bestinpack.index.ItemIndex;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-game smoke test for every game version and loader (only with -Dbestinpack.selftest=true): creates a world,
 * checks the join messages, walks through every tab, search, sort, details, the open key over an inventory slot,
 * the tooltip rank line, the recipe viewer, and takes screenshots. Ends with "SMOKE OK" and closes the game.
 */
public final class SmokeTest {
    private SmokeTest() {}

    private static int phase, ticks, waited;
    private static boolean worldRequested;
    private static final List<String> FAILURES = new ArrayList<>();

    /** The loader passes a function that registers a per-tick callback. */
    public static void install(Consumer<Runnable> onTick) {
        try {
            Files.deleteIfExists(JoinMessages.welcomedFile());
        } catch (Exception ignored) {}
        onTick.accept(SmokeTest::tick);
        log("installed (" + com.bestinpack.platform.Platform.get().loader() + ")");
    }

    private static void log(String s) {
        BestInPack.LOGGER.info("SMOKE: {}", s);
    }

    private static void check(boolean ok, String what) {
        log((ok ? "PASS " : "FAIL ") + what);
        if (!ok) FAILURES.add(what);
    }

    private static void tick() {
        Minecraft client = Minecraft.getInstance();
        ticks++;
        try {
            switch (phase) {
                case 0 -> waitForWorld(client);
                case 1 -> joined(client);
                case 2 -> tabs(client);
                case 3 -> inventory(client);
                case 4 -> finish(client);
                case 5 -> survival(client);
                case 6 -> emiSearch(client);
                default -> {}
            }
        } catch (Throwable t) {
            BestInPack.LOGGER.error("SMOKE: FAIL exception", t);
            FAILURES.add("exception " + t);
            phase = 4;
            ticks = 0;
        }
    }

    private static void waitForWorld(Minecraft client) {
        Screen screen = Screens.current();
        if (ticks % 100 == 0) log("waiting, screen=" + (screen == null ? "none" : screen.getClass().getSimpleName()) + " level=" + (client.level != null));
        if (!worldRequested && client.level == null && screen instanceof TitleScreen && ticks > 40) {
            worldRequested = true;
            log("creating world");
            WorldStarter.createOrLoad(client, "Test");
            return;
        }
        if (client.level != null && client.player != null && screen == null) {
            phase = 1;
            ticks = 0;
        } else if (ticks > 20 * 400) {
            FAILURES.add("world never loaded");
            phase = 4;
            ticks = 0;
        }
    }

    private static void joined(Minecraft client) {
        // The welcome message and the key note come 40 ticks after joining.
        if (ticks == 80) {
            boolean recorded = false;
            try {
                recorded = Files.readAllLines(JoinMessages.welcomedFile()).contains(Discovery.worldKey());
            } catch (Exception ignored) {}
            check(recorded, "welcome message shown and recorded for " + Discovery.worldKey());
            Component w = JoinMessages.welcomeText();
            log("welcome text = " + (w == null ? "null" : w.getString()));
            check(w != null && JoinMessages.hoverTextOf(w) != null && JoinMessages.hoverTextOf(w).contains("welcomeMessage"),
                    "welcome hover tells how to turn it off");
            check(!JoinMessages.welcome(client, Discovery.worldKey()), "no second welcome in the same world");
            Config cfg = Config.get();
            cfg.welcomeMessage = false;
            check(!JoinMessages.welcome(client, "world_smoke_off"), "welcomeMessage=false shows nothing");
            cfg.welcomeMessage = true;
            // openKey: the config's default key (for modpacks) – short names are understood, nonsense is refused.
            check("key.keyboard.n".equals(Config.normalizeKey("N")), "openKey accepts \"N\"");
            check("key.keyboard.left.alt".equals(Config.normalizeKey("left alt")), "openKey accepts \"left alt\"");
            check(Config.normalizeKey("nonsense key") == null, "openKey refuses unknown key names");
            check("key.keyboard.unknown".equals(Config.normalizeKey("none")), "openKey \"none\" means no key");
            String wanted = Config.get().openKey;
            check(BestInPackClient.openKey.getDefaultKey().getName().equals(wanted),
                    "default open key follows openKey in the config (" + wanted + ")");
            List<String> conflicts = JoinMessages.conflictsOf(BestInPackClient.openKey, client.options.keyMappings).stream().map(Component::getString).toList();
            log("real key conflicts = " + conflicts);
            check(conflicts.stream().noneMatch(c -> c.contains("Hitboxes")), "F3 debug keys are not reported as key conflicts");
            check(BestInPackClient.openKey != null && !BestInPackClient.openKey.isUnbound(), "open key registered: "
                    + (BestInPackClient.openKey == null ? "none" : BestInPackClient.openKey.getTranslatedKeyMessage().getString()));
            shot(client, "smoke_chat");
        }
        // F3 + B toggles hitboxes and must not open Best in Pack; B alone must. Real key presses, waited for.
        if (ticks == 84) {
            hitboxesBefore = hitboxesShown(client);
            xdotool("keydown", "F3", "key", "b", "keyup", "F3");
            keyStage = 1;
            return;
        }
        if (keyStage == 1) {
            boolean toggled = hitboxesShown(client) != hitboxesBefore;
            if (!toggled && ticks < 180) return;
            check(toggled, "F3 + B toggles hitboxes (after " + (ticks - 84) + " ticks)");
            check(!(Screens.current() instanceof BestInPackScreen), "F3 + B does not open Best in Pack");
            if (Screens.current() != null) Screens.set(null);
            String bound = BestInPackClient.openKey.saveString();
            String keysym = bound.matches("key\\.keyboard\\.[a-z0-9]") ? bound.substring("key.keyboard.".length()) : "b";
            log("open key is " + bound + ", pressing '" + keysym + "'");
            xdotool("keydown", "F3", "key", "b", "keyup", "F3", "key", keysym); // hitboxes off again, then the open key alone
            keyStage = 2;
            keyStageTick = ticks;
            return;
        }
        if (keyStage == 2) {
            boolean open = Screens.current() instanceof BestInPackScreen;
            if (!open && ticks < keyStageTick + 100) return;
            check(open, "the open key in the world opens Best in Pack (after " + (ticks - keyStageTick) + " ticks)");
            Screens.set(null);
            keyStage = 3;
            check(ItemIndex.isBuilt(), "index built after join");
            // Only items from the creative inventory are ranked; the config switch turns the filter off and on again.
            int skipped = ItemIndex.lastNotInCreative();
            log("items not in the creative inventory skipped = " + skipped);
            check(skipped >= 0, "creative-inventory filter active");
            Config cfgc = Config.get();
            cfgc.onlyCreativeItems = false;
            ItemIndex.ensureBuilt(client.level, client.player);
            check(ItemIndex.lastNotInCreative() == -1, "onlyCreativeItems=false ranks every item");
            cfgc.onlyCreativeItems = true;
            ItemIndex.ensureBuilt(client.level, client.player);
            check(ItemIndex.lastNotInCreative() == skipped, "onlyCreativeItems=true filters again");
            check(!ItemIndex.ranksFor(Items.DIAMOND_SWORD).isEmpty() && !ItemIndex.ranksFor(Items.COOKED_BEEF).isEmpty(),
                    "creative items still ranked (diamond sword, steak)");
            Screens.set(new BestInPackScreen(null));
            phase = 2;
            ticks = 0;
        }
    }

    private static int cat;

    private static void tabs(Minecraft client) {
        if (ticks % 30 != 0) return;
        Category[] cats = Category.values();
        if (cat == cats.length + 2) {
            Screen now = Screens.current();
            log("screen after row click = " + (now == null ? "none" : now.getClass().getName()));
            shot(client, "smoke_viewer");
            Screens.set(null);
            phase = 6;
            ticks = 0;
            return;
        }
        if (!(Screens.current() instanceof BestInPackScreen screen)) {
            FAILURES.add("Best in Pack screen not open");
            phase = 4;
            ticks = 0;
            return;
        }
        if (cat < cats.length) {
            screen.selectCategoryForTest(cats[cat]);
            screen.resetSortForTest();
            int rows = screen.visibleRowCountForTest();
            log(cats[cat].id + ": " + rows + " rows, top " + screen.firstNamesForTest(5));
            check(rows > 0, cats[cat].id + " tab has rows");
            if (cats[cat] == Category.FUEL) {
                check(ItemIndex.fmtCompact(107_374_182.4, 1).equals("107.4M") && ItemIndex.fmtGrouped(107_374_182.4, 1).equals("107 374 182.4"),
                        "compact fuel numbers");
            }
            cat++;
            return;
        }
        if (cat == cats.length) {
            shot(client, "smoke_fuel");
            screen.selectCategoryForTest(Category.WEAPONS);
            screen.setSearchForTest("@minecraft sword");
            List<String> swords = screen.firstNamesForTest(10);
            log("search '@minecraft sword' = " + swords);
            check(!swords.isEmpty() && swords.stream().allMatch(s -> s.contains("Sword")), "@mod search");
            screen.setSearchForTest("sword -diamond");
            check(screen.firstNamesForTest(20).stream().noneMatch(s -> s.contains("Diamond")), "-word search");
            screen.setSearchForTest("");
            int before = screen.shownColumnCountForTest();
            boolean focused = screen.clickDetailsForTest();
            check(screen.detailsForTest() && screen.shownColumnCountForTest() > before && !focused, "Details button");
            screen.clickDetailsForTest();
            screen.selectCategoryForTest(Category.ARMOR);
            screen.resetSortForTest();
            screen.toggleSortForTest("armor");
            List<String> asc = screen.firstNamesForTest(3);
            screen.toggleSortForTest("armor");
            log("armor sorted ascending " + asc + ", descending " + screen.firstNamesForTest(3));
            check(!asc.equals(screen.firstNamesForTest(3)), "column sort toggles");
            cat++;
            return;
        }
        if (cat == cats.length + 1) {
            shot(client, "smoke_armor");
            String viewer = RecipeViewers.available();
            log("recipe viewer = " + viewer);
            if (viewer != null) {
                screen.selectCategoryForTest(Category.WEAPONS);
                screen.resetSortForTest();
                boolean opened = screen.openRecipesForTest();
                check(opened, "row click opens " + viewer);
            }
            cat++;
        }
    }

    private static void inventory(Minecraft client) {
        if (ticks == 1) {
            client.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_CHESTPLATE));
            Screens.set(new InventoryScreen(client.player));
        }
        if (ticks == 20) {
            Screen inv = Screens.current();
            check(inv instanceof InventoryScreen || inv instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen, "inventory open");
            check(!(inv instanceof InventoryScreen) || BestInPackClient.buttonOf(inv) != null, "BiP button on the inventory");
            // Hover a slot holding the chestplate and press the open key: the screen must jump to the chestplate's row.
            // (The test world is creative, so this is usually the creative inventory – find the slot instead of guessing.)
            int[] at = inv instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs
                    ? slotCenter(acs, Items.DIAMOND_CHESTPLATE) : null;
            log("chestplate slot at " + (at == null ? "none" : at[0] + "," + at[1]));
            if (at != null) hover(client, at[0], at[1]);
        }
        if (ticks == 30) fixHover(client);
        // Software rendering on the test display is slow: wait (up to 3 s) until the game itself reports the chestplate
        // slot as hovered before pressing the key, so the test checks the mod and not the frame rate.
        if (ticks >= 40 && keyTick < 0) {
            net.minecraft.world.inventory.Slot hs = Screens.current() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs
                    ? com.bestinpack.platform.Platform.get().hoveredSlot(acs) : null;
            boolean ready = hs != null && hs.getItem().getItem() == Items.DIAMOND_CHESTPLATE;
            if (ready || ticks >= 100) {
                log("hovered slot after " + ticks + " ticks: " + (hs == null ? "none" : "index " + hs.index + " item " + hs.getItem().getItem()));
                shot(client, "smoke_inventory");
                keyTick = ticks;
            }
        }
        if (ticks == keyTick) {
            Screen inv = Screens.current();
            double scale = client.getWindow().getGuiScale();
            Object slot = inv instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs
                    ? com.bestinpack.platform.Platform.get().hoveredSlot(acs) : "n/a";
            log("before key: screen=" + (inv == null ? "none" : inv.getClass().getSimpleName()) + " mouse="
                    + (client.mouseHandler.xpos() / scale) + "," + (client.mouseHandler.ypos() / scale) + " gui=" + client.getWindow().getGuiScaledWidth()
                    + "x" + client.getWindow().getGuiScaledHeight() + " scale=" + scale + " hoveredSlot=" + slot);
            int key = BestInPackClient.openKey == null ? InputConstants.KEY_B : Keys.code(BestInPackClient.openKey.saveString());
            boolean used = inv != null && BestInPackClient.onScreenKey(inv, key, 0);
            Screen now = Screens.current();
            log("open key in inventory used=" + used + " -> " + (now == null ? "none" : now.getClass().getSimpleName()));
            check(now instanceof BestInPackScreen, "open key works in the inventory");
            if (now instanceof BestInPackScreen sc) {
                List<String> rows = sc.firstNamesForTest(40);
                log("rows after jump = " + rows.subList(0, Math.min(3, rows.size())));
                check(rows.stream().anyMatch(r -> r.startsWith("Diamond Chestplate")), "jumped to the hovered item (Diamond Chestplate)");
            }
        }
        if (keyTick > 0 && ticks == keyTick + 20) {
            keyTick = -1;
            shot(client, "smoke_jump");
            Config cfg = Config.get();
            Config.TooltipRank old = cfg.tooltipRank;
            cfg.tooltipRank = Config.TooltipRank.ALWAYS;
            List<Component> lines = new ArrayList<>();
            BestInPackClient.onTooltip(new ItemStack(Items.DIAMOND_SWORD), lines);
            log("tooltip lines = " + lines.stream().map(Component::getString).toList());
            check(!lines.isEmpty() && lines.get(0).getString().startsWith("Best in Pack: #"), "tooltip rank line");
            cfg.tooltipRank = Config.TooltipRank.OFF;
            List<Component> none = new ArrayList<>();
            BestInPackClient.onTooltip(new ItemStack(Items.DIAMOND_SWORD), none);
            check(none.isEmpty(), "tooltipRank OFF hides the line");
            cfg.tooltipRank = old;
            Screens.set(null);
            phase = 5;
            ticks = 0;
        }
    }

    private static int emiAsked = -1;

    /** EMI keeps its search box outside the screen's widgets: typing B there must not open Best in Pack. */
    private static void emiSearch(Minecraft client) {
        if (!com.bestinpack.platform.Platform.get().isModLoaded("emi")) {
            phase = 3;
            ticks = 0;
            return;
        }
        if (ticks == 1) Screens.set(new InventoryScreen(client.player));
        if (ticks == 10) {
            try {
                Object search = Class.forName("dev.emi.emi.screen.EmiScreenManager").getField("search").get(null);
                ((net.minecraft.client.gui.components.EditBox) search).setFocused(true);
                xdotool("key", "b");
                emiAsked = ticks;
            } catch (Throwable t) {
                log("EMI search test could not start: " + t);
            }
        }
        if (ticks > 10) {
            String text = "?";
            try {
                text = String.valueOf(Class.forName("dev.emi.emi.api.EmiApi").getMethod("getSearchText").invoke(null));
            } catch (Throwable t) {
                log("EMI search test could not read EMI: " + t);
            }
            Screen now = Screens.current();
            // Slow software rendering can take a while to deliver the key: wait for it to land somewhere (max 5 s).
            boolean landed = !text.isEmpty() || now instanceof BestInPackScreen;
            if (landed || ticks >= 110) {
                log("EMI search after typing B = '" + text + "' after " + (ticks - emiAsked) + " ticks, screen = "
                        + (now == null ? "none" : now.getClass().getSimpleName()));
                check(!(now instanceof BestInPackScreen), "typing B in EMI's search does not open Best in Pack");
                check(text.toLowerCase(java.util.Locale.ROOT).contains("b"), "the B reached EMI's search box");
                try {
                    Class.forName("dev.emi.emi.api.EmiApi").getMethod("setSearchText", String.class).invoke(null, "");
                    Object search = Class.forName("dev.emi.emi.screen.EmiScreenManager").getField("search").get(null);
                    ((net.minecraft.client.gui.components.EditBox) search).setFocused(false);
                } catch (Throwable ignored) {}
                Screens.set(null);
                phase = 3;
                ticks = 0;
            }
        }
    }

    /** The survival inventory: the BiP button is there and must not cover buttons other mods put on the screen. */
    private static void survival(Minecraft client) {
        var server = client.getSingleplayerServer();
        if (ticks == 1 && server != null) {
            server.execute(() -> server.getPlayerList().getPlayers().forEach(p -> p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)));
        }
        if (ticks == 20) Screens.set(new InventoryScreen(client.player));
        if (ticks == 40) {
            Screen inv = Screens.current();
            check(inv instanceof InventoryScreen, "survival inventory open");
            net.minecraft.client.gui.components.AbstractWidget bip = inv == null ? null : BestInPackClient.buttonOf(inv);
            check(bip != null, "BiP button on the survival inventory");
            if (inv != null && bip != null) {
                List<String> others = new ArrayList<>();
                List<String> overlapping = new ArrayList<>();
                for (net.minecraft.client.gui.components.events.GuiEventListener l : inv.children()) {
                    if (!(l instanceof net.minecraft.client.gui.components.AbstractWidget w) || w == bip || !w.visible) continue;
                    String d = w.getClass().getSimpleName() + "@" + w.getX() + "," + w.getY() + " " + w.getWidth() + "x" + w.getHeight();
                    others.add(d);
                    boolean hit = w.getX() < bip.getX() + bip.getWidth() && bip.getX() < w.getX() + w.getWidth()
                            && w.getY() < bip.getY() + bip.getHeight() && bip.getY() < w.getY() + w.getHeight();
                    if (hit) overlapping.add(d);
                }
                log("BiP button at " + bip.getX() + "," + bip.getY() + " " + bip.getWidth() + "x" + bip.getHeight() + "; other widgets " + others);
                check(overlapping.isEmpty(), "BiP button covers no other button " + overlapping);
            }
            shot(client, "smoke_survival");
        }
        if (ticks == 50) {
            Screens.set(null);
            if (server != null) {
                server.execute(() -> server.getPlayerList().getPlayers().forEach(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)));
            }
            phase = 4;
            ticks = 0;
        }
    }

    private static void finish(Minecraft client) {
        if (ticks == 1) {
            if (FAILURES.isEmpty()) log("SMOKE OK");
            else log("SMOKE FAILED " + FAILURES);
        }
        if (ticks == 40) client.stop();
    }

    /** Screen position (GUI pixels) of the centre of the first visible slot holding this item. */
    private static int[] slotCenter(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen,
                                    net.minecraft.world.item.Item item) {
        int left, top;
        try {
            java.lang.reflect.Field l = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("leftPos");
            java.lang.reflect.Field t = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("topPos");
            l.setAccessible(true);
            t.setAccessible(true);
            left = l.getInt(screen);
            top = t.getInt(screen);
        } catch (ReflectiveOperationException e) {
            // Obfuscated production game (Fabric on 1.21.x): vanilla centres both inventories, so work it out.
            boolean creative = screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
            left = (screen.width - (creative ? 195 : 176)) / 2;
            top = (screen.height - (creative ? 136 : 166)) / 2;
        }
        for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
            if (slot.isActive() && slot.getItem().getItem() == item) return new int[]{left + slot.x + 8, top + slot.y + 8};
        }
        return null;
    }

    private static double hoverX = -1, hoverY = -1;
    private static int keyStage, keyStageTick;
    private static boolean hitboxesBefore;

    private static boolean hitboxesShown(Minecraft client) {
        //#if MC >= 1.21.9
        //$$ return client.debugEntries.isCurrentlyEnabled(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES);
        //#else
        return client.getEntityRenderDispatcher().shouldRenderHitBoxes();
        //#endif
    }

    /**
     * Real key presses through the X server, into the game window. The test display has no window manager, so keys
     * go to the window under the pointer: put the pointer over the game first (focusing it directly fails for the
     * SDL window of 26.3+), then press.
     */
    private static void xdotool(String... args) {
        try {
            Minecraft client = Minecraft.getInstance();
            int x = client.getWindow().getScreenWidth() / 2, y = client.getWindow().getScreenHeight() / 2;
            new ProcessBuilder("xdotool", "search", "--onlyvisible", "--limit", "1", "--name", "Minecraft", "mousemove", "--window", "%1",
                    String.valueOf(x), String.valueOf(y)).inheritIO().start().waitFor();
            List<String> cmd = new ArrayList<>(List.of("xdotool"));
            cmd.addAll(List.of(args));
            new ProcessBuilder(cmd).inheritIO().start().waitFor();
        } catch (Exception e) {
            log("xdotool failed: " + e);
        }
    }
    private static int keyTick = -1;

    /** If the pointer landed somewhere else (window placement differs per windowing backend), nudge it by the error. */
    private static void fixHover(Minecraft client) {
        if (hoverX < 0) return;
        int dx = (int) Math.round(hoverX - client.mouseHandler.xpos()), dy = (int) Math.round(hoverY - client.mouseHandler.ypos());
        if (Math.abs(dx) <= 2 && Math.abs(dy) <= 2) return;
        log("pointer off by " + dx + "," + dy + " – correcting");
        try {
            new ProcessBuilder("xdotool", "mousemove_relative", "--", String.valueOf(dx), String.valueOf(dy)).inheritIO().start().waitFor();
        } catch (Exception ignored) {}
    }

    private static void hover(Minecraft client, double guiX, double guiY) {
        double scale = client.getWindow().getGuiScale();
        hoverX = guiX * scale;
        hoverY = guiY * scale;
        // Move the real X pointer (works for GLFW and SDL alike), relative to the game window – the game centres
        // its window on the test display, so absolute screen coordinates would land somewhere else.
        String px = String.valueOf((int) (guiX * scale)), py = String.valueOf((int) (guiY * scale));
        try {
            int exit = new ProcessBuilder("xdotool", "search", "--onlyvisible", "--limit", "1", "--name", "Minecraft", "mousemove", "--window", "%1", px, py)
                    .inheritIO().start().waitFor();
            if (exit != 0) {
                int ox = 640 - client.getWindow().getScreenWidth() / 2, oy = 360 - client.getWindow().getScreenHeight() / 2;
                new ProcessBuilder("xdotool", "mousemove", String.valueOf(ox + (int) (guiX * scale)), String.valueOf(oy + (int) (guiY * scale)))
                        .inheritIO().start().waitFor();
            }
        } catch (Exception ignored) {}
        Keys.setCursor(guiX * scale, guiY * scale);
        try {
            java.lang.reflect.Field fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(client.mouseHandler, guiX * scale);
            fy.setDouble(client.mouseHandler, guiY * scale);
        } catch (ReflectiveOperationException ignored) {}
    }

    private static void shot(Minecraft client, String name) {
        try {
            Screenshots.take(client, name);
        } catch (Throwable t) {
            log("screenshot failed: " + t);
        }
    }

    /** Where screenshots go (for the log). */
    public static Path screenshotDir(Minecraft client) {
        return client.gameDirectory.toPath().resolve("screenshots");
    }
}
