package com.mattymatty.carpet_reducedpackets.mixins;

import carpet.helpers.TickSpeed;
import com.mattymatty.carpet_reducedpackets.ReducedPackets;
import com.mattymatty.carpet_reducedpackets.utils.TickUtils;
import com.mattymatty.carpet_reducedpackets.utils.holders.ChunkPacketHolder;
import com.mattymatty.carpet_reducedpackets.utils.holders.EntityPacketHolder;
import com.mattymatty.carpet_reducedpackets.utils.holders.ParticlePacketHolder;
import com.mattymatty.carpet_reducedpackets.utils.holders.SoundPacketHolder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    private final AtomicLong suppressed = new AtomicLong();
    private final HashMap<Integer, EntityPacketHolder> entityPackets = new LinkedHashMap<>();
    private final HashMap<ChunkPos, ChunkPacketHolder> chunkPackets = new LinkedHashMap<>();
    private final HashMap<Identifier, SoundPacketHolder> soundPackets = new LinkedHashMap<>();
    private final HashMap<Vec3d, ParticlePacketHolder> particlePackets = new LinkedHashMap<>();
    private RegistryKey<World> current_dimension = null;
    private long last_packet_flush = 0;
    private long respawn_grace = 0;

    @Shadow
    protected abstract void sendImmediately(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> callback);

    @Redirect(method = "send(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;sendImmediately(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V"))
    private void handlePacket(ClientConnection instance, Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> callback) {
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

            this.respawn_grace = 20;
        }

        boolean isTick = packet instanceof WorldTimeUpdateS2CPacket;
        if (isTick && respawn_grace > 0)
            respawn_grace--;

        if (!chunkPackets.isEmpty() || !entityPackets.isEmpty()) {
            boolean override = TickSpeed.isPaused() || TickUtils.getTPS() < 25.0d || respawn_grace > 0;
            if (override || isTick) {
                //use the time update packet as a trigger to send the packets
                if (override || this.last_packet_flush + TickUtils.NORMAL_MSPT <= System.currentTimeMillis()) {
                    //send packets as if the server was running at normal speed
                    //or if a special condition triggers
                    AtomicInteger count = new AtomicInteger();

                    Consumer<Packet<?>> packetConsumer = p -> {
                        count.incrementAndGet();
                        this.sendImmediately(p, null);
                    };

                    ChunkPacketHolder.flushPackets(chunkPackets, current_dimension, packetConsumer);
                    EntityPacketHolder.flushPackets(entityPackets, packetConsumer);
                    SoundPacketHolder.flushPackets(soundPackets, packetConsumer);
                    ParticlePacketHolder.flushPackets(particlePackets, packetConsumer);

                    ReducedPackets.LOGGER.debug("Sent %d Packets instead of %d".formatted(count.get(), suppressed.get()));
                    suppressed.set(0);
                    //reset last sent packet Timer
                    this.last_packet_flush = System.currentTimeMillis();
                }
            }
        }

        //do not throttle packets with callbacks
        if (callback != null) {
            this.sendImmediately(packet, callback);
            return;
        }

        //do not throttle if the server is frozen or if it is not able to keep up with the warp
        if (!TickSpeed.isPaused() && TickUtils.getTPS() >= 25.0d && respawn_grace <= 0) {

            if (chunkPackets.isEmpty() && entityPackets.isEmpty()) {
                //set delay on the first handled packet
                this.last_packet_flush = System.currentTimeMillis();
            }

            if (EntityPacketHolder.HANDLED_PACKETS.containsKey(packet.getClass())) {
                if (EntityPacketHolder.handlePacket(packet, current_dimension, entityPackets)) {
                    suppressed.incrementAndGet();
                    return;
                }
            } else if (ChunkPacketHolder.HANDLED_PACKETS.containsKey(packet.getClass())) {
                if (ChunkPacketHolder.handlePacket(packet, current_dimension, chunkPackets)) {
                    suppressed.incrementAndGet();
                    return;
                }
            } else if (SoundPacketHolder.HANDLED_PACKETS.containsKey(packet.getClass())) {
                if (SoundPacketHolder.handlePacket(packet, soundPackets)) {
                    suppressed.incrementAndGet();
                    return;
                }
            } else if (ParticlePacketHolder.HANDLED_PACKETS.containsKey(packet.getClass())) {
                if (ParticlePacketHolder.handlePacket(packet, particlePackets)) {
                    suppressed.incrementAndGet();
                    return;
                }
            }
        }
        this.sendImmediately(packet, callback);
    }

}
