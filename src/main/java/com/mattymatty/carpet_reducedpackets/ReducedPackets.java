package com.mattymatty.carpet_reducedpackets;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReducedPackets implements DedicatedServerModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(ReducedPackets.class.getName());


    @SuppressWarnings("deprecation")
    public static MinecraftServer getMs() {
        return (MinecraftServer) FabricLoader.getInstance().getGameInstance();
    }

    @Override
    public void onInitializeServer() {
        LOGGER.info("carpet_reduced_packets Loaded!");
    }
}
