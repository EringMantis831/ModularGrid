package com.modulargrid.common.channel;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value precision of {@link Channel}: energy channels must survive the whole {@code long} range and
 * normal channels must survive the whole {@code float} range, including persistence round trips.
 *
 * <p>These tests exist because the payload types were changed from {@code double} to {@code long}
 * (energy) and {@code float} (normal): a {@code double} silently rounds 2^63-1 to 2^63, and the
 * regression must not come back unnoticed.</p>
 */
class ChannelTest {

    private static final UUID OWNER = UUID.fromString("a01a21f5-c117-3c1f-b5d0-e055b927f733");

    private static Channel energyChannel() {
        return new Channel("energy", OWNER, "Developer", false, true);
    }

    private static Channel floatChannel() {
        return new Channel("data", OWNER, "Developer", false, false);
    }

    @Test
    void doubleWouldNotHoldEnergyValuesExactly() {
        long aboveMantissa = 9007199254740993L; // 2^53 + 1
        // a double has a 53 bit mantissa, so this value cannot be represented and the cast rounds it away
        assertEquals((double) aboveMantissa, (double) (aboveMantissa - 1L));
        assertNotEquals(aboveMantissa, (long) (double) aboveMantissa);
        // the long payload keeps them apart
        Channel channel = energyChannel();
        channel.setEnergyValue(aboveMantissa);
        assertEquals(aboveMantissa, channel.getEnergyValue());
        assertNotEquals((long) (double) aboveMantissa, channel.getEnergyValue());
    }

    @Test
    void energyChannelKeepsFullLongPrecision() {
        Channel channel = energyChannel();
        for (long value : new long[]{0L, 1L, 9007199254740993L, Long.MAX_VALUE, Long.MIN_VALUE, -1L}) {
            channel.setEnergyValue(value);
            assertEquals(value, channel.getEnergyValue());
        }
    }

    @Test
    void shortValuesAreShownExactAndLongOnesShortened() {
        Channel channel = energyChannel();
        channel.setEnergyValue(999999999L); // nine digits, still exact next to the unit
        assertEquals("999999999", channel.getValueDisplay());

        channel.setEnergyValue(1000000000L); // ten digits, shortened so the unit keeps its room
        assertEquals("1.00e9", channel.getValueDisplay());

        channel.setEnergyValue(822049568123L);
        assertEquals("8.22e11", channel.getValueDisplay());

        channel.setEnergyValue(Long.MAX_VALUE);
        assertEquals("9.22e18", channel.getValueDisplay());

        channel.setEnergyValue(Long.MIN_VALUE);
        assertEquals("-9.22e18", channel.getValueDisplay());
    }

    @Test
    void theExactFormKeepsEveryDigitOfAnEnergyValue() {
        Channel channel = energyChannel();
        channel.setEnergyValue(Long.MAX_VALUE);
        assertEquals("9223372036854775807", channel.getExactValueDisplay());
        assertEquals("9.22e18", channel.getValueDisplay());

        channel.setEnergyValue(822049568123L);
        assertEquals("822049568123", channel.getExactValueDisplay());
    }

    @Test
    void energyValueSurvivesPersistence() {
        Channel channel = energyChannel();
        channel.setEnergyValue(Long.MAX_VALUE);
        Channel restored = Channel.readFromNBT(channel.getName(), OWNER, channel.writeToNBT());
        assertTrue(restored.isEnergy());
        assertEquals(Long.MAX_VALUE, restored.getEnergyValue());

        channel.setEnergyValue(Long.MIN_VALUE);
        restored = Channel.readFromNBT(channel.getName(), OWNER, channel.writeToNBT());
        assertEquals(Long.MIN_VALUE, restored.getEnergyValue());
    }

    @Test
    void floatChannelKeepsFullFloatPrecision() {
        Channel channel = floatChannel();
        for (float value : new float[]{0.0F, 0.1F, 123.456F, Float.MAX_VALUE, Float.MIN_VALUE, -1.5F,
                Float.intBitsToFloat(0x7F7FFFFF)}) {
            channel.setFloatValue(value);
            assertEquals(Float.floatToIntBits(value), Float.floatToIntBits(channel.getFloatValue()));
        }
    }

    @Test
    void floatChannelShortensLargeValuesTheSameWay() {
        Channel channel = floatChannel();
        channel.setFloatValue(123.456F);
        assertEquals("123.456", channel.getValueDisplay());
        assertEquals("123.456", channel.getExactValueDisplay());

        channel.setFloatValue(1.0E9F);
        assertEquals("1.00e9", channel.getValueDisplay());

        channel.setFloatValue(Float.MAX_VALUE);
        assertEquals("3.40e38", channel.getValueDisplay());
    }

    @Test
    void floatValueSurvivesPersistence() {
        Channel channel = floatChannel();
        channel.setFloatValue(123.456F);
        Channel restored = Channel.readFromNBT(channel.getName(), OWNER, channel.writeToNBT());
        assertFalse(restored.isEnergy());
        assertEquals(Float.floatToIntBits(123.456F), Float.floatToIntBits(restored.getFloatValue()));
    }

    @Test
    void energyPoolDoesNotGoNegative() {
        Channel channel = energyChannel();
        channel.setEnergyValue(100L);
        assertEquals(100L, channel.takeEnergy(1000L));
        assertEquals(0L, channel.getEnergyValue());
        assertEquals(0L, channel.takeEnergy(5L));
        channel.giveEnergy(-5L);
        assertEquals(0L, channel.getEnergyValue());
        channel.giveEnergy(7L);
        assertEquals(7L, channel.getEnergyValue());
    }

    @Test
    void energyPoolSaturatesInsteadOfOverflowing() {
        Channel channel = energyChannel();
        channel.setEnergyValue(Long.MAX_VALUE);
        channel.giveEnergy(100L);
        assertEquals(Long.MAX_VALUE, channel.getEnergyValue());

        channel.setEnergyValue(Long.MAX_VALUE - 5L);
        channel.giveEnergy(100L);
        assertEquals(Long.MAX_VALUE, channel.getEnergyValue());

        channel.setEnergyValue(0L);
        channel.giveEnergy(Long.MAX_VALUE);
        channel.giveEnergy(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, channel.getEnergyValue());
    }

    @Test
    void valuesWrittenAsDoubleByOlderVersionsAreStillReadable() {
        NBTTagCompound energyTag = new NBTTagCompound();
        energyTag.setBoolean("energy", true);
        energyTag.setDouble("value", 1234.75D);
        assertEquals(1234L, Channel.readFromNBT("energy", OWNER, energyTag).getEnergyValue());

        NBTTagCompound dataTag = new NBTTagCompound();
        dataTag.setDouble("value", 1234.5D);
        assertEquals(1234.5F, Channel.readFromNBT("data", OWNER, dataTag).getFloatValue());
    }

    @Test
    void nameValidation() {
        assertTrue(Channel.isValidName("abc_123"));
        assertTrue(Channel.isValidName("测试频道"));
        assertFalse(Channel.isValidName(""));
        assertFalse(Channel.isValidName("bad name"));
        assertFalse(Channel.isValidName("bad/name"));
        assertFalse(Channel.isValidName("a".repeat(Channel.MAX_NAME_LENGTH + 1)));
    }

}
