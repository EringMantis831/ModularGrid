package com.modulargrid.common.util;

/**
 * Conversions between a channel's unbounded {@code long} energy pool and the {@code int} based
 * Forge Energy API.
 */
public final class EnergyIo {

    private EnergyIo() {
    }

    /**
     * Saturating {@code long -> int} conversion of an energy amount.
     *
     * @return the value, clamped to {@code [0, Integer.MAX_VALUE]}
     */
    public static int toInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /**
     * How much of {@code wanted} a pool currently holding {@code current} can still absorb. The pool
     * saturates at {@link Long#MAX_VALUE}, so a nearly full pool accepts less than asked for.
     */
    public static long accepted(long current, long wanted) {
        if (wanted <= 0L) {
            return 0L;
        }
        long space = current >= 0L ? Long.MAX_VALUE - current : Long.MAX_VALUE;
        return Math.min(wanted, space);
    }

    /**
     * The amount a pool gained, given its values before and after the write. Used instead of trusting
     * the requested amount, so saturation can never be reported as a successful transfer.
     */
    public static int gained(long before, long after) {
        return toInt(after - before);
    }

    /**
     * How much of an unbounded pool may be moved in one go: the room left in the pool, limited by {@code limit} and
     * by {@code cap}.
     *
     * @return the amount, never negative and never larger than any of the three bounds
     */
    public static long request(long stored, long limit, long cap) {
        if (limit <= 0L || cap <= 0L) {
            return 0L;
        }
        return Math.min(accepted(stored, Long.MAX_VALUE), Math.min(limit, cap));
    }
}
