package com.bestinpack.compat;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
//#if MC >= 1.21.11
//$$ import net.minecraft.resources.Identifier;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif

/**
 * JEI plugin – grabs the runtime so rows can open JEI's recipe screen, and answers what JEI hides. JEI finds it
 * through {@code @JeiPlugin} (Forge, NeoForge) or the {@code jei_mod_plugin} entry point (Fabric); Best in Pack itself
 * loads it by name only when JEI is installed.
 */
@JeiPlugin
public class JeiBridge implements IModPlugin, Viewer {
    private static IJeiRuntime runtime;

    //#if MC >= 1.21.11
    //$$ @Override
    //$$ public Identifier getPluginUid() {
    //$$     return Identifier.fromNamespaceAndPath("bestinpack", "jei");
    //$$ }
    //#else
    @Override
    public ResourceLocation getPluginUid() {
        return com.bestinpack.util.Ids.of("bestinpack", "jei");
    }
    //#endif

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        // Items hidden in JEI (edit mode, blacklist, KubeJS) are hidden here too – rebuild once JEI knows them,
        // and again whenever the player hides or shows something.
        com.bestinpack.index.ItemIndex.markDirty();
        //#if MC >= 1.21 || MC < 1.20.2
        try {
            jeiRuntime.getJeiHelpers().getIngredientVisibility().registerListener(new mezz.jei.api.runtime.IIngredientVisibility.IListener() {
                @Override
                public <V> void onIngredientVisibilityChanged(mezz.jei.api.ingredients.ITypedIngredient<V> ingredient, boolean visible) {
                    com.bestinpack.index.ItemIndex.markDirty();
                }
            });
        } catch (Throwable ignored) {
            // older JEI without visibility listeners
        }
        //#else
        //$$ // JEI 16 – 18 (1.20.2 – 1.20.6) only have the visibility API in some builds: use it when it is there.
        //$$ visibility = null;
        //$$ try {
        //$$     Object helpers = jeiRuntime.getJeiHelpers();
        //$$     Object vis = helpers.getClass().getMethod("getIngredientVisibility").invoke(helpers);
        //$$     for (java.lang.reflect.Method m : vis.getClass().getMethods()) {
        //$$         if (m.getName().equals("isIngredientVisible") && m.getParameterCount() == 2) isVisible = m;
        //$$     }
        //$$     if (isVisible != null) visibility = vis;
        //$$ } catch (Throwable ignored) {
        //$$     // this JEI cannot tell what is hidden
        //$$ }
        //#endif
    }

    //#if MC < 1.21 && MC >= 1.20.2
    //$$ private static Object visibility;
    //$$ private static java.lang.reflect.Method isVisible;
    //#endif

    @Override
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
        IJeiRuntime r = runtime;
        //#if MC >= 1.21 || MC < 1.20.2
        return r != null && !r.getJeiHelpers().getIngredientVisibility().isIngredientVisible(VanillaTypes.ITEM_STACK, stack);
        //#else
        //$$ if (r == null || visibility == null) return false;
        //$$ try {
        //$$     return !(Boolean) isVisible.invoke(visibility, VanillaTypes.ITEM_STACK, stack);
        //$$ } catch (Throwable t) {
        //$$     return false;
        //$$ }
        //#endif
    }

    @Override
    public boolean matchesRecipeKey(InputConstants.Key key, int keyCode, int scanCode) {
        IJeiRuntime r = runtime;
        return r != null && r.getKeyMappings().getShowRecipe().isActiveAndMatches(key);
    }

    @Override
    public boolean matchesUsesKey(InputConstants.Key key, int keyCode, int scanCode) {
        IJeiRuntime r = runtime;
        return r != null && r.getKeyMappings().getShowUses().isActiveAndMatches(key);
    }

    @Override
    public Component recipeKeyName() {
        IJeiRuntime r = runtime;
        return r == null ? null : r.getKeyMappings().getShowRecipe().getTranslatedKeyMessage();
    }

    @Override
    public Component usesKeyName() {
        IJeiRuntime r = runtime;
        return r == null ? null : r.getKeyMappings().getShowUses().getTranslatedKeyMessage();
    }

    @Override
    public ItemStack hovered() {
        IJeiRuntime r = runtime;
        if (r == null) return null;
        ItemStack s = r.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (s == null) s = r.getBookmarkOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (s == null) s = r.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK).orElse(null);
        return s;
    }

    @Override
    public boolean typing() {
        IJeiRuntime r = runtime;
        return r != null && r.getIngredientListOverlay().hasKeyboardFocus();
    }

    @Override
    public boolean show(ItemStack stack, boolean usages) {
        IJeiRuntime r = runtime;
        if (r == null) return false;
        IFocus<ItemStack> focus = r.getJeiHelpers().getFocusFactory().createFocus(
                usages ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
        r.getRecipesGui().show(focus);
        return true;
    }

    /** Self-test only: hide or show an item the way JEI's edit mode does (config file hide). */
    public static void setHiddenForTest(ItemStack stack, boolean hidden) {
        IJeiRuntime r = runtime;
        if (r == null) return;
        var typed = r.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack).orElseThrow();
        if (hidden) r.getEditModeConfig().hideIngredientUsingConfigFile(typed, mezz.jei.api.runtime.IEditModeConfig.HideMode.SINGLE);
        else r.getEditModeConfig().showIngredientUsingConfigFile(typed, mezz.jei.api.runtime.IEditModeConfig.HideMode.SINGLE);
    }
}
