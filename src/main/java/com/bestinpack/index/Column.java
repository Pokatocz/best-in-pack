package com.bestinpack.index;

import net.minecraft.network.chat.Component;

/**
 * One table column. {@code width} is in GUI pixels; the ITEM column is flexible.
 * {@code optional} columns are dropped first when the screen is too narrow.
 */
public record Column(String key, int width, Kind kind, int decimals, boolean optional, boolean descDefault) {

    public enum Kind { RANK, ITEM, MOD, NUMBER, TEXT }

    public static Column rank() {
        return new Column("rank", 24, Kind.RANK, 0, false, false);
    }

    public static Column item() {
        return new Column("name", 120, Kind.ITEM, 0, false, false);
    }

    public static Column mod() {
        return new Column("mod", 52, Kind.MOD, 0, false, false);
    }

    public static Column number(String key, int width, int decimals) {
        return new Column(key, width, Kind.NUMBER, decimals, false, true);
    }

    public static Column number(String key, int width, int decimals, boolean optional) {
        return new Column(key, width, Kind.NUMBER, decimals, optional, true);
    }

    /** Text column that sorts by a hidden numeric value when one is present (e.g. tool tier). */
    public static Column text(String key, int width, boolean optional) {
        return new Column(key, width, Kind.TEXT, 0, optional, true);
    }

    public Component label() {
        return Component.translatable("bestinpack.col." + key);
    }

    public boolean sortable() {
        return kind != Kind.RANK;
    }
}
