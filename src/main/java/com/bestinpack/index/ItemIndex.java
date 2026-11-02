package com.bestinpack.index;

import com.bestinpack.BestInPack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
//#if MC < 1.21.2
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.HoneyBottleItem;
//#endif
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
//#if MC < 1.21.5
import net.minecraft.world.item.SwordItem;
//#endif
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.bestinpack.platform.Platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the four category tables from data that is already in the game
 * (item components, attribute modifiers, burn times) and keeps a
 * per-item rank lookup for the tooltip line.
 */
public final class ItemIndex {

    /** A ranking an item belongs to, e.g. "#3 of 54 chestplates". */
    public record RankEntry(String groupKey, int rank, int total) {}

    private static final Map<Category, Table> TABLES = new EnumMap<>(Category.class);
    private static final Map<Item, List<RankEntry>> RANKS = new HashMap<>();
    private static boolean dirty = true;
    private static boolean building = false;
    /** The last build had a world, so feature flags (and entity-dependent data) were available. */
    private static boolean builtWithWorld = false;
    private static boolean fuelData = false;
    private static long buildMillis;
    /** Hidden mods/items the index was built with – a change in the config forces a rebuild. */
    private static String builtHidden = "";
    /** Items left out at the last build because no creative tab lists them; -1 = the filter was not used. */
    private static int lastNotInCreative = -1;

    public static int lastNotInCreative() {
        return lastNotInCreative;
    }
    /** Whether the recipe viewer's hidden list was available when the index was built. */
    private static boolean builtWithViewer = false;

    private ItemIndex() {}

    /** The common "c:hidden_from_recipe_viewers" tag that all three loaders use for creative-only/technical items. */
    private static final TagKey<Item> HIDDEN_FROM_VIEWERS = TagKey.create(Registries.ITEM,
            com.bestinpack.util.Ids.of("c", "hidden_from_recipe_viewers"));

    public static void markDirty() {
        dirty = true;
    }

    public static void clear() {
        TABLES.clear();
        RANKS.clear();
        dirty = true;
        fuelData = false;
        builtWithWorld = false;
    }

    public static boolean isBuilt() {
        return !dirty && !TABLES.isEmpty();
    }

    public static long lastBuildMillis() {
        return buildMillis;
    }

    /** True when the last build could read burn times. */
    public static boolean hasFuelData() {
        return fuelData;
    }

    public static Table get(Category category, Level world, Player player) {
        ensureBuilt(world, player);
        return TABLES.get(category);
    }

    /** Rank lines for a tooltip. Never builds the index – {@link #ensureBuilt} is called from the client tick instead. */
    public static List<RankEntry> ranksFor(Item item) {
        // While a rebuild is pending (e.g. after an item was hidden in JEI) the last ranks are still good enough for
        // a tooltip – better than no line at all until the screen is opened again. Leaving the world clears them.
        if (building) return List.of();
        List<RankEntry> list = RANKS.get(item);
        return list == null ? List.of() : list;
    }

    public static void ensureBuilt(Level world, Player player) {
        if (building) return;
        // An index built without a world is upgraded once a world is available (feature flags, mod hooks).
        com.bestinpack.config.Config config = com.bestinpack.config.Config.get();
        String hidden = config.hiddenSignature();
        boolean viewerNow = config.respectJeiHiding && com.bestinpack.compat.RecipeViewers.hidingReady();
        if (dirty || (world != null && !builtWithWorld) || !hidden.equals(builtHidden) || (viewerNow && !builtWithViewer)) {
            rebuild(world, player);
        }
    }

    // ------------------------------------------------------------------ build

    public static void rebuild(Level world, Player player) {
        if (building) return;
        building = true;
        try {
            doRebuild(world, player);
        } finally {
            building = false;
        }
    }

    private static void doRebuild(Level world, Player player) {
        long start = System.currentTimeMillis();
        TABLES.clear();
        RANKS.clear();
        com.bestinpack.config.Config config = com.bestinpack.config.Config.get();
        builtHidden = config.hiddenSignature();
        boolean viewerHiding = config.respectJeiHiding && com.bestinpack.compat.RecipeViewers.hidingReady();
        builtWithViewer = viewerHiding;
        int hiddenByViewer = 0;
        // Only what a player can find in the creative inventory (like JEI): no uncraftable debug/technical items.
        java.util.Set<Item> creative = config.onlyCreativeItems ? CreativeItems.collect(world) : null;
        int notInCreative = 0;

        Item.TooltipContext tooltipContext = world != null ? Item.TooltipContext.of(world) : Item.TooltipContext.EMPTY;

        Table food = foodTable();
        Table armor = armorTable();
        Table weapons = weaponsTable();
        Table fuel = fuelTable();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            // Items behind a disabled experiment do not exist in this world.
            if (world != null && !item.isEnabled(world.enabledFeatures())) continue;
            ItemStack stack;
            try {
                stack = item.getDefaultInstance();
            } catch (Throwable t) {
                continue;
            }
            if (stack == null || stack.isEmpty()) continue;
            if (stack.is(HIDDEN_FROM_VIEWERS)) continue;
            if (creative != null && !creative.contains(item)) { notInCreative++; continue; }

            var key = BuiltInRegistries.ITEM.getKey(item);
            String id = key.toString();
            String modId = key.getNamespace();
            String modName = modDisplayName(modId);
            if (config.isHidden(id, modId, modName)) continue;
            if (viewerHiding && com.bestinpack.compat.RecipeViewers.isHidden(stack)) { hiddenByViewer++; continue; }
            String name;
            try {
                name = stack.getHoverName().getString();
            } catch (Throwable t) {
                name = key.getPath();
            }

            try {
                indexFood(food, stack, id, modId, modName, name, player);
            } catch (Throwable t) {
                BestInPack.LOGGER.debug("Food indexing failed for {}", id, t);
            }
            try {
                indexArmor(armor, stack, id, modId, modName, name, tooltipContext, player);
            } catch (Throwable t) {
                BestInPack.LOGGER.debug("Armor indexing failed for {}", id, t);
            }
            try {
                indexWeapon(weapons, stack, id, modId, modName, name, tooltipContext, player);
            } catch (Throwable t) {
                BestInPack.LOGGER.debug("Weapon indexing failed for {}", id, t);
            }
            try {
                indexFuel(fuel, stack, id, modId, modName, name, world);
            } catch (Throwable t) {
                BestInPack.LOGGER.debug("Fuel indexing failed for {}", id, t);
            }
        }

        fuelData = Platform.get().hasFuelData(world);
        builtWithWorld = world != null;

        TABLES.put(Category.FOOD, food);
        TABLES.put(Category.ARMOR, armor);
        TABLES.put(Category.WEAPONS, weapons);
        TABLES.put(Category.FUEL, fuel);

        computeRanks(food, armor, weapons, fuel);

        dirty = false;
        buildMillis = System.currentTimeMillis() - start;
        lastNotInCreative = creative == null ? -1 : notInCreative;
        BestInPack.LOGGER.info("Best in Pack indexed {} foods, {} armor pieces, {} weapons/tools, {} fuels in {} ms{}{}",
                food.rows.size(), armor.rows.size(), weapons.rows.size(), fuel.rows.size(), buildMillis,
                viewerHiding ? " (" + hiddenByViewer + " items hidden by " + com.bestinpack.compat.RecipeViewers.available() + ")" : "",
                creative == null ? " (creative tabs not used)" : " (" + notInCreative + " items not in the creative inventory skipped)");
    }

    // ------------------------------------------------------------------ tables

    // Food columns
    static final int F_HUNGER = 3, F_SAT = 4, F_TOTAL = 5, F_EAT = 6, F_PER_SEC = 7, F_EFFECTS = 8;
    /** Effects sort key for food without any effect – below every debuff. */
    static final double NO_EFFECT = -1e9;

    private static Table foodTable() {
        List<Column> cols = List.of(
                Column.rank(), Column.item(), Column.mod(),
                Column.number("hunger", 40, 0),
                Column.number("saturation", 40, 1),
                Column.number("total", 38, 1),
                Column.number("eat_time", 40, 1, true),
                Column.number("hunger_per_sec", 46, 1, true),
                Column.text("effects", 130, true));
        Table t = new Table(Category.FOOD, cols, F_TOTAL);
        t.essential(null, "hunger", "saturation", "total", "effects");
        return t;
    }

    // Armor columns
    static final int A_SLOT = 3, A_ARMOR = 4, A_TOUGH = 5, A_KB = 6, A_DUR = 7, A_ENCH = 8, A_BONUS = 9, A_NOTES = 10;

    private static Table armorTable() {
        List<Column> cols = List.of(
                Column.rank(), Column.item(), Column.mod(),
                Column.text("slot", 40, false),
                Column.number("armor", 38, 1),
                Column.number("toughness", 42, 1),
                Column.number("knockback", 40, 1, true),
                Column.number("durability", 42, 0),
                Column.number("enchantability", 34, 0, true),
                Column.text("bonuses", 100, true),
                Column.text("notes", 100, true));
        Table t = new Table(Category.ARMOR, cols, A_ARMOR);
        t.facets(List.of("Head", "Chest", "Legs", "Feet"));
        t.facetColumn("slot");
        t.essential(null, "slot", "armor", "toughness", "durability");
        return t;
    }

    // Weapons & tools columns
    static final int W_TYPE = 3, W_SCORE = 4, W_DMG = 5, W_SPEED = 6, W_DPS = 7, W_MINING = 8, W_TIER = 9, W_DUR = 10, W_ENCH = 11, W_NOTES = 12;

    private static Table weaponsTable() {
        List<Column> cols = List.of(
                Column.rank(), Column.item(), Column.mod(),
                Column.text("type", 46, false),
                Column.number("score", 42, 1),
                Column.number("damage", 42, 1),
                Column.number("speed", 38, 2, true),
                Column.number("dps", 36, 1),
                Column.number("mining", 40, 1),
                Column.text("tier", 54, false),
                Column.number("durability", 42, 0),
                Column.number("enchantability", 34, 0, true),
                Column.text("notes", 100, true));
        Table t = new Table(Category.WEAPONS, cols, W_DMG);
        t.facets(List.of("Weapons", "Pickaxes", "Axes", "Shovels", "Hoes"));
        t.facetColumn("type");
        t.keepFacetColumn("Weapons"); // swords, axes, tridents… the type still matters there
        t.facetSort("Weapons", W_DPS);
        t.essential(null, "type", "damage", "dps", "durability");
        // Score only means something for mining tools – in the mixed "All" view it would be "–" for every weapon.
        t.hide(null, "score");
        t.essential("Weapons", "type", "damage", "speed", "dps", "durability");
        for (String tool : List.of("Pickaxes", "Axes", "Shovels", "Hoes")) {
            t.facetSort(tool, W_SCORE);
            t.essential(tool, "score", "mining", "tier", "durability");
        }
        return t;
    }

    // Fuel columns
    static final int U_BURN = 3, U_SMELTS = 4, U_STACK = 5;

    private static Table fuelTable() {
        List<Column> cols = List.of(
                Column.rank(), Column.item(), Column.mod(),
                Column.number("burn_time", 50, 1),
                Column.number("smelts", 44, 2),
                Column.number("smelts_stack", 48, 0, true));
        Table t = new Table(Category.FUEL, cols, U_BURN);
        t.essential(null, "burn_time", "smelts_stack");
        return t;
    }

    // ------------------------------------------------------------------ food

    private static void indexFood(Table table, ItemStack stack, String id, String modId, String modName, String name, Player player) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return;
        //#if MC >= 1.21.2
        //$$ net.minecraft.world.item.component.Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        //#endif

        Row row = new Row(stack, id, modId, modName, name, table.columns.size());
        double hunger = food.nutrition();
        double saturation = food.saturation();
        //#if MC >= 1.21.2
        //$$ double seconds = consumable != null ? consumable.consumeSeconds() : 1.6;
        //#else
        double seconds = food.eatSeconds();
        //#endif
        row.values[F_HUNGER] = hunger;
        row.values[F_SAT] = saturation;
        row.values[F_TOTAL] = hunger + saturation;
        row.values[F_EAT] = seconds;
        row.values[F_PER_SEC] = seconds > 0 ? hunger / seconds : hunger;

        StringBuilder effects = new StringBuilder();
        double effectScore = 0;
        //#if MC >= 1.21.2
        //$$ List<net.minecraft.world.item.consume_effects.ConsumeEffect> consumeEffects = consumable != null ? consumable.onConsumeEffects() : List.of();
        //$$ List<Object[]> possibleEffects = new ArrayList<>();
        //$$ for (net.minecraft.world.item.consume_effects.ConsumeEffect ce : consumeEffects) {
        //$$     if (ce instanceof net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect apply) {
        //$$         for (MobEffectInstance inst : apply.effects()) possibleEffects.add(new Object[]{inst, apply.probability()});
        //$$     }
        //$$ }
        //#else
        List<Object[]> possibleEffects = new ArrayList<>();
        for (FoodProperties.PossibleEffect possible : food.effects()) possibleEffects.add(new Object[]{possible.effect(), possible.probability()});
        //#endif
        for (Object[] possible : possibleEffects) {
            MobEffectInstance inst = (MobEffectInstance) possible[0];
            if (inst == null) continue;
            float probability = (Float) possible[1];
            if (!effects.isEmpty()) effects.append(", ");
            String effName = Language.getInstance().getOrDefault(inst.getEffect().value().getDescriptionId());
            boolean harmful = inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
            effects.append(harmful ? "-" : "+").append(effName);
            if (inst.getAmplifier() > 0) effects.append(' ').append(roman(inst.getAmplifier() + 1));
            if (inst.isInfiniteDuration()) effects.append(" (permanent)");
            else effects.append(' ').append(fmtSeconds(inst.getDuration() / 20.0));
            if (probability < 1f) effects.append(" (").append(Math.round(probability * 100)).append("%)");
            // Duration is capped at 10 minutes so one very long effect does not dwarf everything else;
            // a permanent effect counts as that maximum (getDuration() is -1 for infinite).
            double seconds10 = inst.isInfiniteDuration() ? 600 : Math.min(inst.getDuration() / 20.0, 600);
            double weight = (inst.getAmplifier() + 1) * seconds10 * probability;
            effectScore += harmful ? -weight : weight;
        }
        //#if MC >= 1.21.2
        //$$ for (net.minecraft.world.item.consume_effects.ConsumeEffect ce : consumeEffects) {
        //$$     String what = null;
        //$$     if (ce instanceof net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect) what = "clears effects";
        //$$     else if (ce instanceof net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect) what = "removes effects";
        //$$     else if (ce instanceof net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect) what = "teleports";
        //$$     if (what != null) {
        //$$         if (!effects.isEmpty()) effects.append(", ");
        //$$         effects.append(what);
        //$$     }
        //$$ }
        //#else
        // 1.21.1 keeps a few food behaviours in the item class instead of the component.
        if (stack.getItem() instanceof HoneyBottleItem) {
            if (!effects.isEmpty()) effects.append(", ");
            effects.append("removes effects");
        } else if (stack.getItem() instanceof ChorusFruitItem) {
            if (!effects.isEmpty()) effects.append(", ");
            effects.append("teleports");
        }
        //#endif
        if (food.canAlwaysEat()) {
            if (!effects.isEmpty()) effects.append(", ");
            effects.append("always edible");
        }
        row.texts[F_EFFECTS] = effects.toString();
        // Sort key for the Effects column: buffs first, then neutral text (always edible…), then debuffs,
        // and food with no effect at all last – it is the uninteresting case when you sort by this column.
        row.values[F_EFFECTS] = effects.isEmpty() ? NO_EFFECT : effectScore;
        row.custom = isCustomClass(stack.getItem());
        table.rows.add(row);
    }

    // ------------------------------------------------------------------ armor

    /** Where the stack is worn: the mod's own answer first, then the vanilla Equipable interface. */
    private static EquipmentSlot wornSlot(ItemStack stack, Player player) {
        EquipmentSlot slot = null;
        try {
            slot = Platform.get().equipmentSlot(stack); // Forge/NeoForge item hook, null unless the mod overrides it
        } catch (Throwable ignored) {}
        if (slot == null) {
            //#if MC >= 1.21.2
            //$$ net.minecraft.world.item.equipment.Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            //$$ if (equippable != null) slot = equippable.slot();
            //#else
            Equipable equipable = Equipable.get(stack);
            if (equipable != null) slot = equipable.getEquipmentSlot();
            //#endif
        }
        if (slot == null && player != null) {
            try {
                slot = player.getEquipmentSlotForItem(stack);
            } catch (Throwable ignored) {}
        }
        return slot;
    }

    private static void indexArmor(Table table, ItemStack stack, String id, String modId, String modName, String name,
                                   Item.TooltipContext ctx, Player player) {
        EquipmentSlot slot = wornSlot(stack, player);
        if (slot == null) return;
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) return;

        AttributeAccumulator acc = new AttributeAccumulator();
        stack.forEachModifier(slot, acc::accept);

        Row row = new Row(stack, id, modId, modName, name, table.columns.size());
        row.texts[A_SLOT] = slotName(slot);
        row.values[A_SLOT] = slotOrder(slot);
        row.setFacet(slotName(slot));
        row.values[A_ARMOR] = acc.get(Attributes.ARMOR, 0);
        row.values[A_TOUGH] = acc.get(Attributes.ARMOR_TOUGHNESS, 0);
        row.values[A_KB] = acc.get(Attributes.KNOCKBACK_RESISTANCE, 0);
        row.values[A_DUR] = durability(stack);
        row.values[A_ENCH] = enchantability(stack);

        StringBuilder bonuses = new StringBuilder();
        double bonusScore = 0;
        int positiveBonuses = 0;
        for (Map.Entry<Holder<Attribute>, double[]> e : acc.map.entrySet()) {
            Attribute attr = e.getKey().value();
            if (attr == Attributes.ARMOR.value() || attr == Attributes.ARMOR_TOUGHNESS.value()
                    || attr == Attributes.KNOCKBACK_RESISTANCE.value()) continue;
            String label = describeBonus(attr, e.getValue());
            // Modern Industrialization's "Quantum Armor" attribute (also used by add-ons such as Extended
            // Industrialization): each point is a 25 % chance to cancel any damage, a full set makes you immune.
            // It has no armor points, so without this it would rank last although it is the strongest armor.
            //#if MC >= 1.21.11
            //$$ String attrPath = e.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            //#else
            String attrPath = e.getKey().unwrapKey().map(k -> k.location().getPath()).orElse("");
            //#endif
            if ("quantum_armor".equals(attrPath) && e.getValue()[0] > 1e-6) {
                row.values[A_ARMOR] = Double.POSITIVE_INFINITY;
                label = Math.round(e.getValue()[0] * 25) + "% ignore damage (full set: immune)";
            }
            if (label.isEmpty()) continue;
            if (!bonuses.isEmpty()) bonuses.append(", ");
            bonuses.append(label);
            bonusScore += 1;
            double[] v = e.getValue();
            if (v[0] > 1e-6 || (1 + v[1]) * v[2] - 1 > 1e-6) positiveBonuses++;
        }
        if (isGlider(stack, player)) {
            if (!bonuses.isEmpty()) bonuses.append(", ");
            bonuses.append("glider");
            bonusScore += 1;
            positiveBonuses++;
        }
        ItemEnchantments baked = stack.get(DataComponents.ENCHANTMENTS);
        if (baked != null && !baked.isEmpty()) {
            if (!bonuses.isEmpty()) bonuses.append(", ");
            bonuses.append("enchanted");
            bonusScore += 1;
            positiveBonuses++;
        }
        // Mob heads, carved pumpkins, modded hats…: wearable, but not armor (a head's "-100 % waypoint range" is
        // not a reason to rank it). They would only pad the "of X helmets".
        if (row.values[A_ARMOR] <= 0 && row.values[A_TOUGH] <= 0 && row.values[A_KB] <= 0 && positiveBonuses == 0) return;

        row.texts[A_BONUS] = bonuses.toString();
        row.values[A_BONUS] = bonusScore > 0 ? bonusScore : Double.NaN;

        row.custom = isCustomClass(stack.getItem());
        String notes = collectNotes(stack, ctx, player);
        if (row.custom && notes.isEmpty()) notes = "⚡";
        row.texts[A_NOTES] = notes;
        row.values[A_NOTES] = notes.isEmpty() ? Double.NaN : 1;
        table.rows.add(row);
    }

    private static boolean isGlider(ItemStack stack, Player player) {
        //#if MC >= 1.21.2
        //$$ if (stack.has(DataComponents.GLIDER)) return true;
        //#else
        if (stack.getItem() instanceof ElytraItem) return true;
        //#endif
        try {
            return player != null && Platform.get().canGlide(stack, player);
        } catch (Throwable t) {
            return false;
        }
    }

    private static double enchantability(ItemStack stack) {
        try {
            int v = Platform.get().enchantability(stack);
            if (v >= 0) return v;
        } catch (Throwable ignored) {}
        //#if MC >= 1.21.2
        //$$ net.minecraft.world.item.enchantment.Enchantable ench = stack.get(DataComponents.ENCHANTABLE);
        //$$ return ench != null ? ench.value() : 0;
        //#else
        return stack.getItem().getEnchantmentValue();
        //#endif
    }

    // ------------------------------------------------------------------ weapons & tools

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState HAY = Blocks.HAY_BLOCK.defaultBlockState();
    private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState();
    private static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();

    private static void indexWeapon(Table table, ItemStack stack, String id, String modId, String modName, String name,
                                    Item.TooltipContext ctx, Player player) {
        // Bows and crossbows have no melee stats, and some modded ones carry a tool component (Draconic Evolution)
        // that would file them under axes; they are not what the Weapons & Tools tab ranks.
        if (stack.getItem() instanceof ProjectileWeaponItem) return;
        // Modular armor that can also mine (again Draconic Evolution) belongs to the Armor tab, not to Pickaxes.
        EquipmentSlot worn = wornSlot(stack, player);
        if (worn == EquipmentSlot.HEAD || worn == EquipmentSlot.CHEST || worn == EquipmentSlot.LEGS || worn == EquipmentSlot.FEET) return;
        Tool tool = stack.get(DataComponents.TOOL);
        AttributeAccumulator acc = new AttributeAccumulator();
        stack.forEachModifier(EquipmentSlot.MAINHAND, acc::accept);
        boolean shield = canBlock(stack);
        //#if MC >= 1.21.11
        //$$ net.minecraft.world.item.component.KineticWeapon kinetic = stack.get(DataComponents.KINETIC_WEAPON);
        //$$ boolean spear = kinetic != null || stack.has(DataComponents.PIERCING_WEAPON);
        //#else
        boolean spear = false;
        //#endif
        boolean weaponClass = isSwordLike(stack) || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof MaceItem || spear;
        boolean hasWeaponStats = acc.has(Attributes.ATTACK_DAMAGE) || acc.has(Attributes.ATTACK_SPEED) || weaponClass;
        if (tool == null && !hasWeaponStats && !shield) return;

        Row row = new Row(stack, id, modId, modName, name, table.columns.size());

        // Player base: 1 damage, 4 attacks/second.
        double damage = acc.get(Attributes.ATTACK_DAMAGE, 1.0);
        double speed = acc.get(Attributes.ATTACK_SPEED, 4.0);
        boolean weapon = hasWeaponStats && (damage > 1.0001 || weaponClass);

        double stone = 1, log = 1, dirt = 1, hay = 1, leaves = 1, cobweb = 1;
        if (tool != null) {
            stone = stack.getDestroySpeed(STONE);
            log = stack.getDestroySpeed(LOG);
            dirt = stack.getDestroySpeed(DIRT);
            hay = stack.getDestroySpeed(HAY);
            leaves = stack.getDestroySpeed(LEAVES);
            cobweb = stack.getDestroySpeed(COBWEB);
        }
        double mining = Math.max(Math.max(stone, log), Math.max(dirt, hay));
        String type;
        if (mining > 1.0001) {
            if (stone >= log && stone >= dirt && stone >= hay) type = "Pickaxe";
            else if (log >= dirt && log >= hay) type = "Axe";
            else if (dirt >= hay) type = "Shovel";
            else type = "Hoe";
        } else if (weapon) {
            // Swords cut cobwebs fast and are "efficient" on leaves; that is still a weapon, not shears.
            type = cobweb > 1.0001 || isSwordLike(stack) ? "Sword" : "Weapon";
        } else if (leaves > 1.0001) {
            type = "Shears";
        } else if (shield) {
            type = "Shield";
        } else if (tool != null) {
            type = "Tool";
        } else {
            type = "Other";
        }

        row.texts[W_TYPE] = type;
        // Axes are a common weapon too, so they appear under "Weapons" (ranked by DPS) as well as under "Axes".
        switch (type) {
            case "Sword", "Weapon" -> row.setFacet("Weapons");
            case "Pickaxe" -> row.setFacet("Pickaxes");
            case "Axe" -> row.setFacet("Axes", "Weapons");
            case "Shovel" -> row.setFacet("Shovels");
            case "Hoe" -> row.setFacet("Hoes");
            default -> row.setFacet("Other");
        }
        row.values[W_TYPE] = switch (type) {
            case "Sword" -> 7; case "Weapon" -> 5; case "Pickaxe" -> 4; case "Axe" -> 3;
            case "Shovel" -> 2; case "Hoe" -> 1; default -> 0;
        };
        if (hasWeaponStats) {
            row.values[W_DMG] = damage;
            row.values[W_SPEED] = speed;
            row.values[W_DPS] = damage * speed;
        }
        if (tool != null && mining > 1.0001) {
            row.values[W_MINING] = mining;
        }
        Tier tier = tool != null && mining > 1.0001 ? tierOf(stack, tool) : null; // only real mining tools have a tier
        if (tier != null) {
            row.texts[W_TIER] = tier.label;
            row.values[W_TIER] = tier.level;
        }
        if (tool != null && mining > 1.0001) {
            row.values[W_SCORE] = toolScore(tier, mining, stack.isDamageableItem() ? stack.getMaxDamage() : 0);
        }
        row.values[W_DUR] = durability(stack);
        row.values[W_ENCH] = enchantability(stack);

        row.custom = isCustomClass(stack.getItem());
        StringBuilder notes = new StringBuilder();
        if (spear) {
            notes.append("charge attack");
            //#if MC >= 1.21.11
            //$$ if (kinetic != null && kinetic.damageMultiplier() > 0) notes.append(" ×").append(fmt(kinetic.damageMultiplier(), 1)).append(" speed");
            //#endif
        }
        if (shield) notes.append(notes.isEmpty() ? "" : " · ").append("blocks attacks");
        String extra = collectNotes(stack, ctx, player);
        if (!extra.isEmpty()) notes.append(notes.isEmpty() ? "" : " · ").append(extra);
        if (row.custom && notes.isEmpty()) notes.append("⚡");
        row.texts[W_NOTES] = notes.toString();
        row.values[W_NOTES] = notes.isEmpty() ? Double.NaN : 1;
        table.rows.add(row);
    }

    private static boolean canBlock(ItemStack stack) {
        try {
            Boolean b = Platform.get().canBlock(stack);
            if (b != null) return b;
        } catch (Throwable ignored) {}
        //#if MC >= 1.21.5
        //$$ return stack.has(DataComponents.BLOCKS_ATTACKS);
        //#else
        return stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
        //#endif
    }

    /** Swords: the sword class up to 1.21.4, the weapon component (which only swords and a few modded items get) after. */
    private static boolean isSwordLike(ItemStack stack) {
        //#if MC >= 1.21.5
        //$$ return stack.has(DataComponents.WEAPON) && stack.get(DataComponents.TOOL) != null;
        //#else
        return stack.getItem() instanceof SwordItem;
        //#endif
    }

    /** Max durability, or NaN ("–") for items that cannot break. */
    private static double durability(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() : Double.NaN;
    }

    /** label + sort level for the Tier column, and the number of ore levels the tool unlocks (1–4) for the score. */
    private record Tier(String label, double level, int oreLevels) {}

    /**
     * One number for "how good is this tool": ore levels unlocked (1–4, from the game's tool tags) × √mining speed
     * × log10(durability). Tier counts most because it is a hard gate, speed has diminishing returns, durability
     * counts least; unbreakable counts as 10 000. Vanilla: Netherite ≈ 39.7, Diamond ≈ 36.1, Iron ≈ 17.6,
     * Stone ≈ 8.5, Gold ≈ 5.2, Wood ≈ 2.5.
     */
    static double toolScore(Tier tier, double mining, int durability) {
        int levels = tier != null ? tier.oreLevels : 1;
        double dur = durability <= 0 ? 10_000 : Math.max(durability, 10);
        return levels * Math.sqrt(mining) * Math.log10(dur);
    }

    private static final Map<TagKey<?>, Tier> TIER_BY_TAG = new HashMap<>();

    static {
        // ore levels: 1 = stone/coal/copper ore, 2 = +iron/lapis, 3 = +diamond/gold/redstone/emerald, 4 = +obsidian/debris
        TIER_BY_TAG.put(BlockTags.INCORRECT_FOR_WOODEN_TOOL, new Tier("Wood", 0, 1));
        TIER_BY_TAG.put(BlockTags.INCORRECT_FOR_GOLD_TOOL, new Tier("Gold", 0.5, 1));
        TIER_BY_TAG.put(BlockTags.INCORRECT_FOR_STONE_TOOL, new Tier("Stone", 1, 2));
        TIER_BY_TAG.put(BlockTags.INCORRECT_FOR_IRON_TOOL, new Tier("Iron", 2, 3));
        TIER_BY_TAG.put(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, new Tier("Diamond", 3, 4));
        TIER_BY_TAG.put(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, new Tier("Netherite", 4, 4));
    }

    private static final Holder<Block> IRON_ORE_ENTRY = BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.IRON_ORE);
    private static final Holder<Block> DIAMOND_ORE_ENTRY = BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.DIAMOND_ORE);
    private static final Holder<Block> OBSIDIAN_ENTRY = BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.OBSIDIAN);

    private static Tier tierOf(ItemStack stack, Tool tool) {
        boolean sawNeverDropping = false, sawAlwaysDropping = false;
        for (Tool.Rule rule : tool.rules()) {
            if (rule.correctForDrops().isEmpty()) continue;
            if (rule.correctForDrops().get()) {
                sawAlwaysDropping = true;
                continue;
            }
            sawNeverDropping = true;
            // Vanilla-style tools carry an "incorrect for X tool" tag – read the tier straight from it.
            var tag = rule.blocks().unwrapKey();
            if (tag.isPresent()) {
                Tier t = TIER_BY_TAG.get(tag.get());
                if (t != null) return t;
            }
            // Modded tier tag: classify by which gate blocks it forbids. Works for every tool type,
            // because it looks at the forbidden set itself instead of probing with pickaxe blocks.
            HolderSet<Block> blocks = rule.blocks();
            try {
                if (blocks.contains(IRON_ORE_ENTRY)) return new Tier("Wood", 0, 1);
                if (blocks.contains(DIAMOND_ORE_ENTRY)) return new Tier("Stone", 1, 2);
                if (blocks.contains(OBSIDIAN_ENTRY)) return new Tier("Iron", 2, 3);
                return new Tier("Diamond+", 3, 4);
            } catch (Throwable ignored) {}
        }
        if (!sawNeverDropping && sawAlwaysDropping) {
            // Nothing is forbidden – the tool drops everything it can mine.
            return new Tier("Any", 4, 4);
        }
        // Last resort (no drop rules at all, or an unreadable one): probe what the tool actually harvests.
        try {
            if (stack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) return new Tier("Diamond+", 3, 4);
            if (stack.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) return new Tier("Iron", 2, 3);
            if (stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())) return new Tier("Stone", 1, 2);
            if (stack.isCorrectToolForDrops(STONE)) return new Tier("Wood", 0, 1);
        } catch (Throwable ignored) {}
        return null;
    }

    // ------------------------------------------------------------------ fuel

    private static void indexFuel(Table table, ItemStack stack, String id, String modId, String modName, String name, Level world) {
        int ticks = Platform.get().burnTicks(stack, world);
        if (ticks <= 0) return;
        Row row = new Row(stack, id, modId, modName, name, table.columns.size());
        row.values[U_BURN] = ticks / 20.0;
        row.values[U_SMELTS] = ticks / 200.0;
        row.values[U_STACK] = ticks / 200.0 * stack.getMaxStackSize();
        table.rows.add(row);
    }

    // ------------------------------------------------------------------ ranks

    /**
     * Fixed ranks. Each table gets a tab-wide rank (view "") by its default column, and each chip a rank by the
     * chip's natural column (view = facet name). The chip rankings are also what the tooltip line reports.
     * Rows with identical stats share a rank (1, 2, 2, 3) – the name never decides.
     */
    private static void computeRanks(Table food, Table armor, Table weapons, Table fuel) {
        rank(food, food.rows, "", "food", food.defaultSort);
        rank(fuel, fuel.rows, "", "fuels", fuel.defaultSort);

        rank(armor, armor.rows, "", null, armor.defaultSort);
        for (String slot : armor.facets()) {
            rank(armor, inFacet(armor, slot), slot, slotGroupKey(slot), armor.sortFor(slot));
        }

        rank(weapons, weapons.rows, "", null, weapons.defaultSort);
        for (String facet : weapons.facets()) {
            rank(weapons, inFacet(weapons, facet), facet, facet.toLowerCase(Locale.ROOT), weapons.sortFor(facet));
        }
    }

    private static List<Row> inFacet(Table table, String facet) {
        List<Row> out = new ArrayList<>();
        for (Row r : table.rows) if (r.facets.contains(facet)) out.add(r);
        return out;
    }

    /**
     * Sorts {@code rows} by {@code column} (best first, full stats tie-break) and stores the dense rank in
     * {@code view}; when {@code groupKey} is given, the same rank is also registered for the tooltip line.
     * Rows without a value in the sort column are not ranked (a sword has no tool score).
     */
    private static void rank(Table table, List<Row> rows, String view, String groupKey, int column) {
        List<Row> sorted = new ArrayList<>();
        for (Row r : rows) if (r.hasValue(column)) sorted.add(r);
        Comparator<Row> stats = table.statsComparator(column, true);
        sorted.sort(stats.thenComparing(r -> r.nameLower));
        // Dense ranking: a group of items with identical stats takes one place (1, 2, 2, 3), so the twenty
        // boats are "#6" together and the next fuel is "#7". The total is the number of places, not of items.
        int[] places = new int[sorted.size()];
        int rank = 0;
        Row prev = null;
        for (int i = 0; i < sorted.size(); i++) {
            Row r = sorted.get(i);
            if (prev == null || stats.compare(prev, r) != 0) rank++;
            places[i] = rank;
            prev = r;
        }
        int total = rank;
        for (int i = 0; i < sorted.size(); i++) {
            Row r = sorted.get(i);
            r.ranks.put(view, places[i]);
            if (groupKey != null) {
                RANKS.computeIfAbsent(r.item, k -> new ArrayList<>(2)).add(new RankEntry(groupKey, places[i], total));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Sums attribute modifiers the way the game applies them: (base + adds) * (1 + multBase) * Π(1 + multTotal). */
    private static final class AttributeAccumulator {
        final Map<Holder<Attribute>, double[]> map = new LinkedHashMap<>();

        void accept(Holder<Attribute> attribute, AttributeModifier modifier) {
            double[] v = map.computeIfAbsent(attribute, k -> new double[]{0, 0, 1});
            switch (modifier.operation()) {
                case ADD_VALUE -> v[0] += modifier.amount();
                case ADD_MULTIPLIED_BASE -> v[1] += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> v[2] *= (1 + modifier.amount());
            }
        }

        boolean has(Holder<Attribute> attribute) {
            for (Holder<Attribute> k : map.keySet()) {
                if (k.value() == attribute.value()) return true;
            }
            return false;
        }

        double get(Holder<Attribute> attribute, double base) {
            for (Map.Entry<Holder<Attribute>, double[]> e : map.entrySet()) {
                if (e.getKey().value() == attribute.value()) {
                    double[] v = e.getValue();
                    return (base + v[0]) * (1 + v[1]) * v[2];
                }
            }
            return base;
        }
    }

    private static String describeBonus(Attribute attr, double[] v) {
        String label = Language.getInstance().getOrDefault(attr.getDescriptionId());
        StringBuilder sb = new StringBuilder();
        if (Math.abs(v[0]) > 1e-6) {
            sb.append(v[0] > 0 ? "+" : "").append(fmt(v[0], 1)).append(' ').append(label);
        }
        double mult = (1 + v[1]) * v[2] - 1;
        if (Math.abs(mult) > 1e-6) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(mult > 0 ? "+" : "").append(Math.round(mult * 100)).append("% ").append(label);
        }
        return sb.toString();
    }

    private static final String[] SKIP_KEY_PREFIXES = {
            "attribute.", "item.modifiers", "item.durability", "item.unbreakable", "enchantment.", "potion.", "effect.",
            "item.components", "item.nbt_tags", "item.disabled", "item.op_warning", "bestinpack.", "item.minecraft.",
            "block.minecraft.", "container.", "gui.", "key."
    };

    /** Mod-written tooltip lines (abilities, lore) – everything that is not a standard stat line. */
    private static String collectNotes(ItemStack stack, Item.TooltipContext ctx, Player player) {
        List<Component> lines;
        try {
            lines = stack.getTooltipLines(ctx, player, TooltipFlag.NORMAL);
        } catch (Throwable t) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (int i = 1; i < lines.size() && kept < 3; i++) {
            Component line = lines.get(i);
            if (hasSkippedKey(line)) continue;
            String s = line.getString().trim();
            if (s.isEmpty() || isInstructionLine(s)) continue;
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append(s);
            kept++;
        }
        return sb.toString();
    }

    private static final java.util.regex.Pattern INSTRUCTION = java.util.regex.Pattern.compile(
            "^(press|hold)\\b|\\[(shift|ctrl|control|alt)]|\\b(hold|press|holding|pressing)\\b[^.]{0,20}\\b(shift|ctrl|control|alt)\\b"
                    + "|\\b(shift|ctrl|control|alt)\\b[^.]{0,12}\\b(for|to show|to view|to see|for more|for details|for info)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** "Hold [SHIFT] to view", "Press Ctrl for details" – UI hints, not information about the item. */
    private static boolean isInstructionLine(String s) {
        return INSTRUCTION.matcher(s).find();
    }

    private static boolean hasSkippedKey(Component text) {
        if (text.getContents() instanceof TranslatableContents t) {
            String key = t.getKey();
            for (String p : SKIP_KEY_PREFIXES) if (key.startsWith(p)) return true;
        }
        for (Component sibling : text.getSiblings()) {
            if (hasSkippedKey(sibling)) return true;
        }
        return false;
    }

    private static boolean isCustomClass(Item item) {
        String cls = item.getClass().getName();
        return !cls.startsWith("net.minecraft.");
    }

    private static final Map<String, String> MOD_NAMES = new HashMap<>();

    public static String modDisplayName(String modId) {
        return MOD_NAMES.computeIfAbsent(modId, id -> Platform.get().modName(id));
    }

    static String slotName(EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD) return "Head";
        if (slot == EquipmentSlot.CHEST) return "Chest";
        if (slot == EquipmentSlot.LEGS) return "Legs";
        if (slot == EquipmentSlot.FEET) return "Feet";
        return slot.getName();
    }

    static int slotOrder(EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD) return 4;
        if (slot == EquipmentSlot.CHEST) return 3;
        if (slot == EquipmentSlot.LEGS) return 2;
        if (slot == EquipmentSlot.FEET) return 1;
        return 0;
    }

    static String slotGroupKey(String facet) {
        return switch (facet) {
            case "Head" -> "helmets";
            case "Chest" -> "chestplates";
            case "Legs" -> "leggings";
            default -> "boots";
        };
    }

    public static String fmt(double v, int decimals) {
        if (Double.isNaN(v)) return "";
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "-∞";
        if (decimals == 0 || Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf(Math.round(v));
        String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
        // trim trailing zeros
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Short form for big numbers so they fit a table cell: 874.8k, 70.9M, 4.7B. Below 100 000 it is {@link #fmt}. */
    public static String fmtCompact(double v, int decimals) {
        if (Double.isNaN(v)) return "";
        if (Double.isInfinite(v) || Math.abs(v) < 100_000) return fmt(v, decimals);
        String[] units = {"k", "M", "B", "T"};
        double scaled = v;
        int u = -1;
        while (Math.abs(scaled) >= 1000 && u < units.length - 1) {
            scaled /= 1000;
            u++;
        }
        // 999 950 would round to "1000k" – show it as "1M" instead
        if (Math.abs(Math.round(scaled * 10) / 10.0) >= 1000 && u < units.length - 1) {
            scaled /= 1000;
            u++;
        }
        return fmt(scaled, 1) + units[u];
    }

    /** Full value with thousands separators, for the tooltip of a shortened cell: 107 374 182.4 */
    public static String fmtGrouped(double v, int decimals) {
        if (Double.isNaN(v)) return "";
        java.text.DecimalFormatSymbols sym = java.text.DecimalFormatSymbols.getInstance(Locale.ROOT);
        sym.setGroupingSeparator(' ');
        java.text.DecimalFormat f = new java.text.DecimalFormat(decimals > 0 ? "#,##0." + "#".repeat(decimals) : "#,##0", sym);
        return f.format(v);
    }

    /** Self-test only: adds a row with huge numbers to a table, to check that wide values still fit their column. */
    public static void addHugeRowForTest(Category category) {
        Table t = TABLES.get(category);
        if (t == null || t.rows.isEmpty()) return;
        Row src = t.rows.get(0);
        Row row = new Row(src.stack, src.id + "_huge_test", src.modId, src.modName, "Huge Test Value", t.columns.size());
        double[] huge = {107_374_182.4, 10_737_418.24, 687_194_767, 4_723_920_000.0};
        int n = 0;
        for (int i = 0; i < t.columns.size(); i++) {
            if (t.columns.get(i).kind() == Column.Kind.NUMBER) row.values[i] = huge[Math.min(n++, huge.length - 1)];
        }
        t.rows.add(row);
    }

    private static String fmtSeconds(double seconds) {
        if (seconds >= 60) {
            int total = (int) Math.round(seconds);
            int m = total / 60, s = total % 60;
            return s == 0 ? m + "m" : m + "m" + s + "s";
        }
        return fmt(seconds, 1) + "s";
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
