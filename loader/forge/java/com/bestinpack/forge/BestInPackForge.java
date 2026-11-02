package com.bestinpack.forge;

import com.bestinpack.BestInPack;
import com.bestinpack.client.BestInPackClient;
import com.bestinpack.client.gui.OpenFolderScreen;
import com.bestinpack.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge entry point (client only). Wires Forge's events to the shared {@link BestInPackClient}. */
@Mod(BestInPack.MOD_ID)
public class BestInPackForge {
    // No-arg on purpose: Forge 51 (1.21) only accepts this one, and every later Forge still does.
    public BestInPackForge() {
        if (!isClient()) return;
        Platform.set(new ForgePlatform());
        BestInPack.LOGGER.info("Best in Pack loaded.");

        InputConstants.Key b = BestInPackClient.defaultOpenKey(); // B unless the config says otherwise
        //#if MC >= 1.21.9
        //$$ KeyMapping.Category category = KeyMapping.Category.register(com.bestinpack.util.Ids.of(BestInPack.MOD_ID, "general"));
        //$$ BestInPackClient.openKey = new KeyMapping(BestInPackClient.KEY_NAME, b.getType(), b.getValue(), category);
        //#else
        BestInPackClient.openKey = new KeyMapping(BestInPackClient.KEY_NAME, b.getType(), b.getValue(), BestInPackClient.KEY_CATEGORY);
        //#endif
        // Vanilla constructor + Forge setter: Forge 61 (1.21.11) changed its own constructors, this pair never changed.
        BestInPackClient.openKey.setKeyConflictContext(KeyConflictContext.UNIVERSAL);

        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new OpenFolderScreen(parent)));

        //#if MC >= 1.21.9
        //$$ RegisterKeyMappingsEvent.BUS.addListener(e -> e.register(BestInPackClient.openKey));
        //#elif MC >= 1.21.6
        //$$ RegisterKeyMappingsEvent.getBus(FMLJavaModLoadingContext.get().getModBusGroup()).addListener(e -> e.register(BestInPackClient.openKey));
        //#else
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> e.register(BestInPackClient.openKey));
        //#endif

        //#if MC >= 1.21.6
        //$$ TickEvent.ClientTickEvent.Post.BUS.addListener(e -> BestInPackClient.onClientTick(Minecraft.getInstance()));
        //$$ ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(e -> BestInPackClient.onJoin());
        //$$ ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> BestInPackClient.onLeave());
        //#if MC >= 26.3
        //$$ ScreenEvent.KeyPressed.Pre.BUS.addListener((java.util.function.Predicate<ScreenEvent.KeyPressed.Pre>) e -> BestInPackClient.onScreenKey(e.getScreen(), e.getInfo().key(), e.getInfo().keycode()));
        //#elif MC >= 1.21.9
        //$$ ScreenEvent.KeyPressed.Pre.BUS.addListener((java.util.function.Predicate<ScreenEvent.KeyPressed.Pre>) e -> BestInPackClient.onScreenKey(e.getScreen(), e.getInfo().key(), e.getInfo().scancode()));
        //#else
        //$$ ScreenEvent.KeyPressed.Pre.BUS.addListener((java.util.function.Predicate<ScreenEvent.KeyPressed.Pre>) e -> BestInPackClient.onScreenKey(e.getScreen(), e.getKeyCode(), e.getScanCode()));
        //#endif
        //$$ ScreenEvent.Init.Post.BUS.addListener(e -> BestInPackClient.onScreenInit(e.getScreen(), e::addListener));
        //$$ ScreenEvent.Render.Pre.BUS.addListener((java.util.function.Predicate<ScreenEvent.Render.Pre>) e -> { BestInPackClient.onScreenRender(e.getScreen()); return false; });
        //$$ ItemTooltipEvent.BUS.addListener(e -> BestInPackClient.onTooltip(e.getItemStack(), e.getToolTip()));
        //$$ if (Boolean.getBoolean("bestinpack.selftest")) {
        //$$     com.bestinpack.client.SmokeTest.install(r -> TickEvent.ClientTickEvent.Post.BUS.addListener(e -> r.run()));
        //$$ }
        //#else
        net.minecraftforge.eventbus.api.IEventBus bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addListener((TickEvent.ClientTickEvent.Post e) -> BestInPackClient.onClientTick(Minecraft.getInstance()));
        bus.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> BestInPackClient.onJoin());
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> BestInPackClient.onLeave());
        bus.addListener((ScreenEvent.KeyPressed.Pre e) -> {
            if (BestInPackClient.onScreenKey(e.getScreen(), e.getKeyCode(), e.getScanCode())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.Init.Post e) -> BestInPackClient.onScreenInit(e.getScreen(), e::addListener));
        bus.addListener((ScreenEvent.Render.Pre e) -> BestInPackClient.onScreenRender(e.getScreen()));
        bus.addListener((ItemTooltipEvent e) -> BestInPackClient.onTooltip(e.getItemStack(), e.getToolTip()));
        if (Boolean.getBoolean("bestinpack.selftest")) {
            com.bestinpack.client.SmokeTest.install(r -> bus.addListener((TickEvent.ClientTickEvent.Post e) -> r.run()));
        }
        //#endif
    }

    private static boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }
}
