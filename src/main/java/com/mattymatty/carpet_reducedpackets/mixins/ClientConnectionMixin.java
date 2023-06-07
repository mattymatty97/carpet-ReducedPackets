package com.mattymatty.carpet_reducedpackets.mixins;

import carpet.helpers.TickSpeed;
import com.mattymatty.carpet_reducedpackets.config.CarpetConfig;
import com.mattymatty.carpet_reducedpackets.utils.TickUtils;
import com.mattymatty.carpet_reducedpackets.utils.exp4j.Extras;
import com.mattymatty.carpet_reducedpackets.utils.holders.ChunkPacketHolder;
import com.mattymatty.carpet_reducedpackets.utils.holders.EntityPacketHolder;
import com.mattymatty.carpet_reducedpackets.utils.holders.ParticlePacketHolder;
import com.mattymatty.carpet_reducedpackets.utils.holders.SoundPacketHolder;
import com.mattymatty.carpet_reducedpackets.utils.interfaces.PlayerHolder;
import com.mojang.authlib.GameProfile;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.objecthunter.exp4j.Expression;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin implements PlayerHolder {

    private ServerPlayerEntity player;
    private GameProfile profile;
    private final HashMap<Integer, EntityPacketHolder> entityPackets = new LinkedHashMap<>();
    private final HashMap<ChunkPos, ChunkPacketHolder> chunkPackets = new LinkedHashMap<>();
    private final HashMap<Identifier, SoundPacketHolder> soundPackets = new LinkedHashMap<>();
    private final HashMap<Vec3d, ParticlePacketHolder> particlePackets = new LinkedHashMap<>();
    private RegistryKey<World> current_dimension = null;
    private final long[] next_packet_flush = new long[4];
    private long respawn_grace = 0;

    private final Consumer<Packet<?>> packetConsumer = p -> {
        this.sendImmediately(p, null);
    };

    @Shadow
    protected abstract void sendImmediately(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> callback);

    @Redirect(method = "send(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;sendImmediately(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V"))
    private void handlePacket(ClientConnection instance, Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> callback) {
        if (getPlayer() == null){
            this.sendImmediately(packet, callback);
            return;
        }

        //do not throttle packets with callbacks
        if (callback != null) {
            this.sendImmediately(packet, callback);
            return;
        }

        //PlayerConfig config = ReducedPackets.player_config_map.getUnchecked(this.profile);

        if (packet instanceof GameJoinS2CPacket joinPacket) {
            this.current_dimension = joinPacket.getDimensionId();
        }

        if (packet instanceof PlayerRespawnS2CPacket respawnS2CPacket) {
            RegistryKey<World> destination = respawnS2CPacket.getDimension();
            //if we're respawning in a new dimension forget all data about the previous dimension
            if (!destination.equals(current_dimension)) {
                chunkPackets.clear();
                entityPackets.clear();
                soundPackets.clear();
                particlePackets.clear();
            }
            this.current_dimension = destination;
            //add few ticks of grace period to prevent weirdness during respawn
            this.respawn_grace = 20;
        }

        Map<String,Double> variables = Map.of(
                "MSPT", TickUtils.getMSPT(),
                "TPS", TickUtils.getTPS(),
                "PING", (double)getPlayer().pingMilliseconds);

        boolean isHighPing = variables.get("PING") > CarpetConfig.MinPing;
        boolean isHighTPS = variables.get("TPS") >= CarpetConfig.MinTPS;
        //do not throttle if the server is frozen or if it is not able to keep up with the warp
        boolean shouldReducePackets = CarpetConfig.enabled &&
                !TickSpeed.isPaused() &&
                respawn_grace <= 0 &&
                isHighPing &&
                isHighTPS;
        boolean forcedFlush = !shouldReducePackets;
        //use the time update packet as a trigger to send the packet
        boolean isTickPacket = packet instanceof WorldTimeUpdateS2CPacket;

        if (isTickPacket && respawn_grace > 0)
            respawn_grace--;

        if (forcedFlush || isTickPacket) {

            if ( !chunkPackets.isEmpty() && (forcedFlush || this.next_packet_flush[0] <= System.currentTimeMillis())) {
                ChunkPacketHolder.flushPackets(chunkPackets, current_dimension, packetConsumer);
                //Expression expression = config.getBlock_delay_expression();
                Expression expression = CarpetConfig.blocksExpression != null ? CarpetConfig.blocksExpression : CarpetConfig.defaultExpression;
                expression.setVariables(variables);
                this.next_packet_flush[0] = System.currentTimeMillis() + Math.round(expression.evaluate());
            }

            if ( !entityPackets.isEmpty() && (forcedFlush || this.next_packet_flush[1] <= System.currentTimeMillis())) {
                EntityPacketHolder.flushPackets(entityPackets, packetConsumer);
                //Expression expression = config.getEntity_delay_expression();
                Expression expression = CarpetConfig.entitiesExpression != null ? CarpetConfig.entitiesExpression : CarpetConfig.defaultExpression;
                expression.setVariables(variables);
                this.next_packet_flush[1] = System.currentTimeMillis() + Math.round(expression.evaluate());
            }

            if ( !soundPackets.isEmpty() && (forcedFlush || this.next_packet_flush[2] <= System.currentTimeMillis())) {
                SoundPacketHolder.flushPackets(soundPackets, packetConsumer);
                //Expression expression = config.getSound_delay_expression();
                Expression expression = CarpetConfig.soundsExpression != null ? CarpetConfig.soundsExpression : CarpetConfig.defaultExpression;
                expression.setVariables(variables);
                this.next_packet_flush[2] = System.currentTimeMillis() + Math.round(expression.evaluate());
            }

            if ( !particlePackets.isEmpty() && (forcedFlush || this.next_packet_flush[3] <= System.currentTimeMillis())) {
                ParticlePacketHolder.flushPackets(particlePackets, packetConsumer);
                //Expression expression = config.getParticle_delay_expression();
                Expression expression = CarpetConfig.particlesExpression != null ? CarpetConfig.particlesExpression : CarpetConfig.defaultExpression;
                expression.setVariables(variables);
                this.next_packet_flush[3] = System.currentTimeMillis() + Math.round(expression.evaluate());
            }

        }

        if (shouldReducePackets) {
            if (EntityPacketHolder.HANDLED_PACKETS.containsKey(packet.getClass())) {
                if ( CarpetConfig.entitiesExpression == Extras.SUPPRESS ||
                        ( CarpetConfig.entitiesExpression != Extras.PASSTHOURGH &&
                                EntityPacketHolder.handlePacket(packet, current_dimension, entityPackets))
                ) {
                    return;
                }
            } else if (ChunkPacketHolder.HANDLED_PACKETS.containsKey(packet.getClass())) {
                if ( CarpetConfig.blocksExpression == Extras.SUPPRESS ||
                        ( CarpetConfig.blocksExpression != Extras.PASSTHOURGH &&
                                ChunkPacketHolder.handlePacket(packet, current_dimension, chunkPackets))
                ){
                    return;
                }
            } else if (SoundPacketHolder.HANDLED_PACKETS.containsKey(packet.getClass())) {
                if ( CarpetConfig.soundsExpression == Extras.SUPPRESS ||
                        ( CarpetConfig.soundsExpression != Extras.PASSTHOURGH &&
                                SoundPacketHolder.handlePacket(packet, soundPackets))
                ){
                    return;
                }
            } else if (ParticlePacketHolder.HANDLED_PACKETS.containsKey(packet.getClass())) {
                if ( CarpetConfig.particlesExpression == Extras.SUPPRESS ||
                        ( CarpetConfig.particlesExpression != Extras.PASSTHOURGH &&
                                ParticlePacketHolder.handlePacket(packet, particlePackets))
                ){
                    return;
                }
            }
        }

        this.sendImmediately(packet, callback);
    }

    @Override
    public ServerPlayerEntity getPlayer() {
        return player;
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        this.player = player;
        this.profile = player.getGameProfile();
    }
}
