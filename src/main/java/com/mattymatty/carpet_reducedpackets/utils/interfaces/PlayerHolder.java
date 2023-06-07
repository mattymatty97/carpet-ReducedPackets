package com.mattymatty.carpet_reducedpackets.utils.interfaces;

import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerHolder {
    ServerPlayerEntity getPlayer();
    void setPlayer(ServerPlayerEntity player);
}
