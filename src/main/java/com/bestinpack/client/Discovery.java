package com.bestinpack.client;

import com.bestinpack.BestInPack;
import com.bestinpack.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.stats.StatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Items the player "knows" in the current world or server: everything that has ever been in their inventory, plus
 * what the game's own statistics say they crafted, picked up or used (so a world played before installing the mod
 * is not empty). Stored per world/server in config/bestinpack/discovered/. Used by the {@code onlyDiscovered}
 * setting; tracked all the time, so turning the setting on later already has the history.
 */
public final class Discovery {
    private Discovery() {}

    private static final Set<Item> KNOWN = new HashSet<>();
    private static String worldKey;
    private static boolean dirty;
    private static int ticks;
    private static long lastSave;
    /** Bumped whenever the set grows, so screens can tell that their view is stale. */
    private static int version;

    public static int version() {
        return version;
    }

    public static int count() {
        return KNOWN.size();
    }

    /** True when the item should be shown normally: the setting is off, the player is in creative, or it is known. */
    public static boolean isKnown(Item item) {
        if (!Config.get().onlyDiscovered) return true;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.isCreative()) return true;
        return KNOWN.contains(item);
    }

    /** True when undiscovered items are being masked right now (setting on, not creative). */
    public static boolean active() {
        if (!Config.get().onlyDiscovered) return false;
        Minecraft client = Minecraft.getInstance();
        return client.player == null || !client.player.isCreative();
    }

    // ------------------------------------------------------------------ lifecycle

    static void onJoin(Minecraft client) {
        save();
        KNOWN.clear();
        worldKey = keyFor(client);
        ticks = 0;
        load();
    }

    static void onDisconnect() {
        save();
        KNOWN.clear();
        worldKey = null;
    }

    static void tick(Minecraft client) {
        Player player = client.player;
        if (player == null || worldKey == null) return;
        ticks++;
        // Ask the server for the statistics shortly after joining, then read them twice (the answer is async).
        if (ticks == 40 && client.getConnection() != null) {
            client.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }
        if (ticks == 100 || ticks == 400) importStats(client);
        if (ticks % 20 == 0) scanInventory(player);
        if (dirty && System.currentTimeMillis() - lastSave > 10_000) save();
    }

    private static void scanInventory(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) add(inv.getItem(i));
        for (EquipmentSlot slot : EquipmentSlot.values()) add(player.getItemBySlot(slot));
        if (player.containerMenu != null) add(player.containerMenu.getCarried());
    }

    private static void importStats(Minecraft client) {
        if (client.player == null) return;
        StatsCounter stats = client.player.getStats();
        int before = KNOWN.size();
        for (Item item : BuiltInRegistries.ITEM) {
            if (stats.getValue(Stats.ITEM_CRAFTED, item) > 0 || stats.getValue(Stats.ITEM_PICKED_UP, item) > 0
                    || stats.getValue(Stats.ITEM_USED, item) > 0) {
                if (KNOWN.add(item)) dirty = true;
            }
        }
        if (KNOWN.size() != before) {
            version++;
            BestInPack.LOGGER.info("Best in Pack: {} known items after reading statistics", KNOWN.size());
        }
    }

    private static void add(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (KNOWN.add(stack.getItem())) {
            dirty = true;
            version++;
        }
    }

    /** For the self-test. */
    public static void addForTest(Item item) {
        if (KNOWN.add(item)) {
            dirty = true;
            version++;
        }
    }

    // ------------------------------------------------------------------ storage

    private static String keyFor(Minecraft client) {
        String raw;
        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            Path root = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            raw = "world_" + root.getFileName();
        } else {
            ServerData info = client.getCurrentServer();
            raw = "server_" + (info != null ? info.ip : "unknown");
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    /** "world_<folder>" or "server_<address>" of the world joined last, null before joining. */
    public static String worldKey() {
        return worldKey;
    }

    public static Path file() {
        return Config.dir().resolve("discovered").resolve((worldKey == null ? "none" : worldKey) + ".txt");
    }

    private static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                var id = com.bestinpack.util.Ids.tryParse(line.trim());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
                //#if MC >= 1.21.2
                //$$ KNOWN.add(BuiltInRegistries.ITEM.getValue(id));
                //#else
                KNOWN.add(BuiltInRegistries.ITEM.get(id));
                //#endif
            }
            version++;
        } catch (IOException e) {
            BestInPack.LOGGER.warn("Could not read {}", f, e);
        }
    }

    public static void save() {
        if (!dirty || worldKey == null) return;
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            List<String> lines = new ArrayList<>();
            for (Item item : KNOWN) lines.add(BuiltInRegistries.ITEM.getKey(item).toString());
            lines.sort(null);
            Files.write(f, lines, StandardCharsets.UTF_8);
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException e) {
            BestInPack.LOGGER.warn("Could not write {}", f, e);
        }
    }
}
