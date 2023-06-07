package com.mattymatty.carpet_reducedpackets.utils;

import carpet.helpers.TickSpeed;
import com.mattymatty.carpet_reducedpackets.ReducedPackets;
import net.minecraft.util.math.MathHelper;

public class TickUtils {

    public static long NORMAL_MSPT = 50L;

    public static double getTPS() {
        double MSPT = getMSPT();
        return 1000.0D / Math.max((TickSpeed.time_warp_start_time != 0) ? 0.0 : TickSpeed.mspt, MSPT);
    }

    public static double getMSPT() {
        return MathHelper.average(ReducedPackets.getMs().lastTickLengths) * 1.0E-6D;
    }
}
