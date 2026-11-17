package com.bestinpack.forge;

import com.bestinpack.BestInPack;
import com.bestinpack.client.BestInPackClient;
import com.bestinpack.client.gui.OpenFolderScreen;
import com.bestinpack.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//#if MC >= 1.19
import net.minecraftforge.client.ConfigScreenHandler;
//#endif
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
//#if MC >= 1.19
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//#endif
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
        //#if MC < 1.19
        //$$ // Client-only: a server without the mod is no mismatch. (mods.toml's displayTest says the same, but Forge
        //$$ // 38 – 40.0 does not read it yet.)
        //$$ ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.fml.IExtensionPoint.DisplayTest.class,
        //$$         () -> new net.minecraftforge.fml.IExtensionPoint.DisplayTest(
        //$$                 () -> net.minecraftforge.network.NetworkConstants.IGNORESERVERONLY, (remoteVersion, fromServer) -> true));
        //#endif
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

        //#if MC >= 1.19
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new OpenFolderScreen(parent)));
        //#else
        //$$ ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.client.ConfigGuiHandler.ConfigGuiFactory.class,
        //$$         () -> new net.minecraftforge.client.ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new OpenFolderScreen(parent)));
        //#endif

        //#if MC >= 1.21.9
        //$$ RegisterKeyMappingsEvent.BUS.addListener(e -> e.register(BestInPackClient.openKey));
        //#elif MC >= 1.21.6
        //$$ RegisterKeyMappingsEvent.getBus(FMLJavaModLoadingContext.get().getModBusGroup()).addListener(e -> e.register(BestInPackClient.openKey));
        //#elif MC >= 1.19
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> e.register(BestInPackClient.openKey));
        //#else
        //$$ // Forge 38 – 40 (1.18.x) has no event for key mappings: they are added to the options during client setup.
        //$$ FMLJavaModLoadingContext.get().getModEventBus().addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) ->
        //$$         net.minecraftforge.client.ClientRegistry.registerKeyBinding(BestInPackClient.openKey));
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
        //#if MC >= 1.20.6
        bus.addListener((TickEvent.ClientTickEvent.Post e) -> BestInPackClient.onClientTick(Minecraft.getInstance()));
        //#else
        //$$ // Forge before 50 (1.20.6) has one tick event with a phase.
        //$$ bus.addListener((TickEvent.ClientTickEvent e) -> {
        //$$     if (e.phase == TickEvent.Phase.END) BestInPackClient.onClientTick(Minecraft.getInstance());
        //$$ });
        //#endif
        bus.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> BestInPackClient.onJoin());
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> BestInPackClient.onLeave());
        bus.addListener((ScreenEvent.KeyPressed.Pre e) -> {
            if (BestInPackClient.onScreenKey(e.getScreen(), e.getKeyCode(), e.getScanCode())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.Init.Post e) -> BestInPackClient.onScreenInit(e.getScreen(), e::addListener));
        bus.addListener((ScreenEvent.Render.Pre e) -> BestInPackClient.onScreenRender(e.getScreen()));
        bus.addListener((ItemTooltipEvent e) -> BestInPackClient.onTooltip(e.getItemStack(), e.getToolTip()));
        if (Boolean.getBoolean("bestinpack.selftest")) {
            //#if MC >= 1.20.6
            com.bestinpack.client.SmokeTest.install(r -> bus.addListener((TickEvent.ClientTickEvent.Post e) -> r.run()));
            //#else
            //$$ com.bestinpack.client.SmokeTest.install(r -> bus.addListener((TickEvent.ClientTickEvent e) -> {
            //$$     if (e.phase == TickEvent.Phase.END) r.run();
            //$$ }));
            //#endif
        }
        //#endif
    }

    private static boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }
}
