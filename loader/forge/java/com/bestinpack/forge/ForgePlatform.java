package com.bestinpack.forge;

import com.bestinpack.platform.Platform;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
//#if MC < 1.21.5
import net.minecraftforge.common.ToolActions;
//#endif
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

final class ForgePlatform implements Platform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        //#if MC >= 26.1
        //$$ return ModList.isLoaded(modId);
        //#else
        return ModList.get().isLoaded(modId);
        //#endif
    }

    @Override
    public String modName(String modId) {
        //#if MC >= 26.1
        //$$ return ModList.getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(modId);
        //#else
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(modId);
        //#endif
    }

    @Override
    public int burnTicks(ItemStack stack, Level level) {
        //#if MC >= 1.21.2
        //$$ // Item override first (-1 = not overridden), then the vanilla/data-driven value, then Forge's fuel event.
        //$$ int base = stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
        //$$ if (base < 0) {
            //#if MC >= 26.3
            //$$ base = Math.max(0, com.bestinpack.index.Fuel.burnTicks(stack));
            //#else
            //$$ base = level == null ? 0 : level.fuelValues().burnDuration(stack);
            //#endif
        //$$ }
        //$$ return Math.max(0, net.minecraftforge.event.ForgeEventFactory.getItemBurnTime(stack, base, RecipeType.SMELTING));
        //#else
        return net.minecraftforge.common.ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
        //#endif
    }

    @Override
    public boolean hasFuelData(Level level) {
        //#if MC >= 26.3
        //$$ return level != null && com.bestinpack.index.Fuel.available();
        //#elif MC >= 1.21.2
        //$$ return level != null;
        //#else
        return true;
        //#endif
    }

    @Override
    public EquipmentSlot equipmentSlot(ItemStack stack) {
        return stack.getEquipmentSlot(); // Forge item hook, null unless the mod overrides it
    }

    //#if MC < 1.20.5
    //$$ @Override
    //$$ public net.minecraft.world.food.FoodProperties foodProperties(ItemStack stack, Player player) {
    //$$     return stack.getFoodProperties(player); // Forge: items may answer per stack and per eater
    //$$ }
    //$$
    //#endif
    @Override
    public Boolean canBlock(ItemStack stack) {
        //#if MC >= 1.21.5
        //$$ return null; // the blocks_attacks component is read by the shared code
        //#else
        return stack.canPerformAction(ToolActions.SHIELD_BLOCK);
        //#endif
    }

    @Override
    public boolean canGlide(ItemStack stack, Player player) {
        return stack.canElytraFly(player);
    }

    @Override
    public int enchantability(ItemStack stack) {
        //#if MC >= 1.21.2
        //$$ return -1; // the enchantable component is read by the shared code
        //#else
        return stack.getEnchantmentValue();
        //#endif
    }

    @Override
    public Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        //#if MC >= 26.3
        //$$ return com.bestinpack.compat.HoveredItem.hoveredSlotByField(screen);
        //#else
        return screen.getSlotUnderMouse();
        //#endif
    }

    @Override
    public String loader() {
        return "forge";
    }
}
