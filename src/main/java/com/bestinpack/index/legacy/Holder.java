//#if MC < 1.19.3
package com.bestinpack.index.legacy;

import net.minecraft.resources.ResourceKey;

import java.util.Optional;

/**
 * A registry value with its key, for Minecraft 1.18 – 1.19.2: the item index keeps attributes as holders (which they
 * are in the game from 1.20.5 on), but 1.18 – 1.18.1 has no Holder and up to 1.19.2 the registry does not hand one out
 * for any value. This has the two methods the index uses.
 */
public record Holder<T>(T value, Optional<ResourceKey<T>> key) {
    public Optional<ResourceKey<T>> unwrapKey() {
        return key;
    }
}
//#endif
