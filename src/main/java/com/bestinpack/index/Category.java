package com.bestinpack.index;

import net.minecraft.network.chat.Component;

public enum Category {
    FOOD("food"),
    ARMOR("armor"),
    WEAPONS("weapons"),
    FUEL("fuel");

    public final String id;

    Category(String id) {
        this.id = id;
    }

    public Component label() {
        return Component.translatable("bestinpack.category." + id);
    }
}
