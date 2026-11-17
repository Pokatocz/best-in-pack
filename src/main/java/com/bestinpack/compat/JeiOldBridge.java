//#if MC < 1.20
package com.bestinpack.compat;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JEI plugin for Minecraft 1.18 – 1.19.4, where players run anything from JEI 9.0 to JEI 13 and almost every one of
 * those changed the API this needs (how a focus is made, the name of the item type, where the list of hidden
 * ingredients and the key bindings are). So only the plugin interface itself is used directly; the rest is looked up
 * by name on JEI's public interfaces, newest form first, and what an old JEI cannot answer falls back to a default
 * (nothing hidden, R and U).
 */
@JeiPlugin
public class JeiOldBridge implements IModPlugin, Viewer {
    private static final String API = "mezz.jei.api.";
    private static final String RUNTIME = API + "runtime.IJeiRuntime";
    private static final String VISIBILITY = API + "runtime.IIngredientVisibility";

    private static Object runtime;
    /** VanillaTypes.ITEM_STACK (JEI 9.5 and newer) or VanillaTypes.ITEM (JEI 9.0 – 9.4, removed in JEI 11). */
    private static Object itemType;
    /** JEI's list of what is hidden (JEI 9.4 and newer), else null. */
    private static Object visibility;

    @Override
    public ResourceLocation getPluginUid() {
        return com.bestinpack.util.Ids.of("bestinpack", "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        itemType = constant(API + "constants.VanillaTypes", "ITEM_STACK");
        if (itemType == null) itemType = constant(API + "constants.VanillaTypes", "ITEM");
        visibility = null;
        try {
            visibility = call(RUNTIME, jeiRuntime, "getIngredientVisibility");
        } catch (Throwable ignored) {
            // JEI 9.0 – 9.3 cannot tell what is hidden
        }
        // Items hidden in JEI (edit mode, blacklist, KubeJS) are hidden here too – rebuild once JEI knows them,
        // and again whenever the player hides or shows something (JEI 11.5 and newer tell when that happens).
        com.bestinpack.index.ItemIndex.markDirty();
        if (visibility != null) {
            try {
                Class<?> listener = Class.forName(VISIBILITY + "$IListener");
                Object proxy = java.lang.reflect.Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (self, method, args) -> {
                    switch (method.getName()) {
                        case "onIngredientVisibilityChanged" -> com.bestinpack.index.ItemIndex.markDirty();
                        case "hashCode" -> { return System.identityHashCode(self); }
                        case "equals" -> { return self == args[0]; }
                        case "toString" -> { return "Best in Pack visibility listener"; }
                        default -> {}
                    }
                    return null;
                });
                call(VISIBILITY, visibility, "registerListener", proxy);
            } catch (Throwable ignored) {
                // an older JEI without visibility listeners: what is hidden while playing counts from the next join
            }
        }
    }

    /** Part of the plugin interface from JEI 11.5 on; older JEI never calls it. */
    public void onRuntimeUnavailable() {
        runtime = null;
        com.bestinpack.index.ItemIndex.markDirty();
    }

    @Override
    public String name() {
        return "JEI";
    }

    @Override
    public boolean ready() {
        return runtime != null;
    }

    @Override
    public boolean isHidden(ItemStack stack) {
        Object vis = visibility;
        if (runtime == null || vis == null || itemType == null) return false;
        try {
            return !(Boolean) call(VISIBILITY, vis, "isIngredientVisible", itemType, stack);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ keys

    /** JEI's own key mapping object (JEI 11 and newer), else null. */
    private static Object jeiKey(boolean uses) {
        Object r = runtime;
        if (r == null) return null;
        try {
            Object mappings = call(RUNTIME, r, "getKeyMappings");
            return call(API + "runtime.IJeiKeyMappings", mappings, uses ? "getShowUses" : "getShowRecipe");
        } catch (Throwable t) {
            return null;
        }
    }

    /** JEI 9 and 10 keep their keys in the game's controls: found there by name. */
    private static KeyMapping vanillaKey(boolean uses) {
        String name = uses ? "key.jei.showUses" : "key.jei.showRecipe";
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            if (name.equals(k.getName())) return k;
        }
        return null;
    }

    private static boolean matches(boolean uses, InputConstants.Key key, int keyCode, int scanCode) {
        Object jei = jeiKey(uses);
        if (jei != null) {
            try {
                return (Boolean) call(API + "runtime.IJeiKeyMapping", jei, "isActiveAndMatches", key);
            } catch (Throwable ignored) {
                // fall through to the game's controls
            }
        }
        KeyMapping k = vanillaKey(uses);
        if (k != null) return com.bestinpack.client.Keys.matches(k, keyCode, scanCode);
        return keyCode == (uses ? InputConstants.KEY_U : InputConstants.KEY_R);
    }

    private static Component keyName(boolean uses) {
        Object jei = jeiKey(uses);
        if (jei != null) {
            try {
                return (Component) call(API + "runtime.IJeiKeyMapping", jei, "getTranslatedKeyMessage");
            } catch (Throwable ignored) {
                // fall through to the game's controls
            }
        }
        KeyMapping k = vanillaKey(uses);
        return k == null ? null : k.getTranslatedKeyMessage();
    }

    @Override
    public boolean matchesRecipeKey(InputConstants.Key key, int keyCode, int scanCode) {
        return matches(false, key, keyCode, scanCode);
    }

    @Override
    public boolean matchesUsesKey(InputConstants.Key key, int keyCode, int scanCode) {
        return matches(true, key, keyCode, scanCode);
    }

    @Override
    public Component recipeKeyName() {
        return keyName(false);
    }

    @Override
    public Component usesKeyName() {
        return keyName(true);
    }

    // ------------------------------------------------------------------ overlays and recipes

    /** An ingredient-under-the-mouse answer: the stack itself, or an Optional of it from JEI 11 on. */
    private static ItemStack stackOf(Object answer) {
        if (answer instanceof Optional<?> o) answer = o.orElse(null);
        return answer instanceof ItemStack s ? s : null;
    }

    @Override
    public ItemStack hovered() {
        Object r = runtime;
        if (r == null || itemType == null) return null;
        try {
            ItemStack s = stackOf(call(API + "runtime.IIngredientListOverlay", call(RUNTIME, r, "getIngredientListOverlay"), "getIngredientUnderMouse", itemType));
            if (s == null) s = stackOf(call(API + "runtime.IBookmarkOverlay", call(RUNTIME, r, "getBookmarkOverlay"), "getIngredientUnderMouse", itemType));
            if (s == null) s = stackOf(call(API + "runtime.IRecipesGui", call(RUNTIME, r, "getRecipesGui"), "getIngredientUnderMouse", itemType));
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean typing() {
        Object r = runtime;
        if (r == null) return false;
        try {
            return (Boolean) call(API + "runtime.IIngredientListOverlay", call(RUNTIME, r, "getIngredientListOverlay"), "hasKeyboardFocus");
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean show(ItemStack stack, boolean usages) {
        Object r = runtime;
        if (r == null || itemType == null) return false;
        try {
            Object focus = null;
            Object role = constant(API + "recipe.RecipeIngredientRole", usages ? "INPUT" : "OUTPUT");
            if (role != null) {
                try {   // JEI 9.5 and newer
                    Object factory = call(API + "helpers.IJeiHelpers", call(RUNTIME, r, "getJeiHelpers"), "getFocusFactory");
                    focus = call(API + "recipe.IFocusFactory", factory, "createFocus", role, itemType, stack);
                } catch (NoSuchMethodException | ClassNotFoundException e) {
                    try {   // JEI 9.4
                        focus = call(RUNTIME, r, "createFocus", role, itemType, stack);
                    } catch (NoSuchMethodException | ClassNotFoundException ignored) {
                        // JEI 9.0 – 9.3, below
                    }
                }
            }
            if (focus == null) {
                Object mode = constant(API + "recipe.IFocus$Mode", usages ? "INPUT" : "OUTPUT");
                focus = call(API + "recipe.IRecipeManager", call(RUNTIME, r, "getRecipeManager"), "createFocus", mode, stack);
            }
            call(API + "runtime.IRecipesGui", call(RUNTIME, r, "getRecipesGui"), "show", focus);
            return true;
        } catch (Throwable t) {
            com.bestinpack.BestInPack.LOGGER.warn("Could not open JEI", t);
            return false;
        }
    }

    /** Self-test only: hide or show an item the way JEI's edit mode does (JEI 11.5 and newer). */
    public static void setHiddenForTest(ItemStack stack, boolean hidden) throws Exception {
        Object r = runtime;
        if (r == null) return;
        Object typed = ((Optional<?>) call(API + "runtime.IIngredientManager", call(RUNTIME, r, "getIngredientManager"), "createTypedIngredient", itemType, stack)).orElseThrow();
        Object single = constant(API + "runtime.IEditModeConfig$HideMode", "SINGLE");
        call(API + "runtime.IEditModeConfig", call(RUNTIME, r, "getEditModeConfig"),
                hidden ? "hideIngredientUsingConfigFile" : "showIngredientUsingConfigFile", typed, single);
    }

    // ------------------------------------------------------------------ by name

    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    /** A public static field (a constant or an enum value) of a JEI class, or null when this JEI has none of that name. */
    private static Object constant(String className, String field) {
        try {
            return Class.forName(className).getField(field).get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Calls a method of one of JEI's public interfaces on an object JEI handed out. The method is taken from the
     * interface (not from the object's class, which need not be public) and chosen by name and by what the
     * arguments are, since several of them are overloaded.
     */
    private static Object call(String iface, Object target, String name, Object... args) throws Exception {
        if (target == null) throw new NoSuchMethodException(iface + "." + name + " on null");
        StringBuilder key = new StringBuilder(iface).append('#').append(name);
        for (Object a : args) key.append(',').append(a == null ? "null" : a.getClass().getName());
        Method m = METHODS.get(key.toString());
        if (m == null) {
            for (Method c : Class.forName(iface).getMethods()) {
                if (!c.getName().equals(name) || c.getParameterCount() != args.length || Modifier.isStatic(c.getModifiers())) continue;
                Class<?>[] types = c.getParameterTypes();
                boolean fits = true;
                for (int i = 0; i < args.length && fits; i++) fits = args[i] == null || types[i].isInstance(args[i]);
                if (fits) { m = c; break; }
            }
            if (m == null) throw new NoSuchMethodException(key.toString());
            METHODS.put(key.toString(), m);
        }
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }
}
//#endif
