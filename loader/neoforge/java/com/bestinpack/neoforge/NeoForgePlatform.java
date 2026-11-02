package com.bestinpack.neoforge;

import com.bestinpack.platform.Platform;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
//#if MC < 1.21.5
import net.neoforged.neoforge.common.ItemAbilities;
//#endif

import java.nio.file.Path;

final class NeoForgePlatform implements Platform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String modName(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(modId);
    }

    @Override
    public int burnTicks(ItemStack stack, Level level) {
        //#if MC >= 26.3
        //$$ return Math.max(0, com.bestinpack.index.Fuel.burnTicks(stack));
        //#else
        // NeoForge answers per stack (data map + FurnaceFuelBurnTimeEvent).
        //#if MC >= 1.21.2
        //$$ if (level == null) return 0;
        //$$ return stack.getBurnTime(RecipeType.SMELTING, level.fuelValues());
        //#else
        return stack.getBurnTime(RecipeType.SMELTING);
        //#endif
        //#endif
    }

    @Override
    public boolean hasFuelData(Level level) {
        //#if MC >= 26.3
        //$$ return level != null && com.bestinpack.index.Fuel.available();
        //#else
        //#if MC >= 1.21.2
        //$$ return level != null;
        //#else
        return true;
        //#endif
        //#endif
    }

    @Override
    public EquipmentSlot equipmentSlot(ItemStack stack) {
        //#if MC >= 1.21.2
        //$$ return null;
        //#else
        return stack.getEquipmentSlot();
        //#endif
    }

    @Override
    public Boolean canBlock(ItemStack stack) {
        //#if MC >= 1.21.5
        //$$ return null; // the blocks_attacks component is read by the shared code
        //#else
        return stack.canPerformAction(ItemAbilities.SHIELD_BLOCK);
        //#endif
    }

    @Override
    public boolean canGlide(ItemStack stack, Player player) {
        //#if MC >= 1.21.2
        //$$ return false;
        //#else
        return stack.canElytraFly(player);
        //#endif
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
        return "neoforge";
    }
}
