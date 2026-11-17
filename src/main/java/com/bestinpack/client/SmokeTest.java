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
                case 7 -> emiPanel(client);
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
        // NeoForge and Forge show a warning screen first when some mod loaded with warnings (REI's @OnlyIn on NeoForge
        // 26.3): go on to the title screen like a player pressing "Proceed to main menu".
        if (!worldRequested && client.level == null && screen != null && ticks > 100
                && screen.getClass().getSimpleName().equals("LoadingErrorScreen")) {
            log("leaving the loader's warning screen " + screen.getClass().getName());
            Screens.set(new TitleScreen());
            return;
        }
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
            if ("EMI".equals(RecipeViewers.available())) {
                check(now != null && now.getClass().getName().startsWith("dev.emi.emi.screen."), "the row click opened EMI's recipe screen");
                check(RecipeViewers.isRecipeKey(InputConstants.KEY_R, 0) && RecipeViewers.isUsesKey(InputConstants.KEY_U, 0)
                        && !RecipeViewers.isRecipeKey(InputConstants.KEY_U, 0), "EMI's recipe and uses keys (R, U) are recognised");
            }
            shot(client, "smoke_viewer");
            Screens.set(null);
            phase = 6;
            ticks = 0;
            return;
        }
        if (lateStart == 1) {   // the inventory has been open for 30 ticks: back to the rankings, then click the row again
            log("after the first inventory screen: " + RecipeViewers.available() + " started = " + RecipeViewers.hidingReady());
            Screens.set(new BestInPackScreen(null));
            lateStart = 2;
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
                if (!opened && !RecipeViewers.hidingReady() && lateStart == 0) {
                    // The viewer has not started although the world was joined long ago. JEI for Forge 1.20.2 does
                    // that: it misses the tags of the new configuration phase and only starts when the first
                    // inventory screen opens (its log says so). Until then a row click can only say "still loading".
                    // Open the inventory once, the way a player would, and click the row again.
                    log(viewer + " has not started yet - opening the inventory once, which is when it starts late");
                    Screens.set(new InventoryScreen(client.player));
                    lateStart = 1;
                    return;
                }
                check(opened, "row click opens " + viewer + (lateStart == 2 ? " (after its late start with the first inventory screen)" : ""));
            }
            cat++;
        }
    }

    /** 0: the viewer started normally; 1: it had not, the inventory is open to start it; 2: back in the rankings. */
    private static int lateStart;

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
            if (ready && !shiftLook(client, "smoke_shift")) {
                // the tooltip with Shift held is being photographed
            } else if (ready || ticks >= 100) {
                releaseShift();
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
                //#if MC < 1.20
                //$$ text = emiSearchText();
                //#else
                text = String.valueOf(Class.forName("dev.emi.emi.api.EmiApi").getMethod("getSearchText").invoke(null));
                //#endif
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
                    //#if MC < 1.20
                    //$$ emiSetSearchText("");
                    //#else
                    Class.forName("dev.emi.emi.api.EmiApi").getMethod("setSearchText", String.class).invoke(null, "");
                    //#endif
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
        //#if MC < 1.20
        //$$ // For the eye: the tooltip of the BiP button (before 1.19.3 it is drawn by the mod's own button class).
        //$$ if (ticks == 42 && Screens.current() instanceof InventoryScreen && BestInPackClient.buttonOf(Screens.current()) != null) {
        //$$     net.minecraft.client.gui.components.AbstractWidget bip = BestInPackClient.buttonOf(Screens.current());
        //$$     hover(client, bip.getX() + bip.getWidth() / 2.0, bip.getY() + bip.getHeight() / 2.0);
        //$$ }
        //$$ if (ticks == 52) fixHover(client);
        //$$ if (ticks == 130) shot(client, "smoke_invbutton");
        //$$ if (ticks == 135) {
        //#else
        if (ticks == 50) {
        //#endif
            Screens.set(null);
            if (com.bestinpack.platform.Platform.get().isModLoaded("emi")) {
                // stay in survival: its inventory leaves room for EMI's item panel
                phase = 7;
                ticks = 0;
                return;
            }
            if (server != null) {
                server.execute(() -> server.getPlayerList().getPlayers().forEach(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)));
            }
            phase = 4;
            ticks = 0;
        }
    }

    // ------------------------------------------------------------------ EMI (by name: this class is also compiled
    // into builds for game versions EMI does not exist for)

    private static Object emiStackOf(ItemStack stack) throws ReflectiveOperationException {
        return Class.forName("dev.emi.emi.api.stack.EmiStack").getMethod("of", ItemStack.class).invoke(null, stack);
    }

    /** The item EMI's panels show at this GUI position, or null. */
    private static net.minecraft.world.item.Item emiItemAt(int x, int y) throws ReflectiveOperationException {
        Object hit = Class.forName("dev.emi.emi.api.EmiApi").getMethod("getHoveredStack", int.class, int.class, boolean.class).invoke(null, x, y, false);
        Object ingredient = Class.forName("dev.emi.emi.api.stack.EmiStackInteraction").getMethod("getStack").invoke(hit);
        List<?> stacks = (List<?>) Class.forName("dev.emi.emi.api.stack.EmiIngredient").getMethod("getEmiStacks").invoke(ingredient);
        for (Object st : stacks) {
            ItemStack item = (ItemStack) Class.forName("dev.emi.emi.api.stack.EmiStack").getMethod("getItemStack").invoke(st);
            if (item != null && !item.isEmpty()) return item.getItem();
        }
        return null;
    }

    /** Centre (GUI pixels) of the cell in EMI's panels that shows the item, or null. */
    private static int[] findInEmi(Minecraft client, net.minecraft.world.item.Item item) throws ReflectiveOperationException {
        int w = client.getWindow().getGuiScaledWidth(), h = client.getWindow().getGuiScaledHeight();
        for (int y = 2; y < h; y += 5) {
            for (int x = 2; x < w; x += 5) {
                if (emiItemAt(x, y) != item) continue;
                int x0 = x, x1 = x, y0 = y, y1 = y;
                while (x0 > 0 && emiItemAt(x0 - 1, y) == item) x0--;
                while (x1 < w - 1 && emiItemAt(x1 + 1, y) == item) x1++;
                while (y0 > 0 && emiItemAt(x, y0 - 1) == item) y0--;
                while (y1 < h - 1 && emiItemAt(x, y1 + 1) == item) y1++;
                return new int[]{(x0 + x1) / 2, (y0 + y1) / 2};
            }
        }
        return null;
    }

    private static void emiSetHidden(ItemStack stack, boolean hidden) throws ReflectiveOperationException {
        Class.forName("dev.emi.emi.runtime.EmiHidden")
                .getMethod("setVisibility", Class.forName("dev.emi.emi.api.stack.EmiIngredient"), boolean.class, boolean.class)
                .invoke(null, emiStackOf(stack), hidden, false);
    }

    //#if MC < 1.20
    //$$ // EMI 0.4 – 0.7 (Fabric 1.18.2, 1.19, 1.19.1): no search text in the API before 0.5, no hiding before 1.0.
    //$$ private static net.minecraft.client.gui.components.EditBox emiSearchBox() throws ReflectiveOperationException {
    //$$     return (net.minecraft.client.gui.components.EditBox) Class.forName("dev.emi.emi.screen.EmiScreenManager").getField("search").get(null);
    //$$ }
    //$$
    //$$ private static String emiSearchText() throws ReflectiveOperationException {
    //$$     try {
    //$$         return String.valueOf(Class.forName("dev.emi.emi.api.EmiApi").getMethod("getSearchText").invoke(null));
    //$$     } catch (NoSuchMethodException e) {
    //$$         return emiSearchBox().getValue();
    //$$     }
    //$$ }
    //$$
    //$$ private static void emiSetSearchText(String text) throws ReflectiveOperationException {
    //$$     try {
    //$$         Class.forName("dev.emi.emi.api.EmiApi").getMethod("setSearchText", String.class).invoke(null, text);
    //$$     } catch (NoSuchMethodException e) {
    //$$         emiSearchBox().setValue(text);
    //$$     }
    //$$ }
    //$$
    //$$ private static boolean emiCanHide() {
    //$$     try {
    //$$         Class.forName("dev.emi.emi.runtime.EmiHidden");
    //$$         return true;
    //$$     } catch (Throwable t) {
    //$$         return false;
    //$$     }
    //$$ }
    //$$
    //#endif
    private static int[] emiAt;
    private static int emiFound = -1, emiPressed = -1;

    /**
     * EMI's item panel next to the survival inventory: the open key over a stack there jumps to that item's row,
     * and what the player hides in EMI disappears from the rankings (and comes back).
     */
    private static void emiPanel(Minecraft client) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        try {
            if (ticks == 1) {
                Screens.set(new InventoryScreen(client.player));
                //#if MC < 1.20
                //$$ emiSetSearchText("diamond sword");
                //#else
                Class.forName("dev.emi.emi.api.EmiApi").getMethod("setSearchText", String.class).invoke(null, "diamond sword");
                //#endif
            }
            // EMI searches on its own thread and lays its panel out while drawing: look for the sword for up to 15 s.
            if (ticks >= 20 && emiAt == null && ticks % 10 == 0) {
                emiAt = findInEmi(client, Items.DIAMOND_SWORD);
                if (emiAt != null) {
                    log("EMI shows the Diamond Sword at " + emiAt[0] + "," + emiAt[1] + " after " + ticks + " ticks");
                    hover(client, emiAt[0], emiAt[1]);
                    emiFound = ticks;
                } else if (ticks >= 300) {
                    check(false, "Diamond Sword found in EMI's item panel");
                    emiFound = emiPressed = -2;
                }
            }
            if (emiFound > 0 && ticks == emiFound + 10) fixHover(client);
            if (emiFound > 0 && emiPressed < 0 && ticks >= emiFound + 20) {
                // slow software rendering: wait (up to 5 s) until EMI itself reports the sword under the real pointer
                Screen inv = Screens.current();
                ItemStack hovered = inv == null ? ItemStack.EMPTY : com.bestinpack.compat.HoveredItem.get(inv);
                boolean ready = hovered.getItem() == Items.DIAMOND_SWORD;
                if (ready && !shiftLook(client, "smoke_emi_shift")) {
                    // the tooltip with Shift held is being photographed
                } else if (ready || ticks >= emiFound + 120) {
                    releaseShift();
                    log("hovered in EMI after " + (ticks - emiFound) + " ticks: " + hovered.getItem());
                    check(ready, "EMI reports the stack under the mouse (Diamond Sword)");
                    shot(client, "smoke_emi_panel");
                    int key = BestInPackClient.openKey == null ? InputConstants.KEY_B : Keys.code(BestInPackClient.openKey.saveString());
                    boolean used = inv != null && BestInPackClient.onScreenKey(inv, key, 0);
                    Screen now = Screens.current();
                    log("open key over EMI's panel used=" + used + " -> " + (now == null ? "none" : now.getClass().getSimpleName()));
                    check(now instanceof BestInPackScreen, "open key works over EMI's item panel");
                    if (now instanceof BestInPackScreen sc) {
                        List<String> rows = sc.firstNamesForTest(60);
                        check(rows.stream().anyMatch(r -> r.startsWith("Diamond Sword")), "jumped to the item hovered in EMI (Diamond Sword)");
                    }
                    emiPressed = ticks;
                }
            }
            if (emiPressed > 0 && ticks == emiPressed + 20) {
                shot(client, "smoke_emi_jump");
                check(RecipeViewers.hidingReady() && !RecipeViewers.isHidden(sword), "EMI lists the Diamond Sword (not hidden)");
                check(!ItemIndex.ranksFor(Items.DIAMOND_SWORD).isEmpty(), "Diamond Sword is ranked with EMI installed");
                //#if MC < 1.20
                //$$ if (!emiCanHide()) {
                //$$     log("note: this EMI cannot hide stacks yet (EMI before 1.0) - the hiding checks are skipped");
                //$$     emiPressed = -2;
                //$$ } else
                //#endif
                emiSetHidden(sword, true);
            }
            if (emiPressed > 0 && ticks == emiPressed + 40) {
                ItemIndex.ensureBuilt(client.level, client.player);
                check(RecipeViewers.isHidden(sword), "a stack hidden in EMI counts as hidden");
                check(ItemIndex.ranksFor(Items.DIAMOND_SWORD).isEmpty(), "a stack hidden in EMI leaves the rankings");
                emiSetHidden(sword, false);
            }
            if (emiPressed > 0 && ticks == emiPressed + 60) {
                ItemIndex.ensureBuilt(client.level, client.player);
                check(!RecipeViewers.isHidden(sword) && !ItemIndex.ranksFor(Items.DIAMOND_SWORD).isEmpty(), "shown again in EMI, it is ranked again");
            }
        } catch (Throwable t) {
            log("EMI panel test failed: " + t);
            check(false, "EMI panel test ran (" + t + ")");
            emiFound = emiPressed = -2;
        }
        if (emiPressed == -2 || (emiPressed > 0 && ticks >= emiPressed + 70)) {
            try {
                //#if MC < 1.20
                //$$ emiSetSearchText("");
                //#else
                Class.forName("dev.emi.emi.api.EmiApi").getMethod("setSearchText", String.class).invoke(null, "");
                //#endif
            } catch (Throwable ignored) {}
            Screens.set(null);
            var server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> server.getPlayerList().getPlayers().forEach(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)));
            }
            phase = 4;
            ticks = 0;
        }
    }

    private static int shiftAt = -1;
    private static boolean shiftShot;
    private static final java.util.Set<String> SHIFT_LOOKED = new java.util.HashSet<>();

    /**
     * For the eye, not a check: holds Shift for a moment over whatever the mouse is on and takes a screenshot, so the
     * rank line of the real, drawn tooltip can be looked at. Call every tick; true once the screenshot is taken and
     * Shift is up again.
     */
    private static boolean shiftLook(Minecraft client, String name) {
        if (SHIFT_LOOKED.contains(name)) return true;
        if (shiftAt < 0) {
            xraw("keydown", "Shift_L");
            shiftAt = ticks;
            shiftShot = false;
        } else if (!shiftShot && ticks >= shiftAt + 14) {
            shot(client, name);
            shiftShot = true;
        } else if (shiftShot && ticks >= shiftAt + 18) {
            releaseShift();
            SHIFT_LOOKED.add(name);
        }
        return false;
    }

    private static void releaseShift() {
        if (shiftAt < 0) return;
        xraw("keyup", "Shift_L");
        shiftAt = -1;
    }

    /** A key event without moving the pointer first (it has to stay on the hovered item). */
    private static void xraw(String... args) {
        try {
            List<String> cmd = new ArrayList<>(List.of("xdotool"));
            cmd.addAll(List.of(args));
            new ProcessBuilder(cmd).inheritIO().start().waitFor();
        } catch (Exception e) {
            log("xdotool failed: " + e);
        }
    }

    //#if MC < 1.20
    //$$ private static int lookTick;
    //$$
    //$$ /**
    //$$  * For the eye, not a check: the tooltips of the "?" and the gear button on the rankings screen, photographed
    //$$  * (before 1.19.3 they are drawn by the mod's own button classes, after the table). True once done.
    //$$  */
    //$$ private static boolean looked(Minecraft client) {
    //$$     if (lookTick < 0) return true;
    //$$     try {
    //$$         lookTick++;
    //$$         if (lookTick == 1 && client.level != null) Screens.set(new BestInPackScreen(null));
    //$$         if (Screens.current() instanceof BestInPackScreen s) {
    //$$             if (lookTick == 10) {
    //$$                 double[] c = s.widgetCenterForTest("help");
    //$$                 hover(client, c[0], c[1]);
    //$$             }
    //$$             if (lookTick == 20 || lookTick == 70) fixHover(client);
    //$$             if (lookTick == 50) shot(client, "smoke_help");
    //$$             if (lookTick == 60) hover(client, s.width - 20, s.height - 16);
    //$$             if (lookTick == 100) shot(client, "smoke_gear");
    //$$             if (lookTick < 105) return false;
    //$$         }
    //$$     } catch (Throwable t) {
    //$$         log("look at the button tooltips failed: " + t);
    //$$     }
    //$$     Screens.set(null);
    //$$     lookTick = -1;
    //$$     ticks = 0; // the result is logged on the next tick
    //$$     return false;
    //$$ }
    //$$
    //#endif
    private static void finish(Minecraft client) {
        //#if MC < 1.20
        //$$ if (!looked(client)) return;
        //#endif
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
        try {
            java.lang.reflect.Field fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(client.mouseHandler, guiX * scale);
            fy.setDouble(client.mouseHandler, guiY * scale);
            Keys.setCursor(guiX * scale, guiY * scale);
        } catch (ReflectiveOperationException e) {
            // A remapped production JAR (Fabric up to 1.21.11, Forge up to 1.20.4): the fields have other names there,
            // so the X pointer event alone has to carry the position. Do not warp the GLFW cursor as well - GLFW drops
            // the motion event that lands exactly where it was just told to put the cursor, and the game would never
            // hear about the move.
        }
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
