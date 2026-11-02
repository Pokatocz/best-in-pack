package com.bestinpack.client;

import com.bestinpack.BestInPack;
import com.bestinpack.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The two chat messages Best in Pack writes after joining a world, both switchable in bestinpack.json:
 * a one-time hint per world ("Press B to see the best…", {@code welcomeMessage}) and a note when another mod
 * uses the same key ({@code keyConflictWarning}). Hovering either message says which setting turns it off.
 */
public final class JoinMessages {
    private JoinMessages() {}

    private static final int MAX_CONFLICTS_LISTED = 4;
    /** The key note is shown at most once per game session, so it does not repeat on every world join. */
    private static boolean conflictWarned;

    /** Called a moment after joining, once the join messages of the game itself are through. */
    public static void afterJoin(Minecraft client) {
        welcome(client, Discovery.worldKey());
        if (!conflictWarned) warnKeyConflicts(client, client.options.keyMappings);
    }

    // ------------------------------------------------------------------ welcome

    public static Path welcomedFile() {
        return Config.dir().resolve("welcomed.txt");
    }

    /** Shows the hint the first time a world (or server) is joined. Returns true when it was shown. */
    public static boolean welcome(Minecraft client, String worldKey) {
        if (client.player == null || worldKey == null || !Config.get().welcomeMessage) return false;
        Set<String> seen = readWelcomed();
        if (seen.contains(worldKey)) return false;
        Component text = welcomeText();
        if (text == null) return false; // no key and no button: nothing to point at
        chat(client, text);
        seen.add(worldKey);
        writeWelcomed(seen);
        BestInPack.LOGGER.info("Best in Pack: welcome message shown for {}", worldKey);
        return true;
    }

    public static Component welcomeText() {
        Config cfg = Config.get();
        KeyMapping key = BestInPackClient.openKey;
        MutableComponent msg;
        if (key != null && !key.isUnbound()) {
            Component name = key.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.WHITE);
            msg = Component.translatable("bestinpack.chat.welcome.key", name);
        } else if (cfg.inventoryButton) {
            msg = Component.translatable("bestinpack.chat.welcome.button");
        } else {
            return null;
        }
        if (cfg.tooltipRank == Config.TooltipRank.SHIFT) msg.append(Component.translatable("bestinpack.chat.welcome.shift"));
        return prefix().append(msg.withStyle(ChatFormatting.YELLOW))
                .withStyle(offHint("welcomeMessage"));
    }

    private static Set<String> readWelcomed() {
        Set<String> out = new LinkedHashSet<>();
        Path f = welcomedFile();
        if (!Files.exists(f)) return out;
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) out.add(line.trim());
            }
        } catch (IOException e) {
            BestInPack.LOGGER.warn("Could not read {}", f, e);
        }
        return out;
    }

    private static void writeWelcomed(Set<String> worlds) {
        Path f = welcomedFile();
        try {
            Files.createDirectories(f.getParent());
            Files.write(f, worlds, StandardCharsets.UTF_8);
        } catch (IOException e) {
            BestInPack.LOGGER.warn("Could not write {}", f, e);
        }
    }

    // ------------------------------------------------------------------ key conflicts

    /**
     * Big packs often put several mods on the same key (in ATM10 Just Zoom, Sophisticated Backpacks and more use B).
     * NeoForge then fires all of them and another mod's screen usually wins, so the key seems to do nothing.
     * Say so once in chat, with the names of the other key bindings, instead of leaving the player guessing.
     */
    public static boolean warnKeyConflicts(Minecraft client, KeyMapping[] all) {
        KeyMapping key = BestInPackClient.openKey;
        if (key == null || client.player == null) return false;
        if (!Config.get().keyConflictWarning || key.isUnbound()) return false;
        List<Component> others = conflictsOf(key, all);
        conflictWarned = true;
        if (others.isEmpty()) return false;
        BestInPack.LOGGER.info("Key {} of Best in Pack is also used by: {}", key.getTranslatedKeyMessage().getString(),
                others.stream().map(Component::getString).collect(Collectors.joining(", ")));
        MutableComponent list = Component.empty();
        for (int i = 0; i < Math.min(others.size(), MAX_CONFLICTS_LISTED); i++) {
            if (i > 0) list.append(", ");
            list.append(others.get(i).copy().withStyle(ChatFormatting.WHITE));
        }
        if (others.size() > MAX_CONFLICTS_LISTED) {
            list.append(" ").append(Component.translatable("bestinpack.chat.keyconflict.more", others.size() - MAX_CONFLICTS_LISTED));
        }
        Component name = key.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.WHITE);
        chat(client, prefix()
                .append(Component.translatable("bestinpack.chat.keyconflict", name, list).withStyle(ChatFormatting.YELLOW))
                .withStyle(offHint("keyConflictWarning")));
        return true;
    }

    /** "Category: Name" of every other bound key mapping that NeoForge treats as clashing with {@code key}. */
    public static List<Component> conflictsOf(KeyMapping key, KeyMapping[] all) {
        List<Component> out = new ArrayList<>();
        for (KeyMapping other : all) {
            if (other == key || other.isUnbound() || !key.same(other)) continue;
            // Vanilla's debug keys (1.21.11+: "Show Hitboxes" = F3 + B) only fire together with F3 – no real clash.
            if (other.getName().startsWith("key.debug.")) continue;
            out.add(Keys.categoryName(other).copy().append(": ").append(Component.translatable(other.getName())));
        }
        return out;
    }

    // ------------------------------------------------------------------ shared

    private static void chat(Minecraft client, Component message) {
        //#if MC >= 26.1
        //$$ if (client.player != null) client.player.sendSystemMessage(message);
        //#else
        client.gui.getChat().addMessage(message);
        //#endif
    }

    private static MutableComponent prefix() {
        return Component.literal("[Best in Pack] ").withStyle(ChatFormatting.GOLD);
    }

    /** Hovering the message tells how to switch it off. */
    private static Style offHint(String setting) {
        Component text = Component.translatable("bestinpack.chat.turnoff", setting);
        //#if MC >= 1.21.5
        //$$ return Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(text));
        //#else
        return Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text));
        //#endif
    }

    /** The text shown when hovering a message (self-test). */
    public static String hoverTextOf(Component message) {
        HoverEvent e = message.getStyle().getHoverEvent();
        if (e == null) return null;
        //#if MC >= 1.21.5
        //$$ return e instanceof HoverEvent.ShowText st ? st.value().getString() : null;
        //#else
        Component c = e.getValue(HoverEvent.Action.SHOW_TEXT);
        return c == null ? null : c.getString();
        //#endif
    }

    /** For the self-test: forget the per-session flag. */
    public static void resetForTest() {
        conflictWarned = false;
    }
}
