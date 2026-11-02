package com.bestinpack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Self-test only: creates a creative test world (the API differs between versions). */
final class WorldStarter {
    private WorldStarter() {}

    static void createOrLoad(Minecraft client, String name) {
        //#if MC >= 26.1
        //$$ LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
        //$$         new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
        //#elif MC >= 1.21.11
        //$$ LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true,
        //$$         new net.minecraft.world.level.gamerules.GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT);
        //#elif MC >= 1.21.2
        //$$ LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true,
        //$$         new net.minecraft.world.level.GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT);
        //#else
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
        //#endif
        client.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
                WorldPresets::createNormalWorldDimensions, Screens.current());
    }
}
