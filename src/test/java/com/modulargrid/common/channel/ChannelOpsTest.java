package com.modulargrid.common.channel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChannelOps}: what {@code addDataConfig} does to a channel.
 */
class ChannelOpsTest {

    private static final UUID OWNER = UUID.fromString("a01a21f5-c117-3c1f-b5d0-e055b927f733");

    private static Channel energy() {
        return new Channel("grid", OWNER, "Developer", false, true);
    }

    private static Channel data() {
        return new Channel("temp", OWNER, "Developer", false, false);
    }

    @Test
    void operationNumbersCoverTheThreeOperationsOnly() {
        assertTrue(ChannelOps.isValid(ChannelOps.OPERATION_ADD));
        assertTrue(ChannelOps.isValid(ChannelOps.OPERATION_MULTIPLY));
        assertTrue(ChannelOps.isValid(ChannelOps.OPERATION_SET));
        assertFalse(ChannelOps.isValid(3));
        assertFalse(ChannelOps.isValid(-1));
        // the numbers are the ones Modular Machinery uses for its modifiers, which is why they are worth pinning
        assertEquals(0, ChannelOps.OPERATION_ADD);
        assertEquals(1, ChannelOps.OPERATION_MULTIPLY);
        assertEquals(2, ChannelOps.OPERATION_SET);
    }

    @Test
    void dataChannelAddsMultipliesAndSets() {
        Channel channel = data();
        ChannelOps.apply(channel, 500, ChannelOps.OPERATION_ADD);
        assertEquals(500.0F, channel.getFloatValue());
        ChannelOps.apply(channel, -100, ChannelOps.OPERATION_ADD);
        assertEquals(400.0F, channel.getFloatValue());
        ChannelOps.apply(channel, 1.5D, ChannelOps.OPERATION_MULTIPLY);
        assertEquals(600.0F, channel.getFloatValue());
        ChannelOps.apply(channel, 2.5D, ChannelOps.OPERATION_SET);
        assertEquals(2.5F, channel.getFloatValue());
    }

    @Test
    void energyChannelKeepsWholeValues() {
        Channel channel = energy();
        ChannelOps.apply(channel, 5_000_000_000L, ChannelOps.OPERATION_ADD);
        assertEquals(5_000_000_000L, channel.getEnergyValue());
        ChannelOps.apply(channel, 1_000_000_000L, ChannelOps.OPERATION_ADD);
        assertEquals(6_000_000_000L, channel.getEnergyValue());
        ChannelOps.apply(channel, 2.0D, ChannelOps.OPERATION_MULTIPLY);
        assertEquals(12_000_000_000L, channel.getEnergyValue());
        ChannelOps.apply(channel, 7L, ChannelOps.OPERATION_SET);
        assertEquals(7L, channel.getEnergyValue());
    }

    @Test
    void energyChannelNeverGoesNegativeAndNeverWrapsAround() {
        Channel channel = energy();
        ChannelOps.apply(channel, -500, ChannelOps.OPERATION_ADD);
        assertEquals(0L, channel.getEnergyValue());

        channel.setEnergyValue(100L);
        ChannelOps.apply(channel, -1000, ChannelOps.OPERATION_ADD);
        assertEquals(0L, channel.getEnergyValue());

        channel.setEnergyValue(Long.MAX_VALUE - 5L);
        ChannelOps.apply(channel, 100L, ChannelOps.OPERATION_ADD);
        assertEquals(Long.MAX_VALUE, channel.getEnergyValue());

        channel.setEnergyValue(Long.MAX_VALUE);
        ChannelOps.apply(channel, 2.0D, ChannelOps.OPERATION_MULTIPLY);
        assertEquals(Long.MAX_VALUE, channel.getEnergyValue());

        // a fractional amount is truncated, it cannot make the pool hold part of an RF
        channel.setEnergyValue(0L);
        ChannelOps.apply(channel, 2.9D, ChannelOps.OPERATION_ADD);
        assertEquals(2L, channel.getEnergyValue());
    }

    @Test
    void settingsBeyondTheLongRangeSaturateInsteadOfWrapping() {
        Channel channel = energy();
        ChannelOps.apply(channel, 1.0E30D, ChannelOps.OPERATION_SET);
        assertEquals(Long.MAX_VALUE, channel.getEnergyValue());
        ChannelOps.apply(channel, -1.0E30D, ChannelOps.OPERATION_SET);
        assertEquals(Long.MIN_VALUE, channel.getEnergyValue());
    }

}
