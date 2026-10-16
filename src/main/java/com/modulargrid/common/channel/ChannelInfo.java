package com.modulargrid.common.channel;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Immutable snapshot of a channel as shown in the access plug GUI.
 */
public final class ChannelInfo {

    private static final String NBT_ADDRESS = "address";
    private static final String NBT_NAME = "name";
    private static final String NBT_OWNER = "owner";
    private static final String NBT_VALUE = "value";
    private static final String NBT_EXACT_VALUE = "exactValue";
    private static final String NBT_ENERGY = "energy";
    private static final String NBT_PUBLIC = "public";
    private static final String NBT_ENABLED = "enabled";
    private static final String NBT_FIRM = "firm";
    private static final String NBT_FIRM_LOCALIZATION = "firmLocalization";
    private static final String NBT_ACTIVE_ENERGY = "activeEnergy";

    private final String address;
    private final String name;
    private final String ownerName;
    private final String valueDisplay;
    private final String exactValueDisplay;
    private final boolean energy;
    private final boolean publicChannel;
    private final boolean enabled;
    private final boolean firm;
    private final String firmLocalization;
    private final boolean activeEnergy;

    public ChannelInfo(Channel channel, boolean enabled, boolean activeEnergy) {
        this.address = channel.getAddress();
        boolean firmChannel = channel.isFirm();
        FirmChannelDefinition definition = firmChannel
                ? FirmChannelConfig.find(channel.getFirmChannelId()) : null;
        // a firm channel is shown by its definition id: the storage key of a per player instance carries a UUID
        this.name = definition != null ? definition.getChannelId() : channel.getName();
        this.ownerName = channel.getOwnerName();
        this.valueDisplay = channel.getValueDisplay();
        this.exactValueDisplay = channel.getExactValueDisplay();
        this.energy = channel.isEnergy();
        this.publicChannel = channel.isPublic();
        this.enabled = enabled;
        this.firm = firmChannel;
        this.firmLocalization = definition == null ? "" : definition.getChannelLocalization();
        this.activeEnergy = activeEnergy;
    }

    private ChannelInfo(String address, String name, String ownerName, String valueDisplay, String exactValueDisplay,
                        boolean energy, boolean publicChannel, boolean enabled, boolean firm, String firmLocalization,
                        boolean activeEnergy) {
        this.address = address;
        this.name = name;
        this.ownerName = ownerName;
        this.valueDisplay = valueDisplay;
        this.exactValueDisplay = exactValueDisplay;
        this.energy = energy;
        this.publicChannel = publicChannel;
        this.enabled = enabled;
        this.firm = firm;
        this.firmLocalization = firmLocalization;
        this.activeEnergy = activeEnergy;
    }

    public boolean isFirm() {
        return firm;
    }

    /**
     * @return whether this is the channel the machine currently draws energy from, which is not always the one a player
     *         would expect: a firm energy channel outranks their own, and the machine has exactly one energy channel
     */
    public boolean isActiveEnergy() {
        return activeEnergy;
    }

    /**
     * @return the text the pack configured for a firm channel, may be empty or a language key. Resolving it is up to
     *         the client, see the language key convention in {@link FirmChannelDefinition#LOCALIZATION_PREFIX}.
     */
    public String getFirmLocalization() {
        return firmLocalization;
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    public String getOwnerName() {
        return ownerName;
    }

    /** Value as text for a channel row, exact while it fits, see {@link Channel#getValueDisplay()}. */
    public String getValueDisplay() {
        return valueDisplay;
    }

    /** Value as text for the details panel, always the whole value, see {@link Channel#getExactValueDisplay()}. */
    public String getExactValueDisplay() {
        return exactValueDisplay;
    }

    public boolean isEnergy() {
        return energy;
    }

    public boolean isPublic() {
        return publicChannel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return a copy with the given enable state
     */
    public ChannelInfo withEnabled(boolean enabled) {
        return this.enabled == enabled ? this
                : new ChannelInfo(address, name, ownerName, valueDisplay, exactValueDisplay, energy, publicChannel,
                        enabled, firm, firmLocalization, activeEnergy);
    }

    /**
     * Two entries are considered the same when everything but the value matches, which is what decides
     * whether the access plug has to push a new copy to its viewers.
     */
    public boolean sameStructure(ChannelInfo other) {
        return other != null && energy == other.energy && publicChannel == other.publicChannel
                && enabled == other.enabled && firm == other.firm && activeEnergy == other.activeEnergy
                && address.equals(other.address) && name.equals(other.name)
                && ownerName.equals(other.ownerName) && firmLocalization.equals(other.firmLocalization);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(NBT_ADDRESS, address);
        tag.setString(NBT_NAME, name);
        tag.setString(NBT_OWNER, ownerName);
        tag.setString(NBT_VALUE, valueDisplay);
        tag.setString(NBT_EXACT_VALUE, exactValueDisplay);
        tag.setBoolean(NBT_ENERGY, energy);
        tag.setBoolean(NBT_PUBLIC, publicChannel);
        tag.setBoolean(NBT_ENABLED, enabled);
        tag.setBoolean(NBT_FIRM, firm);
        tag.setString(NBT_FIRM_LOCALIZATION, firmLocalization);
        tag.setBoolean(NBT_ACTIVE_ENERGY, activeEnergy);
        return tag;
    }

    public static ChannelInfo readFromNBT(NBTTagCompound tag) {
        String valueDisplay = tag.getString(NBT_VALUE);
        String exactValueDisplay = tag.getString(NBT_EXACT_VALUE);
        return new ChannelInfo(tag.getString(NBT_ADDRESS), tag.getString(NBT_NAME), tag.getString(NBT_OWNER),
                valueDisplay, exactValueDisplay.isEmpty() ? valueDisplay : exactValueDisplay,
                tag.getBoolean(NBT_ENERGY), tag.getBoolean(NBT_PUBLIC), tag.getBoolean(NBT_ENABLED),
                tag.getBoolean(NBT_FIRM), tag.getString(NBT_FIRM_LOCALIZATION), tag.getBoolean(NBT_ACTIVE_ENERGY));
    }

    @Override
    public String toString() {
        return "ChannelInfo{" + address + ", energy=" + energy + ", public=" + publicChannel
                + ", firm=" + firm + ", enabled=" + enabled + ", activeEnergy=" + activeEnergy
                + ", value=" + valueDisplay + '}';
    }

}
