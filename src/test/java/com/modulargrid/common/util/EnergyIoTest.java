package com.modulargrid.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code long <-> int} boundary of the channel energy pool: narrow conversions must saturate,
 * never wrap, because Forge Energy is {@code int} based while the pool is unbounded.
 */
class EnergyIoTest {

    @Test
    void toIntSaturatesBothBounds() {
        assertEquals(0, EnergyIo.toInt(Long.MIN_VALUE));
        assertEquals(0, EnergyIo.toInt(-1L));
        assertEquals(0, EnergyIo.toInt(0L));
        assertEquals(1, EnergyIo.toInt(1L));
        assertEquals(1234567, EnergyIo.toInt(1234567L));
        assertEquals(Integer.MAX_VALUE, EnergyIo.toInt(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, EnergyIo.toInt((long) Integer.MAX_VALUE + 1L));
        assertEquals(Integer.MAX_VALUE, EnergyIo.toInt(Long.MAX_VALUE));
    }

    @Test
    void acceptedStopsAtPoolSaturation() {
        assertEquals(0L, EnergyIo.accepted(0L, 0L));
        assertEquals(0L, EnergyIo.accepted(0L, -5L));
        assertEquals(100L, EnergyIo.accepted(0L, 100L));
        assertEquals(10L, EnergyIo.accepted(Long.MAX_VALUE - 10L, 100L));
        assertEquals(0L, EnergyIo.accepted(Long.MAX_VALUE, 100L));
        assertEquals(100L, EnergyIo.accepted(-1L, 100L));
        assertEquals(Long.MAX_VALUE, EnergyIo.accepted(0L, Long.MAX_VALUE));
    }

    @Test
    void gainedReportsTheRealDelta() {
        assertEquals(0, EnergyIo.gained(0L, 0L));
        assertEquals(7, EnergyIo.gained(5L, 12L));
        assertEquals(3, EnergyIo.gained(Long.MAX_VALUE - 3L, Long.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, EnergyIo.gained(0L, Long.MAX_VALUE));
        // a refused write (pool already saturated) reports nothing
        assertEquals(0, EnergyIo.gained(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void requestIsBoundedByRoomLimitAndCap() {
        long cap = 1L << 58;
        // an empty pool with no limit configured may move the caller's cap
        assertEquals(cap, EnergyIo.request(0L, Long.MAX_VALUE, cap));
        // the per connection limit wins when it is the smallest bound
        assertEquals(1000L, EnergyIo.request(0L, 1000L, cap));
        // the room left in the pool wins near saturation
        assertEquals(5L, EnergyIo.request(Long.MAX_VALUE - 5L, Long.MAX_VALUE, cap));
        assertEquals(0L, EnergyIo.request(Long.MAX_VALUE, Long.MAX_VALUE, cap));
        // a limit of zero or less means "do not move anything"
        assertEquals(0L, EnergyIo.request(0L, 0L, cap));
        assertEquals(0L, EnergyIo.request(0L, -1L, cap));
        // a caller without room to spare gets nothing
        assertEquals(0L, EnergyIo.request(0L, Long.MAX_VALUE, 0L));
        // a negative pool counts as empty, like Channel#takeEnergy does
        assertEquals(cap, EnergyIo.request(-1L, Long.MAX_VALUE, cap));
    }
}
