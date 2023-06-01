package com.mattymatty.carpet_reducedpackets.utils.holders;

import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundIdS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class SoundPacketHolder {
    public static final Map<Class<?>, Function<Packet<?>, Identifier>> HANDLED_PACKETS = Map.ofEntries(
            Map.entry(PlaySoundS2CPacket.class, (Packet<?> packet) -> ((PlaySoundS2CPacket) packet).getSound().getId()),
            Map.entry(StopSoundS2CPacket.class, (Packet<?> packet) -> ((StopSoundS2CPacket) packet).getSoundId()),
            Map.entry(PlaySoundIdS2CPacket.class, (Packet<?> packet) -> ((PlaySoundIdS2CPacket) packet).getSoundId())
    );
    public final Identifier ID;
    public final Map<Vec3d, Packet<?>> location_map = new LinkedHashMap<>();
    public StopSoundS2CPacket stopPacket;
    public boolean playing = true;

    public SoundPacketHolder(Identifier ID) {
        this.ID = ID;
    }

    public static boolean handlePacket(Packet<?> packet, Map<Identifier, SoundPacketHolder> soundMap) {
        Identifier soundId = HANDLED_PACKETS.getOrDefault(packet.getClass(), (p) -> null).apply(packet);
        SoundPacketHolder holder = soundMap.computeIfAbsent(soundId, SoundPacketHolder::new);
        if (packet instanceof PlaySoundS2CPacket playPacket) {
            Vec3d pos = new Vec3d(playPacket.getX(), playPacket.getY(), playPacket.getZ());
            holder.location_map.put(pos, packet);
            holder.playing = true;
            return true;
        }
        if (packet instanceof PlaySoundIdS2CPacket playPacket) {
            Vec3d pos = new Vec3d(playPacket.getX(), playPacket.getY(), playPacket.getZ());
            holder.location_map.put(pos, packet);
            holder.playing = true;
            return true;
        }
        if (packet instanceof StopSoundS2CPacket stopPacket) {
            holder.location_map.clear();
            holder.stopPacket = stopPacket;
            holder.playing = false;
            return true;
        }
        return false;
    }

    public static void flushPackets(Map<Identifier, SoundPacketHolder> soundMap, Consumer<Packet<?>> sender) {

        soundMap.values().stream().map(SoundPacketHolder::getStopPacket).filter(Objects::nonNull).forEach(sender);

        soundMap.values().stream().filter(SoundPacketHolder::isPlaying).map(SoundPacketHolder::getPlaying).flatMap(Collection::stream).filter(Objects::nonNull).forEach(sender);

        soundMap.clear();
    }

    public Identifier getID() {
        return ID;
    }

    public StopSoundS2CPacket getStopPacket() {
        return stopPacket;
    }

    public boolean isPlaying() {
        return playing;
    }

    public Collection<Packet<?>> getPlaying() {
        return location_map.values();
    }
}
