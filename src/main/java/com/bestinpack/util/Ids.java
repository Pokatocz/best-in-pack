package com.bestinpack.util;

//#if MC >= 1.21.11
//$$ import net.minecraft.resources.Identifier;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif

/** Resource ids – ResourceLocation up to 1.21.10, Identifier from 1.21.11 on. Callers use {@code var}. */
public final class Ids {
    private Ids() {}

    //#if MC >= 1.21.11
    //$$ public static Identifier of(String namespace, String path) {
    //$$     return Identifier.fromNamespaceAndPath(namespace, path);
    //$$ }
    //$$
    //$$ public static Identifier vanilla(String path) {
    //$$     return Identifier.withDefaultNamespace(path);
    //$$ }
    //$$
    //$$ public static Identifier tryParse(String s) {
    //$$     return Identifier.tryParse(s);
    //$$ }
    //#else
    public static ResourceLocation of(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    public static ResourceLocation tryParse(String s) {
        return ResourceLocation.tryParse(s);
    }
    //#endif
}
