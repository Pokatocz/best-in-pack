package com.bestinpack.neoforge;

import com.bestinpack.BestInPack;
import com.bestinpack.client.BestInPackClient;
import com.bestinpack.client.gui.OpenFolderScreen;
import com.bestinpack.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** NeoForge entry point (client only). Wires NeoForge's events to the shared {@link BestInPackClient}. */
@Mod(value = BestInPack.MOD_ID, dist = Dist.CLIENT)
public class BestInPackNeoForge {
    public BestInPackNeoForge(IEventBus modBus, ModContainer container) {
        Platform.set(new NeoForgePlatform());
        BestInPack.LOGGER.info("Best in Pack loaded.");

        // B by default; the key is looked up by name because key codes are not the same in every version.
        InputConstants.Key b = BestInPackClient.defaultOpenKey(); // B unless the config says otherwise
        //#if MC >= 1.21.9
        //$$ KeyMapping.Category category = new KeyMapping.Category(com.bestinpack.util.Ids.of(BestInPack.MOD_ID, "general"));
        //$$ BestInPackClient.openKey = new KeyMapping(BestInPackClient.KEY_NAME, KeyConflictContext.UNIVERSAL, b.getType(), b.getValue(), category);
        //$$ modBus.addListener(RegisterKeyMappingsEvent.class, e -> {
        //$$     e.registerCategory(category);
        //$$     e.register(BestInPackClient.openKey);
        //$$ });
        //#else
        BestInPackClient.openKey = new KeyMapping(BestInPackClient.KEY_NAME, KeyConflictContext.UNIVERSAL, b.getType(), b.getValue(),
                BestInPackClient.KEY_CATEGORY);
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(BestInPackClient.openKey));
        //#endif

        // The mod list's "Config" button opens config/bestinpack/ (there is no settings screen – the JSON is the settings).
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new OpenFolderScreen(parent));

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> BestInPackClient.onClientTick(Minecraft.getInstance()));
        bus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> BestInPackClient.onJoin());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> BestInPackClient.onLeave());
        // Pre, because container screens swallow every key (their keyPressed always returns true).
        bus.addListener(ScreenEvent.KeyPressed.Pre.class, e -> {
            //#if MC >= 26.3
            //$$ if (BestInPackClient.onScreenKey(e.getScreen(), e.getKeyEvent().key(), e.getKeyEvent().keycode())) e.setCanceled(true);
            //#elif MC >= 1.21.9
            //$$ if (BestInPackClient.onScreenKey(e.getScreen(), e.getKeyEvent().key(), e.getKeyEvent().scancode())) e.setCanceled(true);
            //#else
            if (BestInPackClient.onScreenKey(e.getScreen(), e.getKeyCode(), e.getScanCode())) e.setCanceled(true);
            //#endif
        });
        bus.addListener(ScreenEvent.Init.Post.class, e -> BestInPackClient.onScreenInit(e.getScreen(), e::addListener));
        bus.addListener(ScreenEvent.Render.Pre.class, e -> BestInPackClient.onScreenRender(e.getScreen()));
        bus.addListener(ItemTooltipEvent.class, e -> BestInPackClient.onTooltip(e.getItemStack(), e.getToolTip()));

        if (Boolean.getBoolean("bestinpack.selftest")) {
            com.bestinpack.client.SmokeTest.install(r -> bus.addListener(ClientTickEvent.Post.class, e -> r.run()));
        }
    }
}
