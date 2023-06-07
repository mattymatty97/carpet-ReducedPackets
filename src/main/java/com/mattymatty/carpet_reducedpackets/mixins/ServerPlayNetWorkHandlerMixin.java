package com.mattymatty.carpet_reducedpackets.mixins;

import com.mattymatty.carpet_reducedpackets.utils.interfaces.PlayerHolder;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetWorkHandlerMixin {
    @Inject(method = "<init>",at = @At("RETURN"))
    void registerPlayer(MinecraftServer server, ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci){
        ((PlayerHolder)connection).setPlayer(player);
    }
}
