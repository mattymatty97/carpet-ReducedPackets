package com.mattymatty.carpet_reducedpackets.utils;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.ChunkSectionPos;

public class ChunkUtils {
    public static ChunkDeltaUpdateS2CPacket customChunkDelta(ChunkSectionPos pos, Short2ObjectMap<BlockState> blockStates, boolean noLightingUpdates) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeLong(pos.asLong());
        buf.writeBoolean(noLightingUpdates);
        buf.writeVarInt(blockStates.size());

        blockStates.forEach((localPos, blockState) -> buf.writeVarLong(((long) Block.getRawIdFromState(blockState) << 12 | localPos)));
        return new ChunkDeltaUpdateS2CPacket(buf);
    }
}
