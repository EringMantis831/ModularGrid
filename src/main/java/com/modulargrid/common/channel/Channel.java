package com.modulargrid.common.channel;

import net.minecraft.nbt.NBTTagCompound;

import com.modulargrid.common.util.ValueFormat;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * A ModularGrid channel: a named, globally persisted slot owned by a player.
 *
 * <p>Channels are addressed internally as {@code ownerUUID/name} and are stored in
 * {@link ChannelWorldData}, so their contents are global (cross dimension).</p>
 *
 * <p>The stored value depends on the channel type and is never routed through {@code double}:
 * an <b>energy</b> channel holds a {@code long} (exact for the whole {@code long} range, which is
 * also what Modular Machinery uses for RF internally, {@code 1 RF == 1}), every other channel holds
 * a {@code float}. Parsing, persistence and display all use the same type, so the only precision
 * limit is the one inherent to the type itself.</p>
 *
 * <p>The energy pool is an {@link AtomicLong} because Modular Machinery may read and write it from
 * its recipe thread: {@link #takeEnergy} and {@link #giveEnergy} are atomic, and adding saturates
 * at {@link Long#MAX_VALUE} instead of overflowing. The {@code float} of a data channel is volatile
 * for the same reason, and its read-modify-write operations are synchronized.</p>
 */
public class Channel {

    /** Maximum length of a channel name, see {@link #NAME_PATTERN}. */
    public static final int MAX_NAME_LENGTH = 24;

    /** Allowed channel names: letters, digits, underscore and CJK, 1..24 characters. */
    public static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_\\u4e00-\\u9fa5]{1," + MAX_NAME_LENGTH + "}");

    /** Maximum amount of channels a single player may own. */
    public static final int MAX_CHANNELS_PER_OWNER = 16;

    private static final String NBT_PUBLIC = "public";
    private static final String NBT_ENERGY = "energy";
    private static final String NBT_VALUE = "value";
    private static final String NBT_OWNER_NAME = "ownerName";

    private final String name;
    private final UUID owner;
    private String ownerName;
    private final boolean energy;
    private boolean publicChannel;
    private final AtomicLong energyValue = new AtomicLong();
    private volatile float floatValue;

    public Channel(String name, UUID owner, String ownerName, boolean publicChannel, boolean energy) {
        this.name = name;
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.publicChannel = publicChannel;
        this.energy = energy;
    }

    // ------------------------------------------------------------------ names

    /**
     * @return {@code true} if the given name matches {@link #NAME_PATTERN}
     */
    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    // ----------------------------------------------------------------- getters

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    /** Last known name of the owning player, used for display when they are offline. */
    public String getOwnerName() {
        return ownerName;
    }

    public boolean isPublic() {
        return publicChannel;
    }

    /** @return {@code true} if this is an energy channel, each player may own at most one. */
    public boolean isEnergy() {
        return energy;
    }

    /** @return the energy pool of an energy channel, {@code 0} for float channels */
    public long getEnergyValue() {
        return energyValue.get();
    }

    /** @return the payload of a normal channel, {@code 0} for energy channels */
    public float getFloatValue() {
        return floatValue;
    }

    /**
     * @return the value as shown in a channel row: exact while it fits next to the unit, in {@code N.NNeNN} form once
     *         it would push the unit out of the row (see {@link ValueFormat}), preserving the type's own precision up
     *         to that point
     */
    public String getValueDisplay() {
        return energy ? ValueFormat.display(getEnergyValue()) : ValueFormat.display(floatValue);
    }

    /**
     * @return the value as shown in the details panel: the whole value, with at most
     *         {@link ValueFormat#MAX_EXACT_DECIMALS} decimals for a fractional channel
     */
    public String getExactValueDisplay() {
        return energy ? ValueFormat.exact(getEnergyValue()) : ValueFormat.exact(floatValue);
    }

    /** @return {@code owner/name}, the unique address of this channel. */
    public String getAddress() {
        return owner + "/" + name;
    }

    /**
     * @return {@code true} when this channel is defined by the pack configuration instead of registered by a player
     */
    public boolean isFirm() {
        return FirmChannelDefinition.FIRM_OWNER.equals(owner);
    }

    /**
     * @return the definition id of a firm channel, {@code null} for a player channel. The {@code @player} suffix of a
     *         per player instance belongs to the storage key only and is stripped here.
     */
    @Nullable
    public String getFirmChannelId() {
        if (!isFirm()) {
            return null;
        }
        int separator = name.indexOf('@');
        return separator < 0 ? name : name.substring(0, separator);
    }

    /**
     * @return the owner UUID of the per player instance, {@code null} for a shared firm channel or a player channel
     */
    @Nullable
    public UUID getFirmPlayer() {
        if (!isFirm()) {
            return null;
        }
        int separator = name.indexOf('@');
        if (separator < 0) {
            return null;
        }
        try {
            return UUID.fromString(name.substring(separator + 1));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ----------------------------------------------------------------- setters

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public void setPublic(boolean publicChannel) {
        this.publicChannel = publicChannel;
    }

    public void setEnergyValue(long value) {
        this.energyValue.set(value);
    }

    public void setFloatValue(float value) {
        this.floatValue = value;
    }

    /**
     * Multiplies an energy channel in place, atomically, saturating at {@link Long#MAX_VALUE} and never going below
     * zero.
     *
     * @return the value after the multiplication
     */
    public long multiplyEnergyValue(double factor) {
        while (true) {
            long current = energyValue.get();
            double product = current * factor;
            long next = product >= (double) Long.MAX_VALUE ? Long.MAX_VALUE
                    : product <= 0.0D ? 0L : (long) product;
            if (energyValue.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    /**
     * Adds to a data channel in place, atomically with respect to other readers and writers (recipe threads included).
     *
     * @return the value after the addition
     */
    public synchronized float addFloatValue(float delta) {
        floatValue += delta;
        return floatValue;
    }

    /**
     * Multiplies a data channel in place, atomically with respect to other readers and writers.
     *
     * @return the value after the multiplication
     */
    public synchronized float multiplyFloatValue(float factor) {
        floatValue *= factor;
        return floatValue;
    }

    /**
     * Removes up to {@code amount} from an energy channel, atomically.
     *
     * @return the amount that was actually removed, {@code 0} if the channel is empty
     */
    public long takeEnergy(long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        while (true) {
            long current = energyValue.get();
            if (current <= 0L) {
                return 0L;
            }
            long taken = Math.min(amount, current);
            if (energyValue.compareAndSet(current, current - taken)) {
                return taken;
            }
        }
    }

    /**
     * Adds {@code amount} to an energy channel, atomically. Non-positive amounts are ignored and the
     * pool saturates at {@link Long#MAX_VALUE} rather than wrapping around, an unbounded pool would
     * otherwise be able to turn into a negative one.
     */
    public void giveEnergy(long amount) {
        if (amount <= 0L) {
            return;
        }
        while (true) {
            long current = energyValue.get();
            long next = Long.MAX_VALUE - current < amount ? Long.MAX_VALUE : current + amount;
            if (energyValue.compareAndSet(current, next)) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------- nbt

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(NBT_PUBLIC, publicChannel);
        tag.setBoolean(NBT_ENERGY, energy);
        if (energy) {
            tag.setLong(NBT_VALUE, getEnergyValue());
        } else {
            tag.setFloat(NBT_VALUE, floatValue);
        }
        tag.setString(NBT_OWNER_NAME, ownerName);
        return tag;
    }

    /**
     * Reads a channel back from its persisted form. The name and owner are part of the
     * storage layout, so they are passed in instead of being stored in the tag.
     *
     * <p>Values written by older versions as {@code double} are read through NBT's numeric
     * coercion, so no migration is needed.</p>
     */
    public static Channel readFromNBT(String name, UUID owner, NBTTagCompound tag) {
        Channel channel = new Channel(name, owner, tag.getString(NBT_OWNER_NAME),
                tag.getBoolean(NBT_PUBLIC), tag.getBoolean(NBT_ENERGY));
        if (channel.isEnergy()) {
            channel.setEnergyValue(tag.getLong(NBT_VALUE));
        } else {
            channel.setFloatValue(tag.getFloat(NBT_VALUE));
        }
        return channel;
    }

    @Override
    public String toString() {
        return "Channel{" + getAddress() + ", public=" + publicChannel + ", energy=" + energy
                + ", value=" + getValueDisplay() + '}';
    }

}
