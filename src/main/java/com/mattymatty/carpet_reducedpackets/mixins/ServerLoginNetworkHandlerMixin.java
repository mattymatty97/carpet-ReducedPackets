package com.mattymatty.carpet_reducedpackets.mixins;

import com.mattymatty.carpet_reducedpackets.utils.TickUtils;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginNetworkHandlerMixin {

    @Shadow
    private int loginTicks;
    private long next_expected_tick = 0;

    @Inject(method = "tick", at = @At("RETURN"))
    private void handleTickWarpLogin(CallbackInfo ci) {
        if (TickUtils.getTPS() > 22.0) {
            long now = System.currentTimeMillis();
            if (next_expected_tick > now) {
                this.loginTicks--;
            } else {
                next_expected_tick = now + TickUtils.NORMAL_MSPT;
            }
        }
    }
}
