package com.bestinpack.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Table {
    public final Category category;
    public final List<Column> columns;
    public final List<Row> rows = new ArrayList<>();
    /** Column index used for the default ordering of the whole tab (and its fixed "#"). */
    public final int defaultSort;
    /** Filter chips shown above the table (values of {@link Row#facets}); empty = no chips. */
    private List<String> facets = List.of();
    private final Map<String, Integer> facetSorts = new HashMap<>();
    /** Columns shown without "Details": per facet, with "" as the fallback for no facet. */
    private final Map<String, Set<String>> essentials = new HashMap<>();
    /** Column that repeats the selected facet (armor slot, tool type) and is hidden while a facet is active. */
    private String facetColumn;
    /** Columns never shown under a facet, not even with Details ("" = no chip selected). */
    private final Map<String, Set<String>> hidden = new HashMap<>();
    /** Facets whose rows differ in the facet column, so it stays visible (e.g. "Weapons": Sword/Axe/Spear). */
    private final Set<String> keepFacetColumn = new HashSet<>();

    public Table(Category category, List<Column> columns, int defaultSort) {
        this.category = category;
        this.columns = columns;
        this.defaultSort = defaultSort;
    }

    public void facets(List<String> facets) {
        this.facets = facets;
    }

    public List<String> facets() {
        return facets;
    }

    /** Column that makes the most sense when this chip is selected (e.g. Score for pickaxes, DPS for weapons). */
    public void facetSort(String facet, int column) {
        facetSorts.put(facet, column);
    }

    public int sortFor(String facet) {
        Integer c = facet == null ? null : facetSorts.get(facet);
        return c != null ? c : defaultSort;
    }

    public void essential(String facet, String... keys) {
        essentials.put(facet == null ? "" : facet, new HashSet<>(Arrays.asList(keys)));
    }

    /** Keys of the columns that are shown in the compact view for this facet (null = no facet). */
    public Set<String> essential(String facet) {
        Set<String> set = facet == null ? null : essentials.get(facet);
        if (set == null) set = essentials.get("");
        return set == null ? Set.of() : set;
    }

    public void hide(String facet, String... keys) {
        hidden.put(facet == null ? "" : facet, new HashSet<>(Arrays.asList(keys)));
    }

    public boolean isHidden(String facet, String key) {
        Set<String> set = hidden.get(facet == null ? "" : facet);
        return set != null && set.contains(key);
    }

    public void facetColumn(String key) {
        this.facetColumn = key;
    }

    public void keepFacetColumn(String facet) {
        keepFacetColumn.add(facet);
    }

    /** True when the facet column should be hidden while this facet is selected. */
    public boolean hidesFacetColumn(String facet) {
        return facet != null && facetColumn != null && !keepFacetColumn.contains(facet);
    }

    public String facetColumn() {
        return facetColumn;
    }

    public int indexOf(String key) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).key().equals(key)) return i;
        }
        return -1;
    }

    /** Comparator for a column with a stable name tie-break; descending puts the "best" first. */
    public Comparator<Row> comparator(int col, boolean descending) {
        return columnComparator(col, descending).thenComparing(r -> r.nameLower);
    }

    /**
     * Comparator used by the on-screen table: the chosen column first, then every other numeric
     * column (best first, left to right), then the text columns, so that rows with identical stats
     * end up next to each other and ties are broken sensibly, then the name.
     */
    public Comparator<Row> tableComparator(int col, boolean descending) {
        return statsComparator(col, descending).thenComparing(r -> r.nameLower);
    }

    /** Like {@link #tableComparator} but without the name – rows comparing equal here have identical stats. */
    public Comparator<Row> statsComparator(int col, boolean descending) {
        Comparator<Row> cmp = columnComparator(col, descending);
        for (int i = 0; i < columns.size(); i++) {
            if (i == col || columns.get(i).kind() != Column.Kind.NUMBER) continue;
            cmp = cmp.thenComparing(columnComparator(i, true));
        }
        for (int i = 0; i < columns.size(); i++) {
            if (i == col || columns.get(i).kind() != Column.Kind.TEXT) continue;
            cmp = cmp.thenComparing(columnComparator(i, true));
        }
        return cmp;
    }

    /** Comparator for a single column without any tie-break – chain these with thenComparing. */
    public Comparator<Row> columnComparator(int col, boolean descending) {
        Column c = columns.get(col);
        // Rows without a value ("–") always go last, whichever direction is chosen.
        Comparator<Row> present = (a, b) -> Boolean.compare(b.hasValue(col), a.hasValue(col));
        Comparator<Row> cmp;
        switch (c.kind()) {
            case ITEM -> cmp = Comparator.comparing((Row r) -> r.nameLower);
            case MOD -> cmp = Comparator.comparing((Row r) -> r.modName.toLowerCase(Locale.ROOT));
            case NUMBER -> {
                Comparator<Row> v = Comparator.comparingDouble((Row r) -> r.hasValue(col) ? r.value(col) : 0);
                return present.thenComparing(descending ? v.reversed() : v);
            }
            case TEXT -> {
                Comparator<Row> v = (a, b) -> {
                    if (a.hasValue(col) && b.hasValue(col)) {
                        int d = Double.compare(a.value(col), b.value(col));
                        if (d != 0) return d;
                    }
                    return a.text(col).compareToIgnoreCase(b.text(col));
                };
                // "no text" counts as missing too, so "–" cells sort last in both directions
                Comparator<Row> hasText = (a, b) -> Boolean.compare(!b.text(col).isEmpty() || b.hasValue(col),
                        !a.text(col).isEmpty() || a.hasValue(col));
                return hasText.thenComparing(descending ? v.reversed() : v);
            }
            default -> cmp = Comparator.comparing((Row r) -> r.nameLower);
        }
        return descending ? cmp.reversed() : cmp;
    }
}
