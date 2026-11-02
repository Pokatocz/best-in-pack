package com.bestinpack.client.gui;

import com.bestinpack.compat.RecipeViewers;
import com.bestinpack.config.Config;
import com.bestinpack.index.Category;
import com.bestinpack.index.Column;
import com.bestinpack.index.ItemIndex;
import com.bestinpack.index.Row;
import com.bestinpack.index.Table;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.bestinpack.util.Ids;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The rankings screen. Kept deliberately small: tabs, a search box, "Have" and "Details" toggles,
 * filter chips where they help (armor slot, tool type), and a table with only the essential columns
 * unless Details is on. Rows with identical stats are collapsed into one line.
 *
 * The "#" column is the row's place in the list as shown right now (after search, chip and sort); a group of
 * identical items is one row. The Shift-tooltip reports the fixed rank within the whole tab/chip instead.
 */
public class BestInPackScreen extends Screen {

    private static final int HEADER_H = 14;
    private static final int HEADER_PAD = 6; // left + right padding of a header label
    private static final int TAB_Y = 22;
    private static final int TOOLS_Y = 48;
    private static final int CHIPS_Y = 72;
    private static final int CHIP_H = 16;
    private static final int BOTTOM_PAD = 46;
    private static final int SCROLLBAR_W = 6;
    private static final int HSCROLL_H = 6;
    private static final int ITEM_MIN_W = 140;
    private static final int ITEM_MAX_W = 260;


    private static final String ELLIPSIS = "…";
    private static final String ARROW_DOWN = "▼", ARROW_UP = "▲";

    /** Rows with identical stats collapsed into one line. */
    private record Group(Row head, List<Row> others) {
        int size() { return 1 + others.size(); }
    }

    //#if MC >= 1.21.11
    //$$ private static final net.minecraft.resources.Identifier LIST_BACKGROUND = Ids.vanilla("textures/gui/inworld_menu_list_background.png");
    //#else
    private static final net.minecraft.resources.ResourceLocation LIST_BACKGROUND = Ids.vanilla("textures/gui/inworld_menu_list_background.png");
    //#endif

    private static final WidgetSprites GEAR = new WidgetSprites(Ids.of("bestinpack", "gear"), Ids.of("bestinpack", "gear_hover"));

    /** Remembered between openings. */
    private static Category lastCategory = Category.FOOD;
    private static Boolean lastDetails = null; // null = not touched this session, use the config default
    private static final Map<Category, int[]> lastSort = new EnumMap<>(Category.class); // [col, desc(1/0)]
    private static final Map<Category, String> lastFacet = new EnumMap<>(Category.class);

    private final Screen parent;
    private final Config config = Config.reload();
    private final Theme theme = Theme.of(config.style == null ? Config.Style.DARK : config.style);
    private final int rowH = theme.rowH();
    private Category category = config.rememberTab ? lastCategory : Category.FOOD;
    private Table table;
    private final List<Group> visible = new ArrayList<>();
    private int sortCol = -1;
    private boolean sortDesc = true;
    private boolean haveOnly = false;
    private boolean details = lastDetails != null ? lastDetails : config.detailsByDefault;
    private String facet = null; // null = all
    private Set<Item> haveItems = Set.of();
    private String search = "";
    private double scroll = 0;
    private double hScroll = 0;
    private Component statusOverride;
    private long statusOverrideUntil;
    /** Items in the current view the player knows (onlyDiscovered), for the status line. */
    private int knownCount;
    /** Discovery version the list was built with – rebuilt when the player picks up something new. */
    private int discoveryVersion = -1;

    /** Item to scroll to and outline right after opening (open key over an item), and how long the outline stays. */
    private Item jumpItem;
    private Item highlightItem;
    private long highlightUntil;

    // Scrollbar dragging
    private boolean draggingV, draggingH;
    private double dragGrab;

    private EditBox searchField;
    private Button haveButton;
    private Button detailsButton;
    private Button helpButton;
    private final Map<Category, Button> tabButtons = new EnumMap<>(Category.class);
    private final List<Button> chipButtons = new ArrayList<>();
    private final Map<String, Button> chipByFacet = new HashMap<>();

    // Layout computed in layoutColumns()
    private int headerY;
    private List<Column> frozenCols = List.of();
    private List<Column> scrollCols = List.of();
    private int[] frozenX = new int[0], frozenW = new int[0];
    private int[] scrollX = new int[0], scrollW = new int[0]; // scrollX relative to viewport start
    private int tableX, tableW, frozenTotalW, scrollTotalW, viewportW, listTop, listBottom;

    public BestInPackScreen(Screen parent) {
        super(Component.translatable("bestinpack.screen.title"));
        this.parent = parent;
    }

    /**
     * A screen that opens on the given item: its tab, its chip, natural sort, no search – scrolled to the row and
     * with the row outlined. Falls back to the normal screen when the item is not ranked anywhere.
     */
    public static BestInPackScreen openAt(Screen parent, Item item) {
        BestInPackScreen s = new BestInPackScreen(parent);
        Minecraft client = Minecraft.getInstance();
        // Food, armor and tools before fuel – a wooden sword is more interesting as a sword than as firewood.
        for (Category cat : Category.values()) {
            Table t = ItemIndex.get(cat, client.level, client.player);
            if (t == null || (cat == Category.FUEL && !ItemIndex.hasFuelData())) continue;
            for (Row r : t.rows) {
                if (r.item != item) continue;
                s.category = cat;
                lastCategory = cat;
                lastFacet.put(cat, r.facet != null && t.facets().contains(r.facet) ? r.facet : null);
                lastSort.remove(cat);
                s.jumpItem = item;
                return s;
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        tabButtons.clear();
        int tabW = Math.min(92, (this.width - 40) / Category.values().length), gap = 4;
        int totalTabs = Category.values().length * tabW + (Category.values().length - 1) * gap;
        int tx = (this.width - totalTabs) / 2;
        for (Category cat : Category.values()) {
            Button b = Button.builder(cat.label(), btn -> switchCategory(cat))
                    .bounds(tx, TAB_Y, tabW, 20).build();
            tabButtons.put(cat, b);
            this.addRenderableWidget(b);
            tx += tabW + gap;
        }

        int toolsW = Math.min(this.width - 20, 560);
        int toolsX = (this.width - toolsW) / 2;
        int haveW = 50, detailsW = 56, smallW = 20;
        int searchW = toolsW - haveW - detailsW - smallW - 12;
        searchField = new EditBox(this.font, toolsX, TOOLS_Y, searchW, 20, Component.translatable("bestinpack.search.hint"));
        searchField.setMaxLength(100);
        searchField.setHint(Component.translatable("bestinpack.search.hint"));
        searchField.setValue(search);
        searchField.setResponder(s -> {
            search = s;
            applyFilter();
        });
        this.addRenderableWidget(searchField);

        int bx = toolsX + searchW + 4;
        haveButton = Button.builder(haveLabel(), btn -> {
            haveOnly = !haveOnly;
            btn.setMessage(haveLabel());
            applyFilter();
        }).bounds(bx, TOOLS_Y, haveW, 20).build();
        haveButton.setTooltip(Tooltip.create(Component.translatable("bestinpack.button.have.tooltip")));
        this.addRenderableWidget(haveButton);
        bx += haveW + 4;

        detailsButton = Button.builder(detailsLabel(), btn -> {
            details = !details;
            lastDetails = details;
            btn.setMessage(detailsLabel());
            hScroll = 0;
            layoutColumns();
            applyFilter();
        }).bounds(bx, TOOLS_Y, detailsW, 20).build();
        detailsButton.setTooltip(Tooltip.create(Component.translatable("bestinpack.button.details.tooltip")));
        this.addRenderableWidget(detailsButton);
        bx += detailsW + 4;

        helpButton = Button.builder(Component.literal("?"), btn -> {}).bounds(bx, TOOLS_Y, smallW, 20).build();
        helpButton.setTooltip(Tooltip.create(helpText()));
        this.addRenderableWidget(helpButton);

        this.addRenderableWidget(Button.builder(Component.translatable("bestinpack.button.done"), btn -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());

        // Settings live in config/bestinpack/ – the gear opens that folder, like JEI's wrench.
        ImageButton gear = new ImageButton(this.width - 30, this.height - 26, 20, 20, GEAR,
                btn -> {
                    config.save();
                    Gfx.openFolder(Config.dir());
                }, Component.translatable("bestinpack.button.config"));
        gear.setTooltip(Tooltip.create(Component.translatable("bestinpack.button.config.tooltip")));
        this.addRenderableWidget(gear);

        loadTable();
        if (jumpItem != null) {
            jumpTo(jumpItem);
            jumpItem = null;
        }
    }

    /** Scrolls the row that holds the item into the middle of the list and outlines it for a while. */
    private void jumpTo(Item item) {
        if (table == null) return;
        search = "";
        searchField.setValue("");
        haveOnly = false;
        haveButton.setMessage(haveLabel());
        sortCol = table.sortFor(facet);
        sortDesc = table.columns.get(sortCol).descDefault();
        applyFilter();
        for (int i = 0; i < visible.size(); i++) {
            Group g = visible.get(i);
            boolean here = g.head.item == item;
            if (!here) for (Row o : g.others) if (o.item == item) { here = true; break; }
            if (!here) continue;
            scroll = (i - visibleRows() / 2) * (double) rowH;
            clampScroll();
            highlightItem = item;
            highlightUntil = System.currentTimeMillis() + 8000;
            setStatus(Component.translatable("bestinpack.status.jumped", g.head.stack.getHoverName()));
            return;
        }
    }

    /** Four short lines – a taller tooltip does not fit on small windows. */
    /** One short line per control – "what you do: what happens". */
    private Component helpText() {
        List<Component> lines = new ArrayList<>();
        // Title line: Minecraft leaves a small gap under the first tooltip line, so give it a heading.
        lines.add(Component.translatable("bestinpack.help.title").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("bestinpack.help.sort"));
        if (RecipeViewers.available() != null) {
            lines.add(Component.translatable("bestinpack.help.click", RecipeViewers.recipeKeyName()));
            lines.add(Component.translatable("bestinpack.help.uses", RecipeViewers.usesKeyName()));
        } else {
            lines.add(Component.translatable("bestinpack.help.click.noviewer"));
        }
        lines.add(Component.translatable("bestinpack.help.filter"));
        if (config.openKeyInMenus) {
            Component key = com.bestinpack.client.BestInPackClient.openKey != null
                    ? com.bestinpack.client.BestInPackClient.openKey.getTranslatedKeyMessage() : Component.literal("B");
            lines.add(Component.empty().append(key.copy().withStyle(ChatFormatting.YELLOW)).append(Component.translatable("bestinpack.help.jump")));
        }
        switch (config.tooltipRank) {
            case SHIFT -> lines.add(Component.translatable("bestinpack.help.shift"));
            case ALWAYS -> lines.add(Component.translatable("bestinpack.help.always"));
            default -> {}
        }
        lines.add(Component.translatable("bestinpack.help.green"));
        lines.add(Component.translatable("bestinpack.help.plus"));
        MutableComponent t = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) t.append("\n");
            t.append(lines.get(i));
        }
        return t;
    }

    private Component haveLabel() {
        Component base = Component.translatable("bestinpack.button.have");
        return haveOnly ? Component.literal("✔ ").append(base) : base;
    }

    private Component detailsLabel() {
        Component base = Component.translatable("bestinpack.button.details");
        return details ? Component.literal("✔ ").append(base) : base;
    }

    private void switchCategory(Category cat) {
        if (cat == category) return;
        lastSort.put(category, new int[]{sortCol, sortDesc ? 1 : 0});
        category = cat;
        lastCategory = cat;
        scroll = 0;
        hScroll = 0;
        int[] remembered = lastSort.get(cat);
        if (remembered != null) {
            sortCol = remembered[0];
            sortDesc = remembered[1] == 1;
        } else {
            sortCol = -1;
        }
        loadTable();
    }

    private void loadTable() {
        Minecraft client = Minecraft.getInstance();
        for (Map.Entry<Category, Button> e : tabButtons.entrySet()) {
            e.getValue().active = e.getKey() != category;
        }
        table = ItemIndex.get(category, client.level, client.player);
        if (category == Category.FUEL && !ItemIndex.hasFuelData()) table = null;

        facet = lastFacet.get(category);
        if (table != null && facet != null && !table.facets().contains(facet)) facet = null;

        if (table != null && (sortCol < 0 || sortCol >= table.columns.size())) {
            sortCol = table.sortFor(facet);
            sortDesc = table.columns.get(sortCol).descDefault();
        }
        buildChips();
        layoutColumns();
        applyFilter();
    }

    private void buildChips() {
        for (Button b : chipButtons) this.removeWidget(b);
        chipButtons.clear();
        chipByFacet.clear();
        if (table == null || table.facets().isEmpty()) return;

        List<String> labels = new ArrayList<>();
        labels.add(null);
        labels.addAll(table.facets());
        int total = 0;
        int[] widths = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            widths[i] = this.font.width(chipText(labels.get(i))) + 12;
            total += widths[i] + 3;
        }
        int x = (this.width - total) / 2;
        for (int i = 0; i < labels.size(); i++) {
            String f = labels.get(i);
            Button b = Button.builder(chipText(f), btn -> selectFacet(f))
                    .bounds(x, CHIPS_Y, widths[i], CHIP_H).build();
            chipButtons.add(b);
            chipByFacet.put(f == null ? "" : f, b);
            this.addRenderableWidget(b);
            x += widths[i] + 3;
        }
        updateChipStates();
    }

    private Component chipText(String f) {
        if (f == null) return Component.translatable("bestinpack.chip.all");
        String key = "bestinpack.chip." + f.toLowerCase(Locale.ROOT).replace(' ', '_');
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(f);
    }

    private void updateChipStates() {
        for (Map.Entry<String, Button> e : chipByFacet.entrySet()) {
            boolean selected = e.getKey().isEmpty() ? facet == null : e.getKey().equals(facet);
            e.getValue().active = !selected;
        }
    }

    private void selectFacet(String f) {
        if ((f == null && facet == null) || (f != null && f.equals(facet))) return;
        facet = f;
        lastFacet.put(category, f);
        // A chip usually has a more natural ranking than the tab default (Score for pickaxes, DPS for weapons).
        sortCol = table.sortFor(f);
        sortDesc = table.columns.get(sortCol).descDefault();
        scroll = 0;
        hScroll = 0;
        updateChipStates();
        layoutColumns();
        applyFilter();
    }

    private void collectHave(Minecraft client) {
        Set<Item> set = new HashSet<>();
        Player player = client.player;
        if (player != null) {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty()) set.add(s.getItem());
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack s = player.getItemBySlot(slot);
                if (!s.isEmpty()) set.add(s.getItem());
            }
        }
        haveItems = set;
    }

    /** Key of the rank view the "#" column shows: the chip when one is selected, otherwise the whole tab. */
    private String rankView() {
        return facet == null ? "" : facet;
    }

    /** Which stat columns are on screen right now: all of them with Details, otherwise the tab's essentials. */
    private boolean isShown(Column c) {
        if (c.kind() == Column.Kind.RANK || c.kind() == Column.Kind.ITEM || c.kind() == Column.Kind.MOD) return true;
        if (table.hidesFacetColumn(facet) && c.key().equals(table.facetColumn())) return false;
        if (table.isHidden(facet, c.key())) return false;
        return details || table.essential(facet).contains(c.key());
    }

    private void layoutColumns() {
        boolean chips = table != null && !table.facets().isEmpty();
        headerY = chips ? CHIPS_Y + CHIP_H + 8 : TOOLS_Y + 26;
        listTop = headerY + HEADER_H + 2;
        listBottom = this.height - BOTTOM_PAD;
        if (table == null) {
            frozenCols = List.of();
            scrollCols = List.of();
            return;
        }
        int available = this.width - 16 - SCROLLBAR_W;
        List<Column> frozen = new ArrayList<>();
        List<Column> scrolling = new ArrayList<>();
        for (Column c : table.columns) {
            if (!isShown(c)) continue;
            if (c.kind() == Column.Kind.RANK || c.kind() == Column.Kind.ITEM) frozen.add(c);
            else scrolling.add(c);
        }
        // Sorting by a column that is now hidden would be invisible – fall back to the natural one.
        if (!isShown(table.columns.get(sortCol))) {
            sortCol = table.sortFor(facet);
            sortDesc = table.columns.get(sortCol).descDefault();
        }

        int rankW = 0;
        for (Column c : frozen) if (c.kind() == Column.Kind.RANK) rankW += c.width();

        // The mod column grows to fit the longest mod name in the table (capped), so names are not chopped.
        int modW = 52;
        for (Row r : table.rows) modW = Math.max(modW, this.font.width(r.modName) + 6);
        modW = Math.min(modW, 96);

        // Every header must fit its label plus the sort arrow, so the arrow never gets clipped.
        int arrowW = this.font.width(ARROW_DOWN) + 1;
        int[] widths = new int[scrolling.size()];
        for (int i = 0; i < scrolling.size(); i++) {
            Column c = scrolling.get(i);
            int base = c.kind() == Column.Kind.MOD ? modW : c.width();
            int labelW = this.font.width(c.label().getString()) + HEADER_PAD + (c.sortable() ? arrowW : 0);
            widths[i] = Math.max(base, labelW);
            if (c.kind() == Column.Kind.NUMBER) {
                int col = table.columns.indexOf(c);
                int valueW = 0;
                for (Row r : table.rows) {
                    if (r.hasValue(col)) valueW = Math.max(valueW, this.font.width(ItemIndex.fmtCompact(r.value(col), c.decimals())));
                }
                widths[i] = Math.max(widths[i], valueW + 8);
            }
        }
        scrollTotalW = 0;
        for (int w : widths) scrollTotalW += w;

        // The item name has priority: if it would get less than ITEM_MIN_W, shrink the text columns
        // (Effects, Notes, Bonuses) down to 70 px first; what still does not fit scrolls sideways.
        int deficit = ITEM_MIN_W - (available - rankW - scrollTotalW);
        for (int i = scrolling.size() - 1; i >= 0 && deficit > 0; i--) {
            if (scrolling.get(i).kind() != Column.Kind.TEXT) continue;
            int cut = Math.min(deficit, widths[i] - 70);
            if (cut > 0) {
                widths[i] -= cut;
                scrollTotalW -= cut;
                deficit -= cut;
            }
        }
        int itemW = Mth.clamp(available - rankW - scrollTotalW, ITEM_MIN_W, ITEM_MAX_W);
        frozenTotalW = rankW + itemW;
        viewportW = Math.max(40, Math.min(scrollTotalW, available - frozenTotalW));
        tableW = frozenTotalW + viewportW;
        tableX = (this.width - tableW - SCROLLBAR_W) / 2;

        frozenCols = frozen;
        frozenX = new int[frozen.size()];
        frozenW = new int[frozen.size()];
        int x = tableX;
        for (int i = 0; i < frozen.size(); i++) {
            Column c = frozen.get(i);
            int w = c.kind() == Column.Kind.ITEM ? itemW : c.width();
            frozenX[i] = x;
            frozenW[i] = w;
            x += w;
        }
        scrollCols = scrolling;
        scrollX = new int[scrolling.size()];
        scrollW = new int[scrolling.size()];
        int sx = 0;
        for (int i = 0; i < scrolling.size(); i++) {
            scrollX[i] = sx;
            scrollW[i] = widths[i];
            sx += scrollW[i];
        }
        clampScroll();
    }

    private int viewportX() {
        return tableX + frozenTotalW;
    }

    private boolean needsHScroll() {
        return scrollTotalW > viewportW;
    }

    // ------------------------------------------------------------------ filtering & sorting

    private void applyFilter() {
        visible.clear();
        if (table == null) return;
        collectHave(Minecraft.getInstance()); // cheap, and keeps "Have" and the green names current

        String raw = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<String> terms = new ArrayList<>();
        List<String> mods = new ArrayList<>();
        List<String> notTerms = new ArrayList<>();
        List<String> notMods = new ArrayList<>();
        for (String tok : raw.split("\\s+")) {
            if (tok.isEmpty()) continue;
            boolean not = tok.startsWith("-");
            if (not) tok = tok.substring(1);
            if (tok.startsWith("@")) {
                if (tok.length() > 1) (not ? notMods : mods).add(tok.substring(1));
            } else if (!tok.isEmpty()) {
                (not ? notTerms : terms).add(tok);
            }
        }

        List<Row> rows = new ArrayList<>();
        boolean masking = com.bestinpack.client.Discovery.active();
        boolean searching = !terms.isEmpty() || !mods.isEmpty() || !notTerms.isEmpty() || !notMods.isEmpty();
        knownCount = 0;
        for (Row r : table.rows) {
            if (haveOnly && !haveItems.contains(r.item)) continue;
            if (facet != null && !r.facets.contains(facet)) continue;
            boolean known = !masking || com.bestinpack.client.Discovery.isKnown(r.item);
            if (!known && searching) continue; // a name search must not reveal undiscovered items
            boolean ok = true;
            for (String m : mods) {
                if (!(r.modId.contains(m) || r.modName.toLowerCase(Locale.ROOT).contains(m))) { ok = false; break; }
            }
            if (!ok) continue;
            for (String t : terms) {
                if (!(r.nameLower.contains(t) || r.id.contains(t))) { ok = false; break; }
            }
            if (!ok) continue;
            for (String m : notMods) {
                if (r.modId.contains(m) || r.modName.toLowerCase(Locale.ROOT).contains(m)) { ok = false; break; }
            }
            if (!ok) continue;
            for (String t : notTerms) {
                if (r.nameLower.contains(t) || r.id.contains(t)) { ok = false; break; }
            }
            if (ok) {
                rows.add(r);
                if (known) knownCount++;
            }
        }

        int col = sortCol >= 0 && sortCol < table.columns.size() ? sortCol : table.defaultSort;
        rows.sort(table.tableComparator(col, sortDesc));

        // Identical stats sort next to each other (the comparator covers every column), so one pass collapses them.
        Group current = null;
        for (Row r : rows) {
            if (config.collapseIdentical && current != null && current.head.sameStats(r)) {
                current.others.add(r);
            } else {
                current = new Group(r, new ArrayList<>());
                visible.add(current);
            }
        }
        if (masking) {
            List<Group> split = new ArrayList<>();
            for (Group g : visible) {
                List<Row> all = new ArrayList<>();
                all.add(g.head);
                all.addAll(g.others);
                List<Row> known = new ArrayList<>(), unknown = new ArrayList<>();
                for (Row r : all) (com.bestinpack.client.Discovery.isKnown(r.item) ? known : unknown).add(r);
                if (!known.isEmpty()) split.add(new Group(known.get(0), new ArrayList<>(known.subList(1, known.size()))));
                if (!unknown.isEmpty()) split.add(new Group(unknown.get(0), new ArrayList<>(unknown.subList(1, unknown.size()))));
            }
            Column.Kind k = table.columns.get(col).kind();
            if (k == Column.Kind.ITEM || k == Column.Kind.MOD) {
                // sorting by name would hint at what the "???" rows are – keep them at the end instead
                List<Group> known = new ArrayList<>(), unknown = new ArrayList<>();
                for (Group g : split) (isKnown(g) ? known : unknown).add(g);
                split = known;
                split.addAll(unknown);
            }
            visible.clear();
            visible.addAll(split);
        }
        // The row shows the most familiar member of a group, not the alphabetically first one: something you
        // carry wins, otherwise the one the game registers first (vanilla before mods, Coal before Charcoal).
        for (int i = 0; i < visible.size(); i++) {
            Group g = visible.get(i);
            if (g.others.isEmpty()) continue;
            Row best = g.head;
            for (Row o : g.others) if (preferAsHead(o, best)) best = o;
            if (best != g.head) {
                List<Row> others = new ArrayList<>(g.others);
                others.remove(best);
                others.add(g.head);
                others.sort((a, b) -> Integer.compare(a.rawId, b.rawId));
                visible.set(i, new Group(best, others));
            }
        }
        discoveryVersion = com.bestinpack.client.Discovery.version();
        clampScroll();
    }

    /** False for a "???" row: the onlyDiscovered setting is on and the player never had this item. */
    private static boolean isKnown(Group g) {
        return com.bestinpack.client.Discovery.isKnown(g.head.item);
    }

    private boolean preferAsHead(Row candidate, Row current) {
        boolean cHave = haveItems.contains(candidate.item), hHave = haveItems.contains(current.item);
        if (cHave != hHave) return cHave;
        return candidate.rawId < current.rawId;
    }

    private void toggleSort(int tableColIndex) {
        if (table == null) return;
        Column c = table.columns.get(tableColIndex);
        if (!c.sortable()) return;
        if (sortCol == tableColIndex) {
            sortDesc = !sortDesc;
        } else {
            sortCol = tableColIndex;
            sortDesc = c.descDefault();
        }
        applyFilter();
    }

    private int listEnd() {
        return needsHScroll() ? listBottom - HSCROLL_H - 2 : listBottom;
    }

    private int visibleRows() {
        return Math.max(1, (listEnd() - listTop) / rowH);
    }

    private double maxScroll() {
        return Math.max(0, (visible.size() - visibleRows()) * (double) rowH);
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
        hScroll = Mth.clamp(hScroll, 0, Math.max(0, scrollTotalW - viewportW));
    }

    private void setStatus(Component text) {
        setStatus(text, 4000);
    }

    private void setStatus(Component text, long millis) {
        statusOverride = text;
        statusOverrideUntil = System.currentTimeMillis() + millis;
    }

    // ------------------------------------------------------------------ scrollbar geometry

    private boolean hasVScroll() {
        return visible.size() > visibleRows();
    }

    private int panelLeft() {
        return tableX - 4;
    }

    private int panelRight() {
        return tableX + tableW + 4;
    }

    /** [x, y, w, h] of the vertical thumb, or null when nothing scrolls. The track sits just outside the panel. */
    private int[] vThumb() {
        if (!hasVScroll()) return null;
        int trackX = panelRight();
        int trackH = listEnd() - listTop;
        int thumbH = Math.max(12, (int) ((double) visibleRows() / visible.size() * trackH));
        double max = maxScroll();
        int thumbY = listTop + (int) ((trackH - thumbH) * (max > 0 ? scroll / max : 0));
        return new int[]{trackX, thumbY, SCROLLBAR_W, thumbH};
    }

    /** [x, y, w, h] of the horizontal thumb, or null when nothing scrolls. */
    private int[] hThumb() {
        if (!needsHScroll()) return null;
        int vx = viewportX();
        int trackY = listBottom - HSCROLL_H;
        int thumbW = Math.max(16, (int) ((double) viewportW / scrollTotalW * viewportW));
        double max = scrollTotalW - viewportW;
        int thumbX = vx + (int) ((viewportW - thumbW) * (max > 0 ? hScroll / max : 0));
        return new int[]{thumbX, trackY, thumbW, HSCROLL_H};
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void scrollToThumbY(double thumbTop) {
        int trackH = listEnd() - listTop;
        int thumbH = Math.max(12, (int) ((double) visibleRows() / visible.size() * trackH));
        double range = trackH - thumbH;
        double frac = range > 0 ? (thumbTop - listTop) / range : 0;
        scroll = Mth.clamp(frac, 0, 1) * maxScroll();
    }

    private void scrollToThumbX(double thumbLeft) {
        int vx = viewportX();
        int thumbW = Math.max(16, (int) ((double) viewportW / scrollTotalW * viewportW));
        double range = viewportW - thumbW;
        double frac = range > 0 ? (thumbLeft - vx) / range : 0;
        hScroll = Mth.clamp(frac, 0, 1) * (scrollTotalW - viewportW);
    }

    // ------------------------------------------------------------------ rendering

    /** Background of the table, per style: glass, bevelled vanilla window, textured list, flat panel or parchment. */
    private void drawPanel(GuiGraphics context, int left, int top, int right, int bottom, int headerBottom) {
        switch (theme.style()) {
            case VANILLA -> {
                // Inventory-style window: dark outline, light grey body, bright top-left and dark bottom-right bevel.
                context.fill(left - 3, top - 3, right + 3, bottom + 3, 0xFF000000);
                context.fill(left - 2, top - 2, right + 2, bottom + 2, theme.panel());
                context.fill(left - 2, top - 2, right + 1, top - 1, 0xFFFFFFFF);      // top highlight
                context.fill(left - 2, top - 2, left - 1, bottom + 1, 0xFFFFFFFF);    // left highlight
                context.fill(left - 1, bottom + 1, right + 2, bottom + 2, 0xFF555555); // bottom shadow
                context.fill(right + 1, top - 1, right + 2, bottom + 2, 0xFF555555);   // right shadow
                // header strip sunk like a slot row
                context.fill(left, top, right, headerBottom, theme.headerBg());
                context.fill(left, top, right, top + 1, 0xFF373737);
                context.fill(left, headerBottom - 1, right, headerBottom, 0xFFFFFFFF);
            }
            case LIST -> {
                // Options-screen list: tiled texture with the vanilla header/footer separators.
                context.enableScissor(left, top, right, bottom);
                for (int ty = top; ty < bottom; ty += 32) {
                    for (int tx = left; tx < right; tx += 32) {
                        Gfx.blit(context, LIST_BACKGROUND, tx, ty, 0f, 0f, 32, 32, 32, 32);
                    }
                }
                context.disableScissor();
                Gfx.blit(context, Screen.INWORLD_HEADER_SEPARATOR, left, top - 2, 0f, 0f, right - left, 2, 32, 2);
                Gfx.blit(context, Screen.INWORLD_FOOTER_SEPARATOR, left, bottom, 0f, 0f, right - left, 2, 32, 2);
                context.fill(left, headerBottom, right, headerBottom + 1, theme.separator());
            }
            case COMPACT, BOOK -> {
                context.fill(left - 1, top - 1, right + 1, bottom + 1, theme.panelBorder());
                context.fill(left, top, right, bottom, theme.panel());
                context.fill(left, top, right, headerBottom, theme.headerBg());
                context.fill(left, headerBottom, right, headerBottom + 1, theme.panelBorder());
                if (theme.style() == Config.Style.BOOK) {
                    Gfx.outline(context, left + 2, top + 2, right - left - 4, bottom - top - 4, 0x60B89B6A);
                }
            }
            default -> {
                context.fill(left, top, right, bottom, theme.panel());
                context.fill(left, top, right, headerBottom, theme.headerBg());
            }
        }
    }

    /** Draws a string with or without shadow, as the style wants. */
    private void text(GuiGraphics context, String s, int x, int y, int color) {
        context.drawString(this.font, s, x, y, color, theme.shadow());
    }

    private void text(GuiGraphics context, Component t, int x, int y, int color) {
        context.drawString(this.font, t, x, y, color, theme.shadow());
    }

    /** Trims text to the width and marks the cut with an ellipsis. */
    private String clip(String s, int width) {
        if (width <= 0) return "";
        if (this.font.width(s) <= width) return s;
        int w = Math.max(0, width - this.font.width(ELLIPSIS));
        return this.font.plainSubstrByWidth(s, w) + ELLIPSIS;
    }

    @Override
    public void tick() {
        super.tick();
        if (com.bestinpack.client.Discovery.active() && discoveryVersion != com.bestinpack.client.Discovery.version()) {
            applyFilter();
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);

        if (table == null) {
            context.drawCenteredString(this.font, Component.translatable(Minecraft.getInstance().level != null
                            ? "bestinpack.status.nofuel" : "bestinpack.status.noworld"),
                    this.width / 2, this.height / 2, 0xFFAAAAAA);
            return;
        }

        int panelLeft = panelLeft(), panelRight = panelRight();
        int listEnd = listEnd();
        int vx = viewportX();

        drawPanel(context, panelLeft, headerY - 2, panelRight, listBottom + 2, headerY + HEADER_H);

        List<FormattedCharSequence> pendingTooltip = null;
        ItemStack pendingItemTooltip = null;

        for (int i = 0; i < frozenCols.size(); i++) {
            drawHeader(context, frozenCols.get(i), frozenX[i], frozenW[i], mouseX, mouseY, panelLeft, panelRight);
        }
        context.enableScissor(vx, headerY - 2, vx + viewportW, headerY + HEADER_H);
        for (int i = 0; i < scrollCols.size(); i++) {
            int x = vx + scrollX[i] - (int) hScroll;
            if (x + scrollW[i] < vx || x > vx + viewportW) continue;
            drawHeader(context, scrollCols.get(i), x, scrollW[i], mouseX, mouseY, vx, vx + viewportW);
        }
        context.disableScissor();
        if (mouseY >= headerY - 2 && mouseY < headerY + HEADER_H) {
            Column hc = headerAt(mouseX);
            if (hc != null) {
                String tipKey = "bestinpack.col." + hc.key() + ".tooltip";
                if (net.minecraft.locale.Language.getInstance().has(tipKey)) {
                    pendingTooltip = wrap(Component.literal(I18n.get(tipKey)));
                }
            }
        }

        // Rows
        int first = (int) (scroll / rowH);
        int yOff = (int) (scroll % rowH);
        int rowsOnScreen = (listEnd - listTop) / rowH + 2;
        Group hoveredGroup = null;
        boolean mouseInList = !draggingV && !draggingH
                && mouseX >= panelLeft && mouseX < panelRight && mouseY >= listTop && mouseY < listEnd;

        context.enableScissor(panelLeft, listTop, panelRight, listEnd);
        for (int n = 0; n < rowsOnScreen; n++) {
            int idx = first + n;
            if (idx >= visible.size()) break;
            Group g = visible.get(idx);
            int y = listTop + n * rowH - yOff;
            if (theme.zebra() && idx % 2 == 1) context.fill(panelLeft, y, panelRight, y + rowH, theme.rowAlt());
            if (theme.gridLines()) context.fill(panelLeft + 2, y + rowH - 1, panelRight - 2, y + rowH, theme.gridLine());
            if (mouseInList && mouseY >= y && mouseY < y + rowH) {
                if (theme.hoverOutline()) Gfx.outline(context, panelLeft + 1, y, panelRight - panelLeft - 2, rowH, theme.hover());
                else context.fill(panelLeft, y, panelRight, y + rowH, theme.hover());
                hoveredGroup = g;
            }
            if (highlightItem != null && System.currentTimeMillis() < highlightUntil && holds(g, highlightItem)) {
                context.fill(panelLeft, y, panelRight, y + rowH, (theme.sorted() & 0x00FFFFFF) | 0x30000000);
                Gfx.outline(context, panelLeft, y, panelRight - panelLeft, rowH, theme.sorted());
            }
            renderFrozenCells(context, g, idx + 1, y);
        }
        context.disableScissor();

        context.enableScissor(vx, listTop, vx + viewportW, listEnd);
        for (int n = 0; n < rowsOnScreen; n++) {
            int idx = first + n;
            if (idx >= visible.size()) break;
            int y = listTop + n * rowH - yOff;
            renderScrollCells(context, visible.get(idx), y);
        }
        context.disableScissor();

        if (visible.isEmpty()) {
            Component empty = Component.translatable("bestinpack.status.empty");
            text(context, empty, (panelLeft + panelRight) / 2 - this.font.width(empty) / 2, (listTop + listEnd) / 2 - 4, theme.muted());
        }

        if (needsHScroll()) {
            context.fill(vx - 1, headerY - 2, vx, listEnd, theme.separator());
        }

        // Scrollbars (draggable)
        int[] vt = vThumb();
        if (vt != null) {
            context.fill(vt[0], listTop, vt[0] + SCROLLBAR_W, listEnd, theme.track());
            boolean hot = draggingV || inside(mouseX, mouseY, vt[0], vt[1], vt[2], vt[3]);
            context.fill(vt[0] + 1, vt[1], vt[0] + SCROLLBAR_W - 1, vt[1] + vt[3], hot ? theme.thumbActive() : theme.thumb());
        }
        int[] ht = hThumb();
        if (ht != null) {
            context.fill(vx, ht[1], vx + viewportW, ht[1] + HSCROLL_H, theme.track());
            boolean hot = draggingH || inside(mouseX, mouseY, ht[0], ht[1], ht[2], ht[3]);
            context.fill(ht[0], ht[1] + 1, ht[0] + ht[2], ht[1] + HSCROLL_H - 1, hot ? theme.thumbActive() : theme.thumb());
        }

        // Status line: just the number of items that match (grouped rows still count every item)
        int totalItems = 0;
        for (Group g : visible) totalItems += g.size();
        Component status;
        if (statusOverride != null && System.currentTimeMillis() < statusOverrideUntil) {
            status = statusOverride;
        } else if (config.onlyDiscovered && !com.bestinpack.client.Discovery.active()) {
            status = Component.translatable("bestinpack.status.creative", totalItems);
        } else if (com.bestinpack.client.Discovery.active()) {
            status = Component.translatable("bestinpack.status.count.discovered", totalItems, knownCount);
        } else if (totalItems == 1) {
            status = Component.translatable("bestinpack.status.count.one");
        } else {
            status = Component.translatable("bestinpack.status.count", totalItems);
        }
        context.drawString(this.font, status, panelLeft, listBottom + 5, 0xFFAAAAAA, true);

        // Row tooltips: item tooltip over the name, member list over a "+N", full text over a clipped cell.
        if (hoveredGroup != null && pendingTooltip == null) {
            Column cell = cellAt(mouseX);
            if (!isKnown(hoveredGroup)) {
                if (cell == null || cell.kind() != Column.Kind.NUMBER) {
                    pendingTooltip = ordered(List.of(Component.translatable(hoveredGroup.others.isEmpty()
                            ? "bestinpack.tooltip.undiscovered" : "bestinpack.tooltip.undiscovered.many", hoveredGroup.size())));
                }
            } else if (cell == null || cell.kind() == Column.Kind.ITEM || cell.kind() == Column.Kind.RANK) {
                if (hoveredGroup.others.isEmpty()) {
                    pendingItemTooltip = hoveredGroup.head.stack;
                } else {
                    pendingTooltip = ordered(groupTooltip(hoveredGroup));
                }
            } else if (cell.kind() == Column.Kind.NUMBER) {
                int tableCol = table.columns.indexOf(cell);
                if (hoveredGroup.head.hasValue(tableCol)) {
                    double v = hoveredGroup.head.value(tableCol);
                    if (!ItemIndex.fmtCompact(v, cell.decimals()).equals(ItemIndex.fmt(v, cell.decimals()))) {
                        pendingTooltip = ordered(List.of(cell.label().copy().withStyle(ChatFormatting.GRAY),
                                Component.literal(ItemIndex.fmtGrouped(v, cell.decimals()))));
                    }
                }
            } else if (cell.kind() == Column.Kind.TEXT || cell.kind() == Column.Kind.MOD) {
                int tableCol = table.columns.indexOf(cell);
                String full = cell.kind() == Column.Kind.MOD ? hoveredGroup.head.modName : hoveredGroup.head.text(tableCol);
                boolean truncated = this.font.width(full) > cellWidth(cell) - 4;
                if (full.contains("⚡")) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.translatable("bestinpack.tooltip.custom"));
                    if (truncated) for (String part : full.split(" · ")) if (!part.equals("⚡")) lines.add(Component.literal(part));
                    pendingTooltip = ordered(lines);
                } else if (!full.isEmpty() && truncated) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(cell.label().copy().withStyle(ChatFormatting.GRAY));
                    for (String part : full.split(" · ")) lines.add(Component.literal(part));
                    pendingTooltip = ordered(lines);
                }
            }
        }
        if (pendingItemTooltip != null) {
            Gfx.itemTooltip(context, this.font, pendingItemTooltip, mouseX, mouseY);
        } else if (pendingTooltip != null) {
            Gfx.textTooltip(context, this.font, pendingTooltip, mouseX, mouseY);
        }
    }

    /** Wraps a (possibly multi-line) text to a width that fits the window. */
    private List<FormattedCharSequence> wrap(Component text) {
        int width = Mth.clamp(this.width * 2 / 3, 150, 320);
        return this.font.split(text, width);
    }

    private List<FormattedCharSequence> ordered(List<Component> lines) {
        List<FormattedCharSequence> out = new ArrayList<>(lines.size());
        for (Component t : lines) out.addAll(wrap(t));
        return out;
    }

    private static boolean holds(Group g, Item item) {
        if (g.head.item == item) return true;
        for (Row o : g.others) if (o.item == item) return true;
        return false;
    }

    private List<Component> groupTooltip(Group g) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("bestinpack.group.tooltip.title", g.size()).withStyle(ChatFormatting.GRAY));
        lines.add(g.head.stack.getHoverName());
        int shown = 0;
        for (Row r : g.others) {
            if (shown++ >= 14) {
                lines.add(Component.translatable("bestinpack.group.tooltip.more", g.others.size() - 14).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            lines.add(r.stack.getHoverName());
        }
        return lines;
    }

    private Column headerAt(double mx) {
        for (int i = 0; i < frozenCols.size(); i++) {
            if (mx >= frozenX[i] && mx < frozenX[i] + frozenW[i]) return frozenCols.get(i);
        }
        int vx = viewportX();
        if (mx >= vx && mx < vx + viewportW) {
            double rel = mx - vx + hScroll;
            for (int i = 0; i < scrollCols.size(); i++) {
                if (rel >= scrollX[i] && rel < scrollX[i] + scrollW[i]) return scrollCols.get(i);
            }
        }
        return null;
    }

    private Column cellAt(double mx) {
        return headerAt(mx);
    }

    private int cellWidth(Column c) {
        for (int i = 0; i < frozenCols.size(); i++) if (frozenCols.get(i) == c) return frozenW[i];
        for (int i = 0; i < scrollCols.size(); i++) if (scrollCols.get(i) == c) return scrollW[i];
        return c.width();
    }

    private void drawHeader(GuiGraphics context, Column c, int x, int w, int mouseX, int mouseY, int clipLeft, int clipRight) {
        int tableCol = table.columns.indexOf(c);
        boolean sorted = tableCol == sortCol;
        int color = sorted ? theme.sorted() : theme.header();
        int bandTop = headerY - 2, bandBottom = headerY + HEADER_H;
        boolean hover = mouseX >= Math.max(x, clipLeft) && mouseX < Math.min(x + w, clipRight)
                && mouseY >= bandTop && mouseY < bandBottom;
        if (hover && c.sortable()) {
            // one pixel inside the column on each side, so neighbouring highlights never touch
            if (theme.hoverOutline()) Gfx.outline(context, x + 1, bandTop + 1, w - 2, bandBottom - bandTop - 2, theme.hover());
            else context.fill(x + 1, bandTop + 1, x + w - 1, bandBottom - 1, theme.hover());
        }

        // Number columns get a centred label (the arrow is part of it), text columns a label that starts where
        // their text starts – so headers line up with what is under them and the hover box frames the label.
        String arrow = sorted ? (sortDesc ? ARROW_DOWN : ARROW_UP) : "";
        int arrowW = sorted ? this.font.width(arrow) + 1 : 0;
        String label = clip(c.label().getString(), w - HEADER_PAD - arrowW);
        int labelW = this.font.width(label);
        boolean center = c.kind() == Column.Kind.NUMBER || c.kind() == Column.Kind.RANK;
        int tx = center ? x + (w - labelW - arrowW) / 2 : x + HEADER_PAD / 2 - 1;
        int ty = bandTop + (bandBottom - bandTop - this.font.lineHeight) / 2 + 1;
        text(context, label, tx, ty, color);
        if (sorted) text(context, arrow, tx + labelW + 1, ty, color);
    }

    /** Medals only make sense when the list is "best first": a stat column, descending. */
    private boolean medalsShown() {
        if (table == null || sortCol < 0 || !sortDesc) return false;
        Column.Kind k = table.columns.get(sortCol).kind();
        return k == Column.Kind.NUMBER || k == Column.Kind.TEXT;
    }

    /** {@code position} is the row's place in the list as it is shown right now (1 = top row). */
    private void renderFrozenCells(GuiGraphics context, Group g, int position, int y) {
        Row r = g.head;
        int textY = y + (rowH - this.font.lineHeight) / 2 + 1;
        boolean have = haveItems.contains(r.item);
        if (!have) for (Row o : g.others) if (haveItems.contains(o.item)) { have = true; break; }
        boolean medals = medalsShown();
        for (int i = 0; i < frozenCols.size(); i++) {
            Column c = frozenCols.get(i);
            int x = frozenX[i], w = frozenW[i];
            if (c.kind() == Column.Kind.RANK) {
                int color = !medals ? theme.muted()
                        : position == 1 ? theme.gold() : position == 2 ? theme.silver() : position == 3 ? theme.bronze() : theme.muted();
                String s = "#" + position;
                text(context, s, x + w - 3 - this.font.width(s), textY, color);
            } else if (!isKnown(g)) {
                // undiscovered: no icon, no name
                context.fill(x + 3, y + 2, x + 17, y + 16, 0x60000000);
                Gfx.outline(context, x + 3, y + 2, 14, 14, theme.dim());
                text(context, "?", x + 10 - this.font.width("?") / 2, textY, theme.dim());
                int nameRight = x + w - 2;
                if (!g.others.isEmpty()) {
                    String count = "+" + g.others.size();
                    int cw = this.font.width(count);
                    text(context, count, nameRight - cw, textY, theme.dim());
                }
                text(context, "???", x + 22, textY, theme.dim());
            } else {
                context.renderItem(r.stack, x + 2, y + 1);
                int nameRight = x + w - 2;
                if (!g.others.isEmpty()) {
                    String count = "+" + g.others.size();
                    int cw = this.font.width(count);
                    text(context, count, nameRight - cw, textY, theme.count());
                    nameRight -= cw + 4;
                }
                String s = clip(r.name, nameRight - (x + 22));
                text(context, s, x + 22, textY, have ? theme.have() : theme.text());
            }
        }
    }

    private void renderScrollCells(GuiGraphics context, Group g, int y) {
        Row r = g.head;
        int textY = y + (rowH - this.font.lineHeight) / 2 + 1;
        int vx = viewportX();
        for (int i = 0; i < scrollCols.size(); i++) {
            Column c = scrollCols.get(i);
            int x = vx + scrollX[i] - (int) hScroll, w = scrollW[i];
            if (x + w < vx || x > vx + viewportW) continue;
            int tableCol = table.columns.indexOf(c);
            switch (c.kind()) {
                case MOD -> {
                    if (!isKnown(g)) {
                        text(context, "???", x + 2, textY, theme.dim());
                        continue;
                    }
                    String mod = r.modName;
                    if (!g.others.isEmpty()) {
                        for (Row o : g.others) if (!o.modId.equals(r.modId)) { mod = I18n.get("bestinpack.group.various"); break; }
                    }
                    text(context, clip(mod, w - 4), x + 2, textY, theme.dim());
                }
                case NUMBER -> {
                    if (!r.hasValue(tableCol)) {
                        text(context, "–", x + w - 3 - this.font.width("–"), textY, theme.dim());
                    } else {
                        String s = ItemIndex.fmtCompact(r.value(tableCol), c.decimals());
                        text(context, s, x + w - 3 - this.font.width(s), textY, theme.text());
                    }
                }
                case TEXT -> {
                    String s = r.text(tableCol);
                    if (!isKnown(g) && c.key().equals("notes") && !s.isEmpty()) s = "???"; // mod text names the item
                    if (s.isEmpty()) {
                        text(context, "–", x + 2, textY, theme.dim());
                    } else {
                        text(context, clip(s, w - 4), x + 2, textY, theme.muted());
                    }
                }
                default -> {}
            }
        }
    }

    // ------------------------------------------------------------------ input

    //#if MC >= 1.21.9
    //$$ @Override
    //$$ public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
    //$$     if (super.mouseClicked(event, doubleClick)) {
    //$$         if (!(getFocused() instanceof EditBox)) setFocused(null);
    //$$         return true;
    //$$     }
    //$$     return handleClick(event.x(), event.y(), event.button());
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
    //$$     return handleDrag(event.x(), event.y()) || super.mouseDragged(event, deltaX, deltaY);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
    //$$     return handleRelease() || super.mouseReleased(event);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
    //#if MC >= 26.3
    //$$     return handleKey(event.key(), event.keycode()) || super.keyPressed(event);
    //#else
    //$$     return handleKey(event.key(), event.scancode()) || super.keyPressed(event);
    //#endif
    //$$ }
    //$$
    //$$ /** Self-test: a click like the mouse would do it. */
    //$$ public boolean clickForTest(double x, double y, int button) {
    //$$     net.minecraft.client.input.MouseButtonEvent e = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(button, 0));
    //$$     boolean r = mouseClicked(e, false);
    //$$     mouseReleased(e);
    //$$     return r;
    //$$ }
    //$$
    //$$ public boolean keyForTest(int keyCode) {
    //$$     return keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, 0, 0));
    //$$ }
    //#else
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            // A clicked button keeps keyboard focus and stays drawn highlighted ("Details" looked switched on).
            // Only the search box should keep focus after a mouse click.
            if (!(getFocused() instanceof EditBox)) setFocused(null);
            return true;
        }
        return handleClick(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return handleDrag(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return handleRelease() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return handleKey(keyCode, scanCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Self-test: a click like the mouse would do it. */
    public boolean clickForTest(double x, double y, int button) {
        boolean r = mouseClicked(x, y, button);
        mouseReleased(x, y, button);
        return r;
    }

    public boolean keyForTest(int keyCode) {
        return keyPressed(keyCode, 0, 0);
    }
    //#endif

    private boolean handleClick(double mouseX, double mouseY, int button) {
        if (table == null) return false;
        double mx = mouseX, my = mouseY;
        int panelLeft = panelLeft(), panelRight = panelRight();
        boolean left = button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
        boolean rightBtn = button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;

        if (left) {
            // Vertical scrollbar: grab the thumb, or jump when clicking the track.
            int[] vt = vThumb();
            if (vt != null && inside(mx, my, vt[0], listTop, SCROLLBAR_W, listEnd() - listTop)) {
                searchField.setFocused(false);
                if (my >= vt[1] && my < vt[1] + vt[3]) {
                    dragGrab = my - vt[1];
                } else {
                    dragGrab = vt[3] / 2.0;
                    scrollToThumbY(my - dragGrab);
                }
                draggingV = true;
                return true;
            }
            int[] ht = hThumb();
            if (ht != null && inside(mx, my, viewportX(), ht[1], viewportW, HSCROLL_H)) {
                searchField.setFocused(false);
                if (mx >= ht[0] && mx < ht[0] + ht[2]) {
                    dragGrab = mx - ht[0];
                } else {
                    dragGrab = ht[2] / 2.0;
                    scrollToThumbX(mx - dragGrab);
                }
                draggingH = true;
                return true;
            }
            if (my >= headerY - 2 && my < headerY + HEADER_H) {
                Column c = headerAt(mx);
                if (c != null) toggleSort(table.columns.indexOf(c));
                return true;
            }
        }

        if ((left || rightBtn) && mx >= panelLeft && mx < panelRight && my >= listTop && my < listEnd()) {
            searchField.setFocused(false);
            highlightItem = null;
            int idx = (int) ((my - listTop + scroll) / rowH);
            if (idx >= 0 && idx < visible.size()) {
                if (isKnown(visible.get(idx))) onRowClicked(visible.get(idx).head, rightBtn);
                else setStatus(Component.translatable("bestinpack.tooltip.undiscovered"));
            }
            return true;
        }
        return false;
    }

    private boolean handleDrag(double mouseX, double mouseY) {
        if (draggingV) {
            scrollToThumbY(mouseY - dragGrab);
            return true;
        }
        if (draggingH) {
            scrollToThumbX(mouseX - dragGrab);
            return true;
        }
        return false;
    }

    private boolean handleRelease() {
        if (draggingV || draggingH) {
            draggingV = false;
            draggingH = false;
            return true;
        }
        return false;
    }

    private void onRowClicked(Row row, boolean usages) {
        if (!RecipeViewers.show(row.stack, usages)) {
            setStatus(Component.translatable(RecipeViewers.available() != null ? "bestinpack.status.viewerbusy" : "bestinpack.status.noviewer"));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (table != null && mouseX >= tableX - 8 && mouseX < tableX + tableW + 8 + SCROLLBAR_W && mouseY >= headerY && mouseY < listBottom) {
            Minecraft client = Minecraft.getInstance();
            boolean shift = com.bestinpack.client.Keys.shiftDown();
            if (horizontalAmount != 0) {
                hScroll += horizontalAmount * 30;
            } else if (shift && needsHScroll()) {
                hScroll -= verticalAmount * 30;
            } else {
                scroll -= verticalAmount * rowH * 3;
            }
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean handleKey(int keyCode, int scanCode) {
        // Scrolling keys first – otherwise Screen turns the arrows into focus changes on the buttons.
        if (!searchField.isFocused() && table != null && handleScrollKey(keyCode)) return true;
        // JEI/REI habit: R over a row shows its recipe, U its uses (the viewer's own keys, R/U without one).
        if (!searchField.isFocused() && table != null) {
            boolean recipe = RecipeViewers.isRecipeKey(keyCode, scanCode), uses = !recipe && RecipeViewers.isUsesKey(keyCode, scanCode);
            if (recipe || uses) {
                Group g = groupUnderMouse();
                if (g != null) {
                    if (isKnown(g)) onRowClicked(g.head, uses);
                    else setStatus(Component.translatable("bestinpack.tooltip.undiscovered"));
                    return true;
                }
            }
        }
        // The open key toggles: pressing it again (outside the search box) closes the screen.
        if (!searchField.isFocused() && com.bestinpack.client.BestInPackClient.openKey != null
                && com.bestinpack.client.Keys.matches(com.bestinpack.client.BestInPackClient.openKey, keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return false;
    }

    /** The row under the mouse right now, or null. */
    private Group groupUnderMouse() {
        if (this.minecraft == null) return null;
        double scale = this.minecraft.getWindow().getGuiScale();
        double mx = this.minecraft.mouseHandler.xpos() / scale, my = this.minecraft.mouseHandler.ypos() / scale;
        if (mx < panelLeft() || mx >= panelRight() || my < listTop || my >= listEnd()) return null;
        int idx = (int) ((my - listTop + scroll) / rowH);
        return idx >= 0 && idx < visible.size() ? visible.get(idx) : null;
    }

    private boolean handleScrollKey(int key) {
        switch (key) {
            case com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN -> scroll += visibleRows() * rowH;
            case com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP -> scroll -= visibleRows() * rowH;
            case com.mojang.blaze3d.platform.InputConstants.KEY_HOME -> scroll = 0;
            case com.mojang.blaze3d.platform.InputConstants.KEY_END -> scroll = Double.MAX_VALUE;
            case com.mojang.blaze3d.platform.InputConstants.KEY_DOWN -> scroll += rowH;
            case com.mojang.blaze3d.platform.InputConstants.KEY_UP -> scroll -= rowH;
            case com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT -> hScroll += 40;
            case com.mojang.blaze3d.platform.InputConstants.KEY_LEFT -> hScroll -= 40;
            default -> { return false; }
        }
        clampScroll();
        return true;
    }

    // ------------------------------------------------------------------ hooks for the headless self-test

    public void selectCategoryForTest(Category cat) {
        switchCategory(cat);
    }

    public void selectFacetForTest(String f) {
        selectFacet(f);
    }

    public void setDetailsForTest(boolean on) {
        details = on;
        detailsButton.setMessage(detailsLabel());
        layoutColumns();
        applyFilter();
    }

    public void setSearchForTest(String text) {
        searchField.setValue(text);
        search = text;
        applyFilter();
    }

    /** Back to the natural ordering of the current tab/chip. */
    public void resetSortForTest() {
        if (table == null) return;
        sortCol = table.sortFor(facet);
        sortDesc = table.columns.get(sortCol).descDefault();
        applyFilter();
    }

    public void toggleSortForTest(String columnKey) {
        if (table == null) return;
        int idx = table.indexOf(columnKey);
        if (idx >= 0) toggleSort(idx);
    }

    public void scrollSidewaysForTest(double amount) {
        hScroll += amount;
        clampScroll();
    }

    /** Simulates grabbing the vertical thumb and dragging it to the given fraction of the track. */
    public void dragScrollbarForTest(double fraction) {
        int[] vt = vThumb();
        if (vt == null) return;
        int trackH = listEnd() - listTop;
        draggingV = true;
        dragGrab = 0;
        scrollToThumbY(listTop + fraction * (trackH - vt[3]));
        draggingV = false;
    }

    public double scrollForTest() {
        return scroll;
    }

    public boolean openRecipesForTest() {
        if (visible.isEmpty()) return false;
        return RecipeViewers.show(visible.get(0).head.stack, false);
    }

    public int visibleRowCountForTest() {
        return visible.size();
    }

    /** "name+others #rank [effects]" for the first rows – used by the self-test log. */
    public List<String> firstNamesForTest(int n) {
        List<String> out = new ArrayList<>();
        int effects = table.indexOf("effects");
        for (int i = 0; i < Math.min(n, visible.size()); i++) {
            Group g = visible.get(i);
            out.add(g.head.name + (g.others.isEmpty() ? "" : "+" + g.others.size()) + " #" + (i + 1) + "/r" + g.head.rank(rankView())
                    + (effects >= 0 ? " [" + g.head.text(effects) + "]" : ""));
        }
        return out;
    }

    public int shownColumnCountForTest() {
        return frozenCols.size() + scrollCols.size();
    }

    /** GUI-space centre of a cell: row index in the visible list, column key ("name", "effects", …). */
    public double[] rowCenterForTest(int rowIdx, String columnKey) {
        if (table == null || rowIdx >= visible.size()) return null;
        double y = listTop + rowIdx * rowH - scroll + rowH / 2.0;
        for (int i = 0; i < frozenCols.size(); i++) {
            if (frozenCols.get(i).key().equals(columnKey)) return new double[]{frozenX[i] + 30, y};
        }
        for (int i = 0; i < scrollCols.size(); i++) {
            if (scrollCols.get(i).key().equals(columnKey)) return new double[]{viewportX() + scrollX[i] - hScroll + scrollW[i] / 2.0, y};
        }
        return null;
    }

    public double[] headerCenterForTest(String columnKey) {
        double[] p = rowCenterForTest(0, columnKey);
        return p == null ? null : new double[]{p[0], headerY + HEADER_H / 2.0};
    }

    /** Clicks the Details button like a mouse would; returns whether some widget still has focus afterwards. */
    public boolean clickDetailsForTest() {
        double[] c = widgetCenterForTest("details");
        clickForTest(c[0], c[1], com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        return getFocused() != null;
    }

    /** Presses a key with the mouse over the given visible row. */
    public boolean pressKeyOverRowForTest(int rowIdx, int keyCode) {
        double[] c = rowCenterForTest(rowIdx, "name");
        if (c == null || this.minecraft == null) return false;
        double scale = this.minecraft.getWindow().getGuiScale();
        com.bestinpack.client.Keys.setCursor(c[0] * scale, c[1] * scale);
        try {
            java.lang.reflect.Field fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true); fy.setAccessible(true);
            fx.setDouble(this.minecraft.mouseHandler, c[0] * scale); fy.setDouble(this.minecraft.mouseHandler, c[1] * scale);
        } catch (ReflectiveOperationException ignored) {}
        return keyForTest(keyCode);
    }

    public boolean detailsForTest() {
        return details;
    }

    public double[] widgetCenterForTest(String which) {
        Button b = switch (which) {
            case "have" -> haveButton;
            case "details" -> detailsButton;
            default -> helpButton;
        };
        return new double[]{b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0};
    }

    @Override
    public void onClose() {
        lastSort.put(category, new int[]{sortCol, sortDesc ? 1 : 0});
        if (this.minecraft != null) com.bestinpack.client.Screens.set(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
