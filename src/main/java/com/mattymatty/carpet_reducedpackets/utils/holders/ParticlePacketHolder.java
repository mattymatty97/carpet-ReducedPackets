package com.mattymatty.carpet_reducedpackets.utils.holders;

import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ParticlePacketHolder {
    public static final Map<Class<?>, Function<Packet<?>, Vec3d>> HANDLED_PACKETS = Map.ofEntries(
            Map.entry(ParticleS2CPacket.class, (Packet<?> packet) -> {
                ParticleS2CPacket cast_packet = ((ParticleS2CPacket) packet);
                return new Vec3d(cast_packet.getX(), cast_packet.getY(), cast_packet.getZ());
            })
    );
    public final Vec3d position;
    public Map<ParticleEffect, Queue<ParticleS2CPacket>> particle_map = new LinkedHashMap<>();

    public ParticlePacketHolder(Vec3d position) {
        this.position = position;
    }

    public static boolean handlePacket(Packet<?> packet, Map<Vec3d, ParticlePacketHolder> particleMap) {
        Vec3d pos = HANDLED_PACKETS.getOrDefault(packet.getClass(), (p) -> null).apply(packet);
        ParticlePacketHolder holder = particleMap.computeIfAbsent(pos, ParticlePacketHolder::new);
        if (packet instanceof ParticleS2CPacket particlePacket) {
            Queue<ParticleS2CPacket> packets = holder.particle_map.computeIfAbsent(particlePacket.getParameters(), k -> new CircularFifoQueue<>(5));
            packets.add(particlePacket);
            return true;
        }
        return false;
    }

    public static void flushPackets(Map<Vec3d, ParticlePacketHolder> particleMap, Consumer<Packet<?>> sender) {

        particleMap.values().stream().map(ParticlePacketHolder::getParticles).flatMap(Collection::stream).filter(Objects::nonNull).forEach(sender);

        particleMap.clear();
    }

    public Vec3d getPosition() {
        return position;
    }

    public Collection<ParticleS2CPacket> getParticles() {
        return particle_map.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
    }
}
