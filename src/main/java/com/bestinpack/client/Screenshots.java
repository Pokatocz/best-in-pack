package com.bestinpack.client;

import com.bestinpack.BestInPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Self-test only: saves a screenshot to screenshots/&lt;name&gt;.png. */
final class Screenshots {
    private Screenshots() {}

    static void take(Minecraft client, String name) {
        //#if MC >= 26.2
        //$$ Screenshot.grab(client.gameDirectory, name + ".png", client.gameRenderer.mainRenderTarget(), 1,
        //$$         text -> BestInPack.LOGGER.info("SMOKE: screenshot {}", text.getString()));
        //#elif MC >= 1.21.6
        //$$ Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
        //$$         text -> BestInPack.LOGGER.info("SMOKE: screenshot {}", text.getString()));
        //#else
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(),
                text -> BestInPack.LOGGER.info("SMOKE: screenshot {}", text.getString()));
        //#endif
    }
}
