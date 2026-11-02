package com.bestinpack.client.gui;

import com.bestinpack.config.Config;

/** Colours and metrics of one screen style. Everything the screen draws by itself goes through this. */
record Theme(
        Config.Style style,
        int rowH,
        boolean shadow,       // text shadow
        boolean zebra,        // alternate row tint
        boolean gridLines,    // thin line under every row
        boolean hoverOutline, // outline the hovered row instead of filling it
        int text, int muted, int dim, int header, int sorted, int have, int count,
        int gold, int silver, int bronze,
        int panel, int panelBorder, int headerBg, int rowAlt, int hover, int gridLine, int separator,
        int track, int thumb, int thumbActive
) {
    static Theme of(Config.Style style) {
        return switch (style) {
            case VANILLA -> new Theme(style, 18, false, true, false, false,
                    0xFF404040, 0xFF707070, 0xFF8A8A8A, 0xFF303030, 0xFF1F4FA0, 0xFF2E7D32, 0xFF1F5FA0,
                    0xFFB8860B, 0xFF6E6E6E, 0xFF8B4513,
                    0xFFC6C6C6, 0xFF000000, 0xFFA8A8A8, 0x14000000, 0x30000000, 0, 0xFF8B8B8B,
                    0xFF8B8B8B, 0xFFFFFFFF, 0xFFE0E0E0);
            case LIST -> new Theme(style, 18, true, false, false, true,
                    0xFFFFFFFF, 0xFFB0B0B0, 0xFF808080, 0xFFE8E8E8, 0xFFFFE080, 0xFF90FF90, 0xFF80C8FF,
                    0xFFFFD700, 0xFFC8C8C8, 0xFFD28B4A,
                    0, 0, 0, 0, 0xFFFFFFFF, 0, 0x40FFFFFF,
                    0xFF000000, 0xFF808080, 0xFFC0C0C0);
            case COMPACT -> new Theme(style, 16, false, false, true, false,
                    0xFFE6E6E6, 0xFF9A9A9A, 0xFF6A6A6A, 0xFFBDBDBD, 0xFFFFD766, 0xFF8BE78B, 0xFF7FC3FF,
                    0xFFFFD700, 0xFFC8C8C8, 0xFFD28B4A,
                    0xFF1E1E1E, 0xFF3C3C3C, 0xFF2A2A2A, 0, 0x30FFFFFF, 0x18FFFFFF, 0xFF3C3C3C,
                    0xFF151515, 0xFF5A5A5A, 0xFF8A8A8A);
            case BOOK -> new Theme(style, 18, false, true, false, false,
                    0xFF3B2A14, 0xFF6F5A3C, 0xFF9A8565, 0xFF2E2010, 0xFF8A2B0B, 0xFF2E7D32, 0xFF1F5FA0,
                    0xFFA67C00, 0xFF6E6E6E, 0xFF8B4513,
                    0xFFF4ECD8, 0xFFB89B6A, 0xFFE6D8B8, 0x14000000, 0x24000000, 0, 0xFFB89B6A,
                    0xFFD9CBAA, 0xFF8B7250, 0xFF5B4A30);
            default -> new Theme(style, 18, true, true, false, false,
                    0xFFFFFFFF, 0xFFAAAAAA, 0xFF777777, 0xFFE8E8E8, 0xFFFFE080, 0xFF90FF90, 0xFF80C8FF,
                    0xFFFFD700, 0xFFC8C8C8, 0xFFD28B4A,
                    0x70000000, 0, 0x60000000, 0x18FFFFFF, 0x40FFFFFF, 0, 0x50FFFFFF,
                    0x50000000, 0xA0FFFFFF, 0xFFFFFFFF);
        };
    }
}
