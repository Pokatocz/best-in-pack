package com.bestinpack.index;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One item in a category table. values/texts are indexed by column position. */
public final class Row {
    public final ItemStack stack;
    public final Item item;
    public final String id;
    public final String modId;
    public final String modName;
    public final String name;
    public final String nameLower;
    /** Registry order – vanilla items first, "Coal" before "Charcoal", "Oak Boat" before "Acacia Boat". */
    public final int rawId;
    public final double[] values;
    public final String[] texts;
    /** True when the item class is not vanilla – its numbers may not tell the whole story. */
    public boolean custom;
    /** Primary filter-chip bucket (armor slot, tool type, …); null when the table has no facets. */
    public String facet;
    /** Every chip this row appears under (an axe is under "Axes" and "Weapons"). */
    public final Set<String> facets = new LinkedHashSet<>();
    /**
     * Fixed rank of this item per view: key "" = the whole tab ordered by its default column,
     * a facet name = that chip ordered by its natural column. Items with identical stats share a rank
     * (1, 2, 2, 4). Filled by {@link ItemIndex} after indexing.
     */
    public final Map<String, Integer> ranks = new HashMap<>(4);

    public Row(ItemStack stack, String id, String modId, String modName, String name, int columns) {
        this.stack = stack;
        this.item = stack.getItem();
        this.id = id;
        this.modId = modId;
        this.modName = modName;
        this.name = name;
        this.nameLower = name.toLowerCase(java.util.Locale.ROOT);
        this.rawId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(this.item);
        this.values = new double[columns];
        Arrays.fill(this.values, Double.NaN);
        this.texts = new String[columns];
    }

    public double value(int col) {
        return values[col];
    }

    public String text(int col) {
        return texts[col] == null ? "" : texts[col];
    }

    public boolean hasValue(int col) {
        return !Double.isNaN(values[col]);
    }

    public void setFacet(String primary, String... extra) {
        this.facet = primary;
        this.facets.clear();
        if (primary != null) this.facets.add(primary);
        for (String f : extra) if (f != null) this.facets.add(f);
    }

    /** Rank in the given view ("" = whole tab), or 0 when the row is not ranked there. */
    public int rank(String view) {
        Integer r = ranks.get(view == null ? "" : view);
        return r == null ? 0 : r;
    }

    /** True when every stat column (numbers and texts) matches – used to collapse duplicate rows. */
    public boolean sameStats(Row o) {
        for (int i = 0; i < values.length; i++) {
            double a = values[i], b = o.values[i];
            if (Double.isNaN(a) != Double.isNaN(b)) return false;
            if (!Double.isNaN(a) && a != b && Math.abs(a - b) > 1e-6) return false;
            if (!text(i).equals(o.text(i))) return false;
        }
        return true;
    }
}
