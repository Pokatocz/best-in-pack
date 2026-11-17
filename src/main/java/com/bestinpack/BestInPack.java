package com.bestinpack;

//#if MC >= 1.18.2
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#else
//$$ import org.apache.logging.log4j.LogManager;
//$$ import org.apache.logging.log4j.Logger;
//#endif

/** Shared constants. The loader entry points live in com.bestinpack.fabric / .forge / .neoforge. */
public final class BestInPack {
    private BestInPack() {}

    public static final String MOD_ID = "bestinpack";
    //#if MC >= 1.18.2
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    //#else
    //$$ // Minecraft 1.18 – 1.18.1 logs through Log4j only: Forge 38 – 39 has nothing behind SLF4J there, and every
    //$$ // message would be dropped. Log4j's logger takes the same calls.
    //$$ public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    //#endif
}
