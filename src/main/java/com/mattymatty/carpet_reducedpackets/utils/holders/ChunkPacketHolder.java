package com.mattymatty.carpet_reducedpackets.utils.holders;

import com.mattymatty.carpet_reducedpackets.ReducedPackets;
import com.mattymatty.carpet_reducedpackets.utils.ChunkUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import net.minecraft.block.BlockState;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.light.LightingProvider;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChunkPacketHolder {
    public static final Map<Class<?>, Function<Packet<?>, ChunkPos>> HANDLED_PACKETS = Map.ofEntries(
            Map.entry(ChunkDataS2CPacket.class, (Packet<?> packet) -> {
                ChunkDataS2CPacket castPacket = ((ChunkDataS2CPacket) packet);
                return new ChunkPos(castPacket.getX(), castPacket.getZ());
            }),
            Map.entry(UnloadChunkS2CPacket.class, (Packet<?> packet) -> {
                UnloadChunkS2CPacket castPacket = ((UnloadChunkS2CPacket) packet);
                return new ChunkPos(castPacket.getX(), castPacket.getZ());
            }),
            Map.entry(ChunkDeltaUpdateS2CPacket.class, (Packet<?> packet) -> {
                ChunkDeltaUpdateS2CPacket castPacket = ((ChunkDeltaUpdateS2CPacket) packet);
                AtomicReference<ChunkPos> pos = new AtomicReference<>();
                castPacket.visitUpdates((blockPos, state) -> pos.set(new ChunkPos(blockPos)));
                return pos.get();
            }),
            Map.entry(BlockEventS2CPacket.class, (Packet<?> packet) -> {
                BlockEventS2CPacket castPacket = ((BlockEventS2CPacket) packet);
                return new ChunkPos(castPacket.getPos());
            }),
            Map.entry(BlockBreakingProgressS2CPacket.class, (Packet<?> packet) -> {
                BlockBreakingProgressS2CPacket castPacket = ((BlockBreakingProgressS2CPacket) packet);
                return new ChunkPos(castPacket.getPos());
            }),
            Map.entry(ExplosionS2CPacket.class, (Packet<?> packet) -> null),
            Map.entry(LightUpdateS2CPacket.class, (Packet<?> packet) -> {
                LightUpdateS2CPacket castPacket = ((LightUpdateS2CPacket) packet);
                return new ChunkPos(castPacket.getChunkX(), castPacket.getChunkZ());
            })
    );
    public final ChunkPos pos;
    public final Set<BlockEventWrapper> blockEvents = new LinkedHashSet<>();
    public final Map<BlockPos, BlockBreakingProgressS2CPacket> blockBreaking = new LinkedHashMap<>();
    public final Set<ExplosionS2CPacket> explosions = new LinkedHashSet<>();
    public boolean loaded = true;
    public ChunkDataS2CPacket loadPacket;

    public UnloadChunkS2CPacket unloadPacket;

    public Int2ObjectMap<Short2ObjectMap<BlockState>> deltaUpdates = new Int2ObjectLinkedOpenHashMap<>();

    public BitSet[] light = null;

    public ChunkPacketHolder(ChunkPos pos) {
        this.pos = pos;
    }

    public static boolean handlePacket(Packet<?> packet, RegistryKey<World> world_key, Map<ChunkPos, ChunkPacketHolder> chunkMap) {
        World world = ReducedPackets.getMs().getWorld(world_key);
        if (world == null)
            return false;

        if (packet instanceof ExplosionS2CPacket explosionS2CPacket) {
            explosionS2CPacket.getAffectedBlocks().forEach(
                    blockPos -> {
                        ChunkSectionPos sectionPos = ChunkSectionPos.from(blockPos);
                        ChunkPos chunkPos = sectionPos.toChunkPos();
                        ChunkPacketHolder holder = chunkMap.computeIfAbsent(chunkPos, ChunkPacketHolder::new);
                        holder.explosions.add(explosionS2CPacket);
                        holder.addExplosionDelta(blockPos, world);
                    }
            );
            return true;
        }

        ChunkPos chunkPos = HANDLED_PACKETS.getOrDefault(packet.getClass(), (p) -> null).apply(packet);
        Chunk chunk = null;
        if (chunkPos != null)
            chunk = world.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY);
        if (chunk == null)
            return false;

        if (packet instanceof UnloadChunkS2CPacket unloadChunkS2CPacket) {
            ChunkPacketHolder holder = new ChunkPacketHolder(chunkPos);
            holder.loaded = false;
            holder.unloadPacket = unloadChunkS2CPacket;
            return true;
        }

        ChunkPacketHolder holder = chunkMap.computeIfAbsent(chunkPos, ChunkPacketHolder::new);

        if (packet instanceof ChunkDataS2CPacket chunkDataS2CPacket) {
            holder.loadPacket = chunkDataS2CPacket;
            //clear all delta updates
            holder.deltaUpdates.clear();
            //if there are explosions regenerate the corresponding delta updates
            if (!holder.explosions.isEmpty()) {
                holder.explosions.stream()
                        .map(ExplosionS2CPacket::getAffectedBlocks)
                        .flatMap(Collection::stream)
                        .distinct()
                        .forEach(blockPos -> holder.addExplosionDelta(blockPos, world));
            }
            return true;
        }

        if (packet instanceof LightUpdateS2CPacket lightUpdateS2CPacket) {
            BitSet sky = (BitSet) lightUpdateS2CPacket.getSkyLightMask().clone();
            sky.or(lightUpdateS2CPacket.getFilledSkyLightMask());
            BitSet block = (BitSet) lightUpdateS2CPacket.getBlockLightMask().clone();
            block.or(lightUpdateS2CPacket.getFilledBlockLightMask());
            if (holder.light == null)
                holder.light = new BitSet[]{sky, block};
            else {
                holder.light[0].or(sky);
                holder.light[1].or(block);
            }
            return true;
        }

        if (packet instanceof ChunkDeltaUpdateS2CPacket deltaUpdateS2CPacket) {
            Short2ObjectMap<BlockState> new_list = new Short2ObjectLinkedOpenHashMap<>();
            AtomicReference<ChunkSectionPos> sectionPos = new AtomicReference<>();
            deltaUpdateS2CPacket.visitUpdates(((blockPos, blockState) -> {
                sectionPos.set(ChunkSectionPos.from(blockPos));
                new_list.put(ChunkSectionPos.packLocal(blockPos), blockState);
            }));
            int sectionIndex = sectionPos.get().getSectionY();
            Short2ObjectMap<BlockState> old_list = holder.deltaUpdates.get(sectionIndex);
            if (old_list == null) {
                holder.deltaUpdates.put(sectionIndex, new_list);
            } else {
                new_list.forEach((k, v) -> old_list.merge(k, v, (v1, v2) -> v2));
            }
            return true;
        }

        if (packet instanceof BlockEventS2CPacket blockEventS2CPacket) {
            holder.blockEvents.add(new BlockEventWrapper(blockEventS2CPacket));
            return true;
        }

        if (packet instanceof BlockBreakingProgressS2CPacket breakingProgressS2CPacket) {
            holder.blockBreaking.put(breakingProgressS2CPacket.getPos(), breakingProgressS2CPacket);
            return true;
        }

        return false;
    }

    public static void flushPackets(Map<ChunkPos, ChunkPacketHolder> chunkMap, RegistryKey<World> world_key, Consumer<Packet<?>> sender) {
        World world = ReducedPackets.getMs().getWorld(world_key);
        if (world == null)
            throw new NullPointerException("Current World does not exist??");

        //remove dummy chunk
        chunkMap.remove(null);
        //send all unload packets
        chunkMap.values().stream()
                .map(ChunkPacketHolder::getUnloadPacket)
                .filter(Objects::nonNull)
                .forEach(sender);

        Collection<ChunkPacketHolder> loaded_holders = chunkMap.values().stream()
                .filter(ChunkPacketHolder::isLoaded)
                .toList();

        //send all Load packets
        loaded_holders.stream()
                .map(ChunkPacketHolder::getLoadPacket)
                .filter(Objects::nonNull)
                .forEach(sender);
        //trigger explosions
        loaded_holders.stream()
                .map(ChunkPacketHolder::getExplosions)
                .flatMap(Collection::stream)
                .distinct()
                .filter(Objects::nonNull)
                .forEach(sender);
        //send all delta updates
        loaded_holders.stream()
                .map(ChunkPacketHolder::getDeltaUpdates)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .forEach(sender);
        //send all light updates
        loaded_holders.stream()
                .map(holder -> holder.getLight(world.getLightingProvider()))
                .filter(Objects::nonNull)
                .forEach(sender);
        //send all block events
        loaded_holders.stream()
                .map(ChunkPacketHolder::getBlockEvents)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .forEach(sender);
        //send all breaking updates
        loaded_holders.stream()
                .map(ChunkPacketHolder::getBlockBreaking)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .forEach(sender);

        chunkMap.clear();
    }

    private void addExplosionDelta(BlockPos blockPos, World world) {
        ChunkSectionPos sectionPos = ChunkSectionPos.from(blockPos);
        int sectionIndex = sectionPos.getSectionY();
        short coord = ChunkSectionPos.packLocal(blockPos);
        BlockState blockState = world.getBlockState(blockPos);
        this.deltaUpdates.computeIfAbsent(sectionIndex, integer -> new Short2ObjectLinkedOpenHashMap<>()).put(coord, blockState);
    }

    public ChunkPos getPos() {
        return pos;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public ChunkDataS2CPacket getLoadPacket() {
        return loadPacket;
    }

    public UnloadChunkS2CPacket getUnloadPacket() {
        return unloadPacket;
    }

    public Collection<ChunkDeltaUpdateS2CPacket> getDeltaUpdates() {
        return deltaUpdates.int2ObjectEntrySet().stream().map(shortSetEntry -> {
            ChunkSectionPos sectionPos = ChunkSectionPos.from(pos, shortSetEntry.getIntKey());
            return ChunkUtils.customChunkDelta(sectionPos, this.deltaUpdates.get(sectionPos.getSectionY()), true);
        }).collect(Collectors.toList());
    }

    public LightUpdateS2CPacket getLight(LightingProvider lightingProvider) {
        if (this.light == null)
            return null;
        return new LightUpdateS2CPacket(this.getPos(), lightingProvider, this.light[0], this.light[1], false);
    }

    public Collection<BlockEventS2CPacket> getBlockEvents() {
        return blockEvents.stream().map(BlockEventWrapper::getPacket).collect(Collectors.toList());
    }

    public Collection<BlockBreakingProgressS2CPacket> getBlockBreaking() {
        return blockBreaking.values();
    }

    public Collection<ExplosionS2CPacket> getExplosions() {
        return explosions;
    }

    private static class BlockEventWrapper {
        public final BlockEventS2CPacket packet;

        public BlockEventWrapper(BlockEventS2CPacket packet) {
            this.packet = packet;
        }

        public BlockEventS2CPacket getPacket() {
            return packet;
        }

        @Override
        public int hashCode() {
            return packet.getPos().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof BlockEventWrapper wrapper) {
                if (!this.packet.getPos().equals(wrapper.packet.getPos()))
                    return false;
                if (!Registry.BLOCK.getId(this.packet.getBlock()).equals(Registry.BLOCK.getId(wrapper.packet.getBlock())))
                    return false;
                if (this.packet.getType() != wrapper.packet.getType())
                    return false;
                return this.packet.getData() == wrapper.packet.getData();
            } else
                return false;
        }
    }
}
