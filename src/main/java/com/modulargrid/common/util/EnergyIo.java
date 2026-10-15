package com.modulargrid.common.util;

/**
 * Conversions between a channel's unbounded {@code long} energy pool and the {@code int} based
 * Forge Energy API.
 *
 * <p>{@link net.minecraftforge.energy.IEnergyStorage} can only move {@code int} amounts, while a
 * channel holds a {@code long} (see {@link com.modulargrid.common.channel.Channel}). Every
 * conversion therefore has to saturate instead of wrapping: a pool of {@code Long.MAX_VALUE} RF must
 * report {@code Integer.MAX_VALUE} stored energy and a single call must be able to move at most
 * {@code Integer.MAX_VALUE} RF, but the pool itself must stay untouched by the narrowing.</p>
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
     *
     * <p>A negative pool (possible through {@code /modulargrid set}) is treated as empty, which is
     * also how {@link com.modulargrid.common.channel.Channel#takeEnergy} behaves.</p>
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
     * <p>Used by the wireless network integrations, which have to tell a network how much they would accept in the
     * current cycle. A pool has no capacity of its own, so the interesting bound is usually the per connection limit
     * the player configured; {@code cap} is the caller's own upper bound (a network that adds the requests of all its
     * connectors together needs one, or that sum could overflow).</p>
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
