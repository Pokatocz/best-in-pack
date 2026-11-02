package com.bestinpack.fabric;

import com.bestinpack.fabric.mixin.AbstractContainerScreenAccessor;
import com.bestinpack.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.nio.file.Path;

final class FabricPlatform implements Platform {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public String modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(c -> c.getMetadata().getName()).orElse(modId);
    }

    @Override
    public int burnTicks(ItemStack stack, Level level) {
        //#if MC >= 26.3
        //$$ return Math.max(0, com.bestinpack.index.Fuel.burnTicks(stack));
        //#else
        //#if MC >= 1.21.2
        //$$ // The fuel table belongs to the world since 1.21.2 (Fabric's FuelRegistryEvents add to it).
        //$$ if (level == null) return 0;
        //$$ return level.fuelValues().burnDuration(stack);
        //#else
        Integer t = net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.get(stack.getItem());
        return t == null ? 0 : t;
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
    public Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        return ((AbstractContainerScreenAccessor) screen).bestinpack$getHoveredSlot();
    }

    @Override
    public String loader() {
        return "fabric";
    }
}
