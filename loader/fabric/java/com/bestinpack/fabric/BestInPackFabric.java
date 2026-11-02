package com.bestinpack.fabric;

import com.bestinpack.BestInPack;
import com.bestinpack.client.BestInPackClient;
import com.bestinpack.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;

/** Fabric entry point (client only). Wires Fabric API events to the shared {@link BestInPackClient}. */
public class BestInPackFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Platform.set(new FabricPlatform());
        BestInPack.LOGGER.info("Best in Pack loaded.");

        // Looked up by name ("key.keyboard.b") because key codes are not the same in every version.
        InputConstants.Key b = BestInPackClient.defaultOpenKey(); // B unless the config says otherwise
        //#if MC >= 1.21.9
        //$$ KeyMapping.Category category = KeyMapping.Category.register(com.bestinpack.util.Ids.of(BestInPack.MOD_ID, "general"));
        //$$ KeyMapping key = new KeyMapping(BestInPackClient.KEY_NAME, b.getType(), b.getValue(), category);
        //#else
        KeyMapping key = new KeyMapping(BestInPackClient.KEY_NAME, b.getType(), b.getValue(), BestInPackClient.KEY_CATEGORY);
        //#endif
        //#if MC >= 26.1
        //$$ BestInPackClient.openKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(key);
        //#else
        BestInPackClient.openKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(key);
        //#endif

        ClientTickEvents.END_CLIENT_TICK.register(BestInPackClient::onClientTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BestInPackClient.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BestInPackClient.onLeave());

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            //#if MC >= 26.1
            //$$ BestInPackClient.onScreenInit(screen, w -> Screens.getWidgets(screen).add(w));
            //#else
            BestInPackClient.onScreenInit(screen, w -> Screens.getButtons(screen).add(w));
            //#endif
            // allowKeyPress runs before the screen sees the key – inventories swallow every key otherwise.
            //#if MC >= 26.3
            //$$ ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !BestInPackClient.onScreenKey(s, event.key(), event.keycode()));
            //#elif MC >= 1.21.9
            //$$ ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !BestInPackClient.onScreenKey(s, event.key(), event.scancode()));
            //#else
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyCode, scanCode, modifiers) -> !BestInPackClient.onScreenKey(s, keyCode, scanCode));
            //#endif
            //#if MC >= 26.1
            //$$ ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> BestInPackClient.onScreenRender(s));
            //#else
            ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, delta) -> BestInPackClient.onScreenRender(s));
            //#endif
        });
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> BestInPackClient.onTooltip(stack, lines));

        if (Boolean.getBoolean("bestinpack.selftest")) {
            com.bestinpack.client.SmokeTest.install(r -> ClientTickEvents.END_CLIENT_TICK.register(c -> r.run()));
        }
    }
}
