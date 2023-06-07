package com.mattymatty.carpet_reducedpackets;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.settings.SettingsManager;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mattymatty.carpet_reducedpackets.config.CarpetConfig;
import com.mattymatty.carpet_reducedpackets.config.PlayerConfig;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

public class ReducedPackets implements DedicatedServerModInitializer, CarpetExtension {

    public static final Logger LOGGER = LoggerFactory.getLogger(ReducedPackets.class.getName());

    public static SettingsManager settingsManager;

    private static MinecraftServer ms;

    private static final ResourceBundle version = ResourceBundle.getBundle("placeholders");

/*
    public static final LoadingCache<GameProfile, PlayerConfig> player_config_map = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build(CacheLoader.from(k -> new PlayerConfig(
                    CarpetConfig.MinTPS, CarpetConfig.MinPing, CarpetConfig.DefaultDelay, CarpetConfig.EntityDelay, CarpetConfig.BlockDelay, CarpetConfig.SoundDelay, CarpetConfig.ParticleDelay
            )));
*/

    @SuppressWarnings("deprecation")
    public static MinecraftServer getMs() {
        return ms != null ? ms :(MinecraftServer) FabricLoader.getInstance().getGameInstance();
    }

    @Override
    public void onInitializeServer() {
        LOGGER.info("carpet_reduced_packets Loaded!");
        CarpetServer.manageExtension(this);
        settingsManager = new SettingsManager(version.getString("version") + "-" + version.getString("build"),"reduced_packets","Reduced Packets");
        settingsManager.parseSettingsClass(CarpetConfig.class);
    }

    @Override
    public void onServerLoaded(MinecraftServer server) {
        ReducedPackets.ms = server;
    }

    @Override
    public SettingsManager customSettingsManager() {
        return settingsManager;
    }

    @Override
    public void onTick(MinecraftServer server) {
        CarpetExtension.super.onTick(server);
    }
}
