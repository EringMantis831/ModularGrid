package com.modulargrid.common.channel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A channel a modpack author declares in {@code config/modulargrid/firm_channel.json}.
 *
 * <ul>
 *     <li>a <b>public</b> data channel - one shared pool, every machine allowed to use it may read and write it,</li>
 *     <li>a <b>per player</b> data channel ({@code isPublic: false}) - every player gets their own instance,</li>
 *     <li>an <b>energy</b> channel - either one shared pool or, when {@code isPublic: false}, the energy channel of
 *         every single player, which occupies that player's one energy channel slot.</li>
 * </ul>
 */
public final class FirmChannelDefinition {

    /**
     * Owner the firm channels are stored under. No real player can have this UUID, so firm channel storage can never
     * collide with a player's own channels even though both live in the same map.
     */
    public static final UUID FIRM_OWNER = new UUID(0L, 0L);

    /** Prefix a bare machine name is completed with: the only machines that exist are Modular Machinery's. */
    public static final String MACHINE_NAMESPACE = "modularmachinery";

    /** Language key a firm channel is looked up under before its configured text is used. */
    public static final String LOCALIZATION_PREFIX = "modulargrid.channel.";

    /** Firm channel ids are script identifiers, so they are stricter than an ordinary channel name. */
    public static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,31}");

    public static final int MAX_ID_LENGTH = 32;

    private final String channelId;
    private final String channelLocalization;
    private final boolean publicChannel;
    private final boolean energy;
    private final List<String> masterTo;
    private final boolean masterToAll;

    private FirmChannelDefinition(String channelId, String channelLocalization, boolean publicChannel, boolean energy,
                                  List<String> masterTo, boolean masterToAll) {
        this.channelId = channelId;
        this.channelLocalization = channelLocalization;
        this.publicChannel = publicChannel;
        this.energy = energy;
        this.masterTo = Collections.unmodifiableList(masterTo);
        this.masterToAll = masterToAll;
    }

    /**
     * Validates and normalizes one definition.
     *
     * @param masterTo machine names the channel is limited to; expects already normalized entries
     * @param masterToAll {@code true} when the list contained an empty entry, which means "every machine"
     * @return the definition, or {@code null} when the id is not usable
     */
    @Nullable
    public static FirmChannelDefinition create(String channelId, @Nullable String channelLocalization,
                                               boolean publicChannel, boolean energy, List<String> masterTo,
                                               boolean masterToAll) {
        if (channelId == null || !isValidId(channelId)) {
            return null;
        }
        return new FirmChannelDefinition(channelId, channelLocalization == null ? "" : channelLocalization,
                publicChannel, energy, new ArrayList<>(new LinkedHashSet<>(masterTo)), masterToAll);
    }

    /**
     * @return whether the id can be used as a firm channel id and a script variable name
     */
    public static boolean isValidId(@Nullable String channelId) {
        return channelId != null && ID_PATTERN.matcher(channelId).matches();
    }

    /**
     * Completes a machine name the way the configuration allows it to be written: bare names
     * ({@code alloy_furnace}) are the norm, a full name ({@code modularmachinery:alloy_furnace}) is accepted too.
     *
     * @return the full registry name, or {@code null} for an empty entry (which means "every machine")
     */
    @Nullable
    public static String normalizeMachineName(String machineName) {
        if (machineName == null) {
            return null;
        }
        String trimmed = machineName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.indexOf(':') < 0 ? MACHINE_NAMESPACE + ':' + trimmed : trimmed;
    }

    public String getChannelId() {
        return channelId;
    }

    /** @return the text from the configuration, may be empty or a language key */
    public String getChannelLocalization() {
        return channelLocalization;
    }

    /** @return the language key this channel is looked up under first, see {@link #LOCALIZATION_PREFIX} */
    public String getConventionalLocalizationKey() {
        return LOCALIZATION_PREFIX + channelId;
    }

    public boolean isPublic() {
        return publicChannel;
    }

    public boolean isEnergy() {
        return energy;
    }

    /** @return {@code true} when the channel is limited to a set of machines instead of being open to all */
    public boolean isMachineRestricted() {
        return !energy && !masterToAll && !masterTo.isEmpty();
    }

    /** @return the machines the channel is limited to, empty for an unrestricted one */
    public List<String> getMasterTo() {
        return masterTo;
    }

    /**
     * @param machineRegistryName full registry name of the machine, {@code null} while the plug is not part of one
     * @return whether that machine may use this channel
     */
    public boolean allowsMachine(@Nullable String machineRegistryName) {
        if (energy || masterToAll || masterTo.isEmpty()) {
            return true;
        }
        return machineRegistryName != null && masterTo.contains(machineRegistryName);
    }

    /** @return the set form of {@link #getMasterTo()}, for logs and tests */
    public Set<String> getMasterToSet() {
        return new LinkedHashSet<>(masterTo);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FirmChannelDefinition definition && channelId.equals(definition.channelId);
    }

    @Override
    public int hashCode() {
        return channelId.hashCode();
    }

    @Override
    public String toString() {
        return "FirmChannel{" + channelId + ", public=" + publicChannel + ", energy=" + energy
                + ", masterTo=" + (masterToAll || masterTo.isEmpty() ? "*" : masterTo) + '}';
    }
}
