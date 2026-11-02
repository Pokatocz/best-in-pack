package com.bestinpack.config;

import com.bestinpack.BestInPack;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * User settings, stored as config/bestinpack.json. Read the way JEI reads its config: leniently
 * (comments, trailing commas and unquoted keys are fine), one bad value only resets that value and
 * logs a warning, and only a file that cannot be parsed at all is set aside as .bak before the
 * defaults are written.
 */
public final class Config {

    public enum TooltipRank { SHIFT, ALWAYS, OFF }

    /** Look of the rankings screen. */
    public enum Style { DARK, VANILLA, LIST, COMPACT, BOOK }

    /** When the "#3 of 54 chestplates" line appears in item tooltips. */
    public TooltipRank tooltipRank = TooltipRank.SHIFT;
    /** Open the screen with every column shown. */
    public boolean detailsByDefault = false;
    /** Collapse items with identical stats into one row. */
    public boolean collapseIdentical = true;
    /** Small "BiP" button above the survival inventory. */
    public boolean inventoryButton = true;
    /** Reopen on the tab that was open last time (otherwise Food). */
    public boolean rememberTab = true;
    /** Visual style of the screen: DARK (glass), VANILLA (inventory window), LIST (options list), COMPACT, BOOK. */
    public Style style = Style.DARK;

    /** Which rankings get a line in item tooltips. Fuel is off by default – every wooden tool would show it. */
    public boolean tooltipFood = true;
    public boolean tooltipArmor = true;
    public boolean tooltipWeapons = true;
    public boolean tooltipTools = true;
    public boolean tooltipFuel = false;

    /** Mods left out of every table and ranking – mod id or name, e.g. "creativeitems" or "Creative Items". */
    public List<String> hiddenMods = new ArrayList<>();
    /** Single items left out, by id, e.g. "minecraft:enchanted_golden_apple". */
    public List<String> hiddenItems = new ArrayList<>();

    /** Items hidden in JEI/REI (edit mode, blacklist, pack filters) are left out here too. */
    public boolean respectJeiHiding = true;

    /** Only items listed in the creative inventory are ranked (like JEI) – debug items without a recipe stay out. */
    public boolean onlyCreativeItems = true;

    /** Items the player has never had are shown as "???" rows (stats only, no name, icon or mod). */
    public boolean onlyDiscovered = false;

    /** The open key also works inside inventories and JEI/REI (jumps to the hovered item). */
    public boolean openKeyInMenus = true;

    /**
     * The default key that opens Best in Pack, for modpacks whose packs already use B. It is only the default (new
     * installs, "Reset" in Controls) – players change their key in Minecraft's Controls as usual.
     */
    public String openKey = "key.keyboard.b";

    /** The first time a world is joined, a chat line says how to open Best in Pack ("Press B to see the best…"). */
    public boolean welcomeMessage = true;

    /** After joining a world, says once in chat when another mod uses the same key (e.g. B in big modpacks). */
    public boolean keyConflictWarning = true;

    /** Moves the "BiP" button above the inventory, in GUI pixels (+ = right / down). */
    public int inventoryButtonX = 0;
    public int inventoryButtonY = 0;

    private static final String[] KEYS = {"tooltipRank", "style", "detailsByDefault", "collapseIdentical", "inventoryButton",
            "rememberTab", "tooltipFood", "tooltipArmor", "tooltipWeapons", "tooltipTools", "tooltipFuel",
            "hiddenMods", "hiddenItems", "respectJeiHiding", "onlyCreativeItems", "onlyDiscovered", "openKey", "openKeyInMenus", "welcomeMessage", "keyConflictWarning", "inventoryButtonX", "inventoryButtonY"};

    /** Tooltip switch for one ranking group key ("food", "helmets", "weapons", "pickaxes", "fuels", …). */
    public boolean tooltipShows(String groupKey) {
        return switch (groupKey) {
            case "food" -> tooltipFood;
            case "helmets", "chestplates", "leggings", "boots" -> tooltipArmor;
            case "weapons" -> tooltipWeapons;
            case "pickaxes", "axes", "shovels", "hoes" -> tooltipTools;
            case "fuels" -> tooltipFuel;
            default -> true;
        };
    }

    /** True when an item is hidden by {@link #hiddenItems} or {@link #hiddenMods}. Case does not matter. */
    public boolean isHidden(String itemId, String modId, String modName) {
        for (String h : hiddenItems) if (h.trim().equalsIgnoreCase(itemId)) return true;
        for (String h : hiddenMods) {
            String m = h.trim();
            if (m.equalsIgnoreCase(modId) || m.equalsIgnoreCase(modName)) return true;
        }
        return false;
    }

    /** Changes when the hidden lists change, so the item index knows it has to be rebuilt. */
    public String hiddenSignature() {
        return String.join("|", hiddenMods).toLowerCase(Locale.ROOT) + "#" + String.join("|", hiddenItems).toLowerCase(Locale.ROOT) + "#" + respectJeiHiding + "#" + onlyCreativeItems;
    }

    /** What was wrong with the file at the last load (unknown names, bad values) – shown on the screen. */
    public transient List<String> problems = new ArrayList<>();
    private static final List<String> LOAD_PROBLEMS = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    /** Our own folder inside config/, like JEI's – the gear button opens it, so it should hold only our files. */
    public static Path dir() {
        return com.bestinpack.platform.Platform.get().configDir().resolve("bestinpack");
    }

    public static Path path() {
        return dir().resolve("bestinpack.json");
    }

    /** Versions before 0.4.4 kept the file directly in config/ – move it into the folder once. */
    private static void migrateOldLocation() {
        Path configDir = com.bestinpack.platform.Platform.get().configDir();
        Path oldFile = configDir.resolve("bestinpack.json");
        Path oldReadme = configDir.resolve("bestinpack-README.txt");
        try {
            if (Files.exists(oldFile) && !Files.exists(path())) {
                Files.createDirectories(dir());
                Files.move(oldFile, path());
                BestInPack.LOGGER.info("Moved {} to {}", oldFile, path());
            }
            Files.deleteIfExists(oldReadme);
        } catch (IOException e) {
            BestInPack.LOGGER.warn("Could not move the old config file into {}", dir(), e);
        }
    }

    public static Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** Re-read the file so edits made while the game runs are picked up the next time the screen opens. */
    public static Config reload() {
        instance = load();
        return instance;
    }

    private static Config load() {
        migrateOldLocation();
        Path p = path();
        if (!Files.exists(p)) {
            Config c = new Config();
            c.save(); // the file is the settings UI, so make sure it exists
            return c;
        }
        JsonObject json;
        try (Reader r = Files.newBufferedReader(p)) {
            JsonReader reader = new JsonReader(r);
            reader.setLenient(true);
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new IllegalStateException("not a JSON object");
            json = root.getAsJsonObject();
        } catch (Exception e) {
            Path bak = p.resolveSibling("bestinpack.json.bak");
            BestInPack.LOGGER.warn("Could not read {} ({}); keeping it as {} and writing defaults", p, e.getMessage(), bak.getFileName());
            try {
                Files.move(p, bak, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException io) {
                BestInPack.LOGGER.warn("Could not back up {}", p, io);
            }
            Config c = new Config();
            c.save();
            return c;
        }

        Config c = new Config();
        boolean dirty = false;
        LOAD_PROBLEMS.clear();
        for (String key : json.keySet()) {
            if (!java.util.Arrays.asList(KEYS).contains(key)) {
                BestInPack.LOGGER.warn("bestinpack.json: unknown setting '{}' – check the spelling (see README.txt)", key);
                LOAD_PROBLEMS.add("unknown setting \"" + key + "\"");
            }
        }
        c.tooltipRank = readEnum(json, "tooltipRank", c.tooltipRank, TooltipRank.class);
        c.style = readEnum(json, "style", c.style, Style.class);
        c.detailsByDefault = readBool(json, "detailsByDefault", c.detailsByDefault);
        c.collapseIdentical = readBool(json, "collapseIdentical", c.collapseIdentical);
        c.inventoryButton = readBool(json, "inventoryButton", c.inventoryButton);
        c.rememberTab = readBool(json, "rememberTab", c.rememberTab);
        c.tooltipFood = readBool(json, "tooltipFood", c.tooltipFood);
        c.tooltipArmor = readBool(json, "tooltipArmor", c.tooltipArmor);
        c.tooltipWeapons = readBool(json, "tooltipWeapons", c.tooltipWeapons);
        c.tooltipTools = readBool(json, "tooltipTools", c.tooltipTools);
        c.tooltipFuel = readBool(json, "tooltipFuel", c.tooltipFuel);
        c.hiddenMods = readList(json, "hiddenMods");
        c.hiddenItems = readList(json, "hiddenItems");
        c.respectJeiHiding = readBool(json, "respectJeiHiding", c.respectJeiHiding);
        c.onlyCreativeItems = readBool(json, "onlyCreativeItems", c.onlyCreativeItems);
        c.onlyDiscovered = readBool(json, "onlyDiscovered", c.onlyDiscovered);
        c.openKeyInMenus = readBool(json, "openKeyInMenus", c.openKeyInMenus);
        c.openKey = readKey(json, "openKey", c.openKey);
        c.welcomeMessage = readBool(json, "welcomeMessage", c.welcomeMessage);
        c.keyConflictWarning = readBool(json, "keyConflictWarning", c.keyConflictWarning);
        c.inventoryButtonX = readInt(json, "inventoryButtonX", c.inventoryButtonX);
        c.inventoryButtonY = readInt(json, "inventoryButtonY", c.inventoryButtonY);
        for (String key : KEYS) {
            if (!json.has(key)) dirty = true; // a missing option is added back with its default
        }
        c.problems = new ArrayList<>(LOAD_PROBLEMS);
        if (dirty || warned) c.save();
        else writeReadme(); // keep the help file current after a mod update even when the settings did not change
        warned = false;
        return c;
    }

    private static boolean warned;

    private static boolean readBool(JsonObject json, String key, boolean def) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return def;
        try {
            if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
            String s = e.getAsString().trim().toLowerCase(Locale.ROOT);
            if (s.equals("true") || s.equals("false")) return Boolean.parseBoolean(s);
        } catch (Exception ignored) {}
        BestInPack.LOGGER.warn("bestinpack.json: '{}' should be true or false, found {} – using {}", key, e, def);
        LOAD_PROBLEMS.add("\"" + key + "\" should be true or false");
        warned = true;
        return def;
    }

    private static int readInt(JsonObject json, String key, int def) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return def;
        try {
            return (int) Math.round(Double.parseDouble(e.getAsString().trim()));
        } catch (Exception ignored) {}
        BestInPack.LOGGER.warn("bestinpack.json: '{}' should be a number, found {} – using {}", key, e, def);
        LOAD_PROBLEMS.add("\"" + key + "\" should be a number");
        warned = true;
        return def;
    }

    /** A list of strings; a single string or comma-separated text is accepted too. */
    private static List<String> readList(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return out;
        try {
            if (e.isJsonArray()) {
                for (JsonElement x : e.getAsJsonArray()) {
                    String v = x.getAsString().trim();
                    if (!v.isEmpty()) out.add(v);
                }
            } else {
                for (String v : e.getAsString().split(",")) if (!v.isBlank()) out.add(v.trim());
            }
            return out;
        } catch (Exception ignored) {}
        BestInPack.LOGGER.warn("bestinpack.json: '{}' should be a list like [\"a\", \"b\"], found {} – using []", key, e);
        warned = true;
        return out;
    }

    /** A key name: "key.keyboard.n", or short forms like "N", "F6", "left alt"; "none" = no default key. */
    private static String readKey(JsonObject json, String key, String def) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return def;
        String raw = "";
        try {
            raw = e.getAsString();
        } catch (Exception ignored) {}
        String name = normalizeKey(raw);
        if (name != null) return name;
        BestInPack.LOGGER.warn("bestinpack.json: '{}' is not a key name like \"key.keyboard.n\" or \"N\", found {} – using {}", key, e, def);
        LOAD_PROBLEMS.add("\"" + key + "\" should be a key like \"key.keyboard.n\" or \"N\"");
        warned = true;
        return def;
    }

    /** The vanilla name of a key ("N" -> "key.keyboard.n"), or null when Minecraft does not know that key. */
    public static String normalizeKey(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("none") || s.equals("unbound")) return "key.keyboard.unknown";
        String name = s.startsWith("key.") ? s : "key.keyboard." + s.replace(' ', '.');
        try {
            com.mojang.blaze3d.platform.InputConstants.Key k = com.mojang.blaze3d.platform.InputConstants.getKey(name);
            if (k == null || (k == com.mojang.blaze3d.platform.InputConstants.UNKNOWN && !name.equals("key.keyboard.unknown"))) return null;
            return k.getName();
        } catch (Throwable t) {
            return null;
        }
    }

    private static <E extends Enum<E>> E readEnum(JsonObject json, String key, E def, Class<E> type) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return def;
        try {
            return Enum.valueOf(type, e.getAsString().trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {}
        BestInPack.LOGGER.warn("bestinpack.json: '{}' should be one of {}, found {} – using {}", key,
                java.util.Arrays.toString(type.getEnumConstants()), e, def);
        LOAD_PROBLEMS.add("\"" + key + "\" should be one of " + java.util.Arrays.toString(type.getEnumConstants()));
        warned = true;
        return def;
    }

    /** Written next to the JSON so the options are explained where people edit them. */
    private static final String README = """
            BEST IN PACK
            ============

            Shows which food, armor, weapon, tool and fuel is the best in your modpack.


            CONTROLS
            --------

                B                    open / close the screen
                B over an item       jump to that item (inventory, chest, JEI, REI)
                R / U over a row     recipe / uses (the JEI or REI keys)
                Shift over an item   tooltip shows "Best in Pack: #3 of 54 chestplates"
                Click a column       sort, click again to reverse
                Click a row          show its recipe (needs JEI or REI)
                Right-click a row    show what it is used for
                Search box           type a name, @mod to show one mod, -word to hide
                Have                 only items in your inventory
                Details              show all columns
                Gear button          open the settings folder
                Mouse wheel          scroll, Shift + wheel scrolls sideways

            B does nothing? Another mod uses the same key (big modpacks often do).
            Change "Open Best in Pack" in Options > Controls > Key Binds.


            SETTINGS
            --------

            Open the settings folder with the gear button and edit bestinpack.json.
            Changes apply the next time you open the screen.

                SCREEN
                style               DARK, VANILLA, LIST, COMPACT or BOOK             default DARK
                detailsByDefault    true = start with all columns                    default false
                collapseIdentical   true = same items share one row (Oak Boat +19)   default true
                rememberTab         true = open the last used tab                    default true

                TOOLTIPS
                tooltipRank         SHIFT, ALWAYS or OFF                             default SHIFT
                tooltipFood         true / false                                     default true
                tooltipArmor        true / false                                     default true
                tooltipWeapons      true / false                                     default true
                tooltipTools        true / false                                     default true
                tooltipFuel         true / false                                     default false

                HIDING
                hiddenMods          mods to hide, e.g. ["modid"]                     default []
                hiddenItems         items to hide, e.g. ["minecraft:apple"]          default []
                                    (press F3+H in game to see item ids)
                respectJeiHiding    true = items hidden in JEI/REI are hidden here too  default true
                onlyCreativeItems   true = only items in the creative inventory      default true
                                    (false also ranks debug items without a recipe)
                onlyDiscovered      true = items you never had show as ???           default false

                KEY AND BUTTON
                openKey             default key that opens Best in Pack              default "key.keyboard.b"
                                    e.g. "key.keyboard.n" or just "N", "none" = no key
                                    (for modpacks: only new installs and "Reset" in Controls
                                    use it – players change their key in Controls)
                openKeyInMenus      true = B also works in inventories and JEI/REI   default true
                inventoryButton     true = BiP button above the inventory            default true
                inventoryButtonX    move the button left/right, + = right            default 0
                inventoryButtonY    move the button up/down, + = down                default 0

                CHAT MESSAGES (set both to false for none)
                welcomeMessage      true = "Press B to see the best…" once per world  default true
                keyConflictWarning  true = say in chat when another mod uses B too   default true

            A wrong value goes back to its default. Delete bestinpack.json to reset everything.
            """;

    private static void writeReadme() {
        Path readme = dir().resolve("README.txt");
        try {
            if (Files.exists(readme) && Files.readString(readme).equals(README)) return;
            Files.createDirectories(dir());
            Files.writeString(readme, README);
        } catch (IOException e) {
            BestInPack.LOGGER.warn("Could not write {}", readme, e);
        }
    }

    public void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(this, w);
            }
            writeReadme();
        } catch (IOException e) {
            BestInPack.LOGGER.warn("Could not write {}", p, e);
        }
    }
}
