//#if MC >= 26.3
//$$ package com.bestinpack.index;
//$$
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.core.BlockPos;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.component.CookingFuel;
//$$ import net.minecraft.world.level.block.Blocks;
//$$ import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
//$$ import net.minecraft.world.level.storage.loot.LootContext;
//$$ import net.minecraft.world.level.storage.loot.LootParams;
//$$ import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
//$$ import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
//$$ import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
//$$ import net.minecraft.world.phys.Vec3;
//$$
//$$ import java.util.Optional;
//$$
//$$ /**
//$$  * 26.3 moved burn times into the cooking_fuel item component. Most values are data-driven formulas that need a loot
//$$  * context (a fast furnace burns fuel twice as fast), which only exists on the server – so in single player they are
//$$  * evaluated for a plain furnace on the integrated server, on a server only the constant ones can be read.
//$$  */
//$$ public final class Fuel {
//$$     private Fuel() {}
//$$
//$$     private static LootContext context;
//$$     private static ServerLevel contextLevel;
//$$
//$$     /** Burn time in ticks, 0 for no fuel, -1 when it cannot be worked out here. */
//$$     public static int burnTicks(ItemStack stack) {
//$$         CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
//$$         if (fuel == null) return 0;
//$$         if (fuel.burnTime() instanceof ResolvableInt.Constant c) return c.value();
//$$         LootContext ctx = context();
//$$         if (ctx == null) return -1;
//$$         try {
//$$             return fuel.burnTime().get(ctx, 0);
//$$         } catch (RuntimeException e) {
//$$             return -1;
//$$         }
//$$     }
//$$
//$$     /** True when formula-based burn times can be evaluated (single player). */
//$$     public static boolean available() {
//$$         return context() != null;
//$$     }
//$$
//$$     private static LootContext context() {
//$$         Minecraft client = Minecraft.getInstance();
//$$         ServerLevel level = client.getSingleplayerServer() != null ? client.getSingleplayerServer().overworld() : null;
//$$         if (level == null) return null;
//$$         if (context == null || contextLevel != level) {
//$$             // The same parameters a furnace uses (BaseContainerBlockEntity.getLootContext).
//$$             FurnaceBlockEntity furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
//$$             LootParams params = new LootParams.Builder(level)
//$$                     .withParameter(LootContextParams.BLOCK_STATE, Blocks.FURNACE.defaultBlockState())
//$$                     .withParameter(LootContextParams.BLOCK_ENTITY, furnace)
//$$                     .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(BlockPos.ZERO))
//$$                     .withParameter(LootContextParams.CONTAINER, furnace)
//$$                     .create(LootContextParamSets.CONTAINER_PROCESS);
//$$             context = new LootContext.Builder(params).create(Optional.empty());
//$$             contextLevel = level;
//$$         }
//$$         return context;
//$$     }
//$$ }
//#endif
