package com.modulargrid.common.channel;

import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

import com.modulargrid.ModularGrid;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Static access point to the global {@link Channel} store.
 *
 * <p>All mutating methods are meant to be called from the server thread. Client side
 * reads return {@code null}, channel data reaches the client through GUI sync instead.</p>
 */
public final class ChannelRegistry {

    /** Result of a {@link #register} attempt, mapped to a message by the command layer. */
    public enum RegisterResult {
        OK,
        INVALID_NAME,
        NAME_TAKEN,
        /** a firm channel of the pack already uses that id, which scripts address by it */
        NAME_FIRM,
        LIMIT_REACHED,
        ENERGY_ALREADY_EXISTS,
        /** the pack configuration forbids player energy channels altogether */
        PLAYER_ENERGY_DISABLED,
        /** the pack configuration already gives this player an energy channel, which occupies their only slot */
        ENERGY_PROVIDED_BY_FIRM,
        UNAVAILABLE
    }

    /** Outcome of {@link #register}, holding the created channel when successful. */
    public static final class RegisterOutcome {

        public static final RegisterOutcome UNAVAILABLE = new RegisterOutcome(RegisterResult.UNAVAILABLE, null);

        private final RegisterResult result;
        private final Channel channel;

        private RegisterOutcome(RegisterResult result, Channel channel) {
            this.result = result;
            this.channel = channel;
        }

        public RegisterResult getResult() {
            return result;
        }

        /** @return the created channel, {@code null} unless the result is {@link RegisterResult#OK} */
        public Channel getChannel() {
            return channel;
        }

        public boolean isSuccess() {
            return result == RegisterResult.OK;
        }
    }

    private static final Comparator<Channel> BY_NAME = Comparator.comparing(Channel::getName);
    private static final Comparator<Channel> BY_OWNER_THEN_NAME =
            Comparator.comparing(Channel::getOwnerName).thenComparing(Channel::getName);

    private ChannelRegistry() {
    }

    // -------------------------------------------------------------- data access

    /**
     * @param world any world of the running server
     * @return the global channel data, {@code null} on the client or without a world
     */
    public static ChannelWorldData getData(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        World overworld = world;
        if (world.provider.getDimension() != 0 && world.getMinecraftServer() != null) {
            World serverOverworld = world.getMinecraftServer().getWorld(0);
            if (serverOverworld != null) {
                overworld = serverOverworld;
            }
        }
        MapStorage storage = overworld.getMapStorage();
        if (storage == null) {
            return null;
        }
        ChannelWorldData data = (ChannelWorldData) storage.getOrLoadData(ChannelWorldData.class, ChannelWorldData.DATA_NAME);
        if (data == null) {
            data = new ChannelWorldData(ChannelWorldData.DATA_NAME);
            storage.setData(ChannelWorldData.DATA_NAME, data);
        }
        return data;
    }

    // ------------------------------------------------------------- registration

    /**
     * Registers a new channel for the given owner.
     *
     * @param ownerName last known name of the owner, stored for offline display
     * @param publicChannel {@code true} if every player may connect to it
     * @param energy {@code true} for the owner's single energy channel
     */
    public static RegisterOutcome register(World world, UUID owner, String ownerName, String name,
                                           boolean publicChannel, boolean energy) {
        ChannelWorldData data = getData(world);
        if (data == null) {
            return RegisterOutcome.UNAVAILABLE;
        }
        if (!Channel.isValidName(name)) {
            return new RegisterOutcome(RegisterResult.INVALID_NAME, null);
        }
        Map<String, Channel> ownerChannels = data.getOrCreateChannels(owner);
        if (ownerChannels.containsKey(name)) {
            return new RegisterOutcome(RegisterResult.NAME_TAKEN, null);
        }
        // a firm channel id is a script variable name, so a player channel of the same name would shadow it
        if (FirmChannelConfig.isFirm(name)) {
            return new RegisterOutcome(RegisterResult.NAME_FIRM, null);
        }
        // firm channels are stored under the firm owner, so they never count against a player's own limit
        if (ownerChannels.size() >= Channel.MAX_CHANNELS_PER_OWNER) {
            return new RegisterOutcome(RegisterResult.LIMIT_REACHED, null);
        }
        if (energy) {
            // the pack may hand out the one energy channel a player has, and may forbid own ones entirely
            if (hasFirmEnergyChannel()) {
                return new RegisterOutcome(RegisterResult.ENERGY_PROVIDED_BY_FIRM, null);
            }
            if (!FirmChannelConfig.isPlayerEnergyChannelsAllowed()) {
                return new RegisterOutcome(RegisterResult.PLAYER_ENERGY_DISABLED, null);
            }
            for (Channel existing : ownerChannels.values()) {
                if (existing.isEnergy()) {
                    return new RegisterOutcome(RegisterResult.ENERGY_ALREADY_EXISTS, null);
                }
            }
        }
        Channel channel = new Channel(name, owner, ownerName, publicChannel, energy);
        ownerChannels.put(name, channel);
        data.markDirty();
        return new RegisterOutcome(RegisterResult.OK, channel);
    }

    /**
     * Deletes one of {@code owner}'s own channels.
     *
     * @return the removed channel, {@code null} if the owner has no channel with that name
     */
    public static Channel delete(World world, UUID owner, String name) {
        ChannelWorldData data = getData(world);
        if (data == null) {
            return null;
        }
        Map<String, Channel> ownerChannels = data.getChannels(owner);
        if (ownerChannels == null) {
            return null;
        }
        Channel removed = ownerChannels.remove(name);
        if (removed != null) {
            data.markDirty();
        }
        return removed;
    }

    // ------------------------------------------------------------------ lookups

    /** @return the owner's channel with that name, {@code null} when absent */
    public static Channel get(World world, UUID owner, String name) {
        ChannelWorldData data = getData(world);
        if (data == null) {
            return null;
        }
        Map<String, Channel> ownerChannels = data.getChannels(owner);
        return ownerChannels == null ? null : ownerChannels.get(name);
    }

    /** @return the owner's energy channel, {@code null} when they have none */
    public static Channel getEnergyChannel(World world, UUID owner) {        ChannelWorldData data = getData(world);
        if (data == null) {
            return null;
        }
        Map<String, Channel> ownerChannels = data.getChannels(owner);
        if (ownerChannels == null) {
            return null;
        }
        for (Channel channel : ownerChannels.values()) {
            if (channel.isEnergy()) {
                return channel;
            }
        }
        return null;
    }

    /**
     * Looks for a channel with that name owned by somebody, used to tell "unknown channel"
     * and "that one is not yours" apart in command feedback.
     *
     * <p>Firm channels are skipped: they belong to nobody, so "not yours" would be the wrong answer for them.</p>
     */
    public static Channel findAny(World world, String name) {
        ChannelWorldData data = getData(world);
        if (data == null) {
            return null;
        }
        for (Map.Entry<UUID, Map<String, Channel>> entry : data.getAllChannels().entrySet()) {
            if (entry.getKey().equals(FirmChannelDefinition.FIRM_OWNER)) {
                continue;
            }
            Channel channel = entry.getValue().get(name);
            if (channel != null) {
                return channel;
            }
        }
        return null;
    }

    /**
     * Looks a channel up by its {@code ownerUUID/name} address.
     *
     * <p>Channel names cannot contain a slash, so the first slash always separates owner and name.</p>
     *
     * @return the channel, {@code null} if the address is malformed or the channel is gone
     */
    public static Channel getByAddress(World world, String address) {
        if (address == null) {
            return null;
        }
        int separator = address.indexOf('/');
        if (separator <= 0 || separator == address.length() - 1) {
            return null;
        }
        UUID owner;
        try {
            owner = UUID.fromString(address.substring(0, separator));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return get(world, owner, address.substring(separator + 1));
    }

    /** @return the owner's channels, sorted by name */
    public static List<Channel> listOwn(World world, UUID owner) {
        ChannelWorldData data = getData(world);
        if (data == null) {
            return Collections.emptyList();
        }
        Map<String, Channel> ownerChannels = data.getChannels(owner);
        if (ownerChannels == null || ownerChannels.isEmpty()) {
            return Collections.emptyList();
        }
        List<Channel> list = new ArrayList<>(ownerChannels.values());
        list.sort(BY_NAME);
        return list;
    }

    /**
     * @return the pack's channels as they apply to one player: their per player instances first, then the shared ones
     *         in configuration order. Instances are created on first listing, see {@link #listAccessible}.
     */
    public static List<Channel> listFirmFor(World world, UUID player) {
        List<Channel> firm = new ArrayList<>();
        for (FirmChannelDefinition definition : FirmChannelConfig.getDefinitions()) {
            if (!definition.isPublic()) {
                Channel channel = getOrCreateFirmValue(world, definition, player);
                if (channel != null) {
                    firm.add(channel);
                }
            }
        }
        for (FirmChannelDefinition definition : FirmChannelConfig.getDefinitions()) {
            if (definition.isPublic()) {
                Channel channel = getOrCreateFirmValue(world, definition, null);
                if (channel != null) {
                    firm.add(channel);
                }
            }
        }
        return firm;
    }

    /**
     * @return every channel {@code viewer} may connect to, in the order the access plug shows them: their own
     *         channels, the pack's per player channels for them, the pack's shared channels in configuration order, and
     *         finally the public channels of other players.
     *
     * <p>Firm channels are created on first listing, which is what makes a declared channel the player has never used
     * show up with a value of zero instead of being missing.</p>
     */
    public static List<Channel> listAccessible(World world, UUID viewer) {
        ChannelWorldData data = getData(world);
        if (data == null) {
            return Collections.emptyList();
        }
        List<Channel> accessible = new ArrayList<>();
        Map<String, Channel> ownChannels = data.getChannels(viewer);
        if (ownChannels != null && !ownChannels.isEmpty()) {
            List<Channel> own = new ArrayList<>(ownChannels.values());
            own.sort(BY_NAME);
            accessible.addAll(own);
        }
        accessible.addAll(listFirmFor(world, viewer));
        List<Channel> foreign = new ArrayList<>();
        data.getAllChannels().forEach((owner, ownerChannels) -> {
            if (owner.equals(viewer) || owner.equals(FirmChannelDefinition.FIRM_OWNER)) {
                return;
            }
            for (Channel channel : ownerChannels.values()) {
                if (channel.isPublic()) {
                    foreign.add(channel);
                }
            }
        });
        foreign.sort(BY_OWNER_THEN_NAME);
        accessible.addAll(foreign);
        return accessible;
    }

    /**
     * Resolves the pack's energy channels for one player: the per player instance of a private declaration first, then
     * the shared ones in configuration order.
     *
     * <p>A firm channel outranks a channel the player registered themselves, which is the point of a pack declaring one:
     * the pack decides where the energy of its machines lands. A player who would rather use their own channel switches
     * the firm one off in the access plug's GUI.</p>
     *
     * @param accepted additional filter, the access plug passes its own enable state here
     */
    @Nullable
    public static Channel findFirmEnergyChannelFor(World world, UUID player, Predicate<Channel> accepted) {
        FirmChannelDefinition perPlayer = getPrivateFirmEnergyChannel();
        if (perPlayer != null) {
            Channel channel = getOrCreateFirmValue(world, perPlayer, player);
            if (channel != null && accepted.test(channel)) {
                return channel;
            }
        }
        for (FirmChannelDefinition definition : FirmChannelConfig.getDefinitions()) {
            if (!definition.isEnergy() || !definition.isPublic()) {
                continue;
            }
            Channel channel = getOrCreateFirmValue(world, definition, null);
            if (channel != null && accepted.test(channel)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * Resolves the shared energy channel of <b>another player</b> a machine may draw from, lowest owner UUID first.
     *
     * <p>Firm channels are not looked at here, they come before the machine owner's own channel, see
     * {@link #findFirmEnergyChannelFor(World, UUID, Predicate)}.</p>
     *
     * @param accepted additional filter, the access plug passes its own enable state here
     */
    public static Channel findPublicEnergyChannelFor(World world, UUID machineOwner, Predicate<Channel> accepted) {
        ChannelWorldData data = getData(world);
        if (data == null) {
            return null;
        }
        List<Channel> candidates = new ArrayList<>();
        data.getAllChannels().forEach((owner, ownerChannels) -> {
            if (owner.equals(machineOwner) || owner.equals(FirmChannelDefinition.FIRM_OWNER)) {
                return;
            }
            for (Channel channel : ownerChannels.values()) {
                if (channel.isEnergy() && channel.isPublic() && accepted.test(channel)) {
                    candidates.add(channel);
                }
            }
        });
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparing((Channel channel) -> channel.getOwner().toString())
                .thenComparing(Channel::getName));
        return candidates.get(0);
    }

    // ------------------------------------------------------------ firm channels

    /** Separator between a per player firm channel's id and its player in the storage key. */
    public static final char FIRM_PLAYER_SEPARATOR = '@';

    /**
     * @return the key a firm channel's value is stored under: the plain id for a shared channel, {@code id@player} for
     *         the instance of one player. {@code null} when a per player channel is asked for without a player - there
     *         is nothing to read then, which is exactly what callers outside a machine context should see.
     */
    @Nullable
    public static String firmValueName(FirmChannelDefinition definition, @Nullable UUID player) {
        if (definition.isPublic()) {
            return definition.getChannelId();
        }
        return player == null ? null : definition.getChannelId() + FIRM_PLAYER_SEPARATOR + player;
    }

    /**
     * @return the energy channel the pack gives every player, {@code null} when it declares none. A per player firm
     *         energy channel <i>is</i> that player's energy channel, which is why it occupies the one slot a player has
     *         (see {@link #register}) and why a pack provides at most one of them.
     */
    @Nullable
    public static FirmChannelDefinition getPrivateFirmEnergyChannel() {
        for (FirmChannelDefinition definition : FirmChannelConfig.getDefinitions()) {
            if (definition.isEnergy() && !definition.isPublic()) {
                return definition;
            }
        }
        return null;
    }

    /**
     * @return whether the pack declares any energy channel at all. Such a channel replaces the energy channels players
     *         used to register themselves, so the question needs no world: it is answered by the configuration.
     */
    public static boolean hasFirmEnergyChannel() {
        for (FirmChannelDefinition definition : FirmChannelConfig.getDefinitions()) {
            if (definition.isEnergy()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the pack provided energy channel of one player, creating its value on first use like every other firm
     * channel.
     *
     * @return the channel, {@code null} when the pack declares no per player energy channel
     */
    @Nullable
    public static Channel getFirmEnergyChannel(World world, UUID player) {
        FirmChannelDefinition definition = getPrivateFirmEnergyChannel();
        return definition == null ? null : getOrCreateFirmValue(world, definition, player);
    }

    /**
     * Looks a firm channel's value up without creating anything.
     *
     * @return the value holder, {@code null} while the channel has no value yet
     */
    @Nullable
    public static Channel getFirmValue(World world, FirmChannelDefinition definition, @Nullable UUID player) {
        String name = firmValueName(definition, player);
        return name == null ? null : get(world, FirmChannelDefinition.FIRM_OWNER, name);
    }

    /**
     * Looks a firm channel's value up, creating it on first use: the configuration declares the channel, a save only
     * holds the values, so a channel nobody has used yet has no entry at all.
     *
     * @return the value holder, {@code null} when a per player channel is asked for without a player
     */
    @Nullable
    public static Channel getOrCreateFirmValue(World world, FirmChannelDefinition definition, @Nullable UUID player) {
        String name = firmValueName(definition, player);
        ChannelWorldData data = name == null ? null : getData(world);
        if (data == null) {
            return null;
        }
        Map<String, Channel> firmChannels = data.getOrCreateChannels(FirmChannelDefinition.FIRM_OWNER);
        Channel channel = firmChannels.get(name);
        if (channel != null && channel.isEnergy() != definition.isEnergy()) {
            // the definition decides the type of its channel, so a pack author flipping isEnergy starts the value over
            ModularGrid.LOGGER.warn("Firm channel '{}' changed its type to {}, its stored value is dropped",
                    name, definition.isEnergy() ? "energy" : "data");
            channel = null;
        }
        if (channel == null) {
            channel = new Channel(name, FirmChannelDefinition.FIRM_OWNER, "", definition.isPublic(),
                    definition.isEnergy());
            firmChannels.put(name, channel);
            data.markDirty();
        }
        return channel;
    }

    /**
     * @return every firm channel value in the save, whoever it belongs to and whether it is still declared or not
     */
    public static List<Channel> listFirm(World world) {
        ChannelWorldData data = getData(world);
        if (data == null) {
            return Collections.emptyList();
        }
        Map<String, Channel> firmChannels = data.getChannels(FirmChannelDefinition.FIRM_OWNER);
        return firmChannels == null ? Collections.emptyList() : new ArrayList<>(firmChannels.values());
    }

    /**
     * Drops the values of firm channels the configuration no longer declares, and of those whose sharing mode or type
     * has changed. Runs after the configuration was read, so a save never keeps a value nothing can reach any more.
     *
     * <p>Only definition <i>references</i> are cleaned up here: a value whose channel is still declared is kept, which
     * is what makes editing the configuration safe.</p>
     *
     * @return the amount of values that were dropped
     */
    public static int pruneFirmChannels(World world) {
        if (!FirmChannelConfig.isLoaded()) {
            // without a successfully read configuration every value would look undeclared, so nothing is dropped
            return 0;
        }
        ChannelWorldData data = getData(world);
        if (data == null) {
            return 0;
        }
        Map<String, Channel> firmChannels = data.getChannels(FirmChannelDefinition.FIRM_OWNER);
        if (firmChannels == null || firmChannels.isEmpty()) {
            return 0;
        }
        int removed = 0;
        Iterator<Map.Entry<String, Channel>> iterator = firmChannels.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Channel> entry = iterator.next();
            String reason = firmPruneReason(entry.getKey(), entry.getValue());
            if (reason == null) {
                continue;
            }
            ModularGrid.LOGGER.info("Dropping the value of firm channel '{}': {}", entry.getKey(), reason);
            iterator.remove();
            removed++;
        }
        if (firmChannels.isEmpty()) {
            data.getAllChannels().remove(FirmChannelDefinition.FIRM_OWNER);
        }
        if (removed > 0) {
            data.markDirty();
        }
        return removed;
    }

    /**
     * Decides what happens to one stored firm value. Only meaningful with a successfully read configuration: without
     * one every id looks undeclared, which is why {@link #pruneFirmChannels(World)} bails out first.
     *
     * @param key the storage key the value sits under
     * @return why the value has to be dropped, {@code null} while the configuration still reaches it
     */
    @Nullable
    static String firmPruneReason(String key, Channel channel) {
        FirmChannelDefinition definition = FirmChannelConfig.find(channel.getFirmChannelId());
        if (definition == null) {
            return "it is no longer declared in " + FirmChannelConfig.FILE_NAME;
        }
        if (!key.equals(firmValueName(definition, channel.getFirmPlayer()))) {
            return "its declaration now uses a different sharing mode";
        }
        if (channel.isEnergy() != definition.isEnergy()) {
            return "its declaration changed the channel type";
        }
        return null;
    }

    /**
     * Drops the energy channels players registered themselves. A pack that declares firm energy channels decides where
     * the energy of its machines lands, so a channel somebody set up before that has no place any more - and neither has
     * a resolution order that would silently keep using it.
     *
     * <p>Their values are lost on purpose: the channels are gone, and only the pools the pack built remain. Runs with
     * the configuration, so a save is brought in line on server start and on every reload.</p>
     *
     * @return the amount of energy channels that were removed
     */
    public static int prunePlayerEnergyChannels(World world) {
        if (!FirmChannelConfig.isLoaded() || !hasFirmEnergyChannel()) {
            return 0;
        }
        ChannelWorldData data = getData(world);
        if (data == null) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<UUID, Map<String, Channel>> ownerChannels : data.getAllChannels().entrySet()) {
            if (ownerChannels.getKey().equals(FirmChannelDefinition.FIRM_OWNER)) {
                continue;
            }
            Iterator<Map.Entry<String, Channel>> channels = ownerChannels.getValue().entrySet().iterator();
            while (channels.hasNext()) {
                Channel channel = channels.next().getValue();
                if (!channel.isEnergy()) {
                    continue;
                }
                ModularGrid.LOGGER.info("Dropping the energy channel '{}' of {}: this pack declares firm energy channels",
                        channel.getName(), channel.getOwnerName().isEmpty()
                                ? ownerChannels.getKey().toString() : channel.getOwnerName());
                channels.remove();
                removed++;
            }
        }
        if (removed > 0) {
            data.markDirty();
            ModularGrid.LOGGER.info("Dropped {} player energy channel(s): the pack declares firm energy channels",
                    removed);
        }
        return removed;
    }

    // ----------------------------------------------------------------- mutation

    /**
     * Sets the value of an energy channel, whose payload is a {@code long}.
     *
     * @return {@code false} if the channel does not exist or is not an energy channel
     */
    public static boolean setEnergyValue(World world, UUID owner, String name, long value) {
        Channel channel = get(world, owner, name);
        if (channel == null || !channel.isEnergy()) {
            return false;
        }
        channel.setEnergyValue(value);
        markChanged(world);
        return true;
    }

    /**
     * Sets the value of a normal channel, whose payload is a {@code float}.
     *
     * @return {@code false} if the channel does not exist or is an energy channel
     */
    public static boolean setFloatValue(World world, UUID owner, String name, float value) {
        Channel channel = get(world, owner, name);
        if (channel == null || channel.isEnergy()) {
            return false;
        }
        channel.setFloatValue(value);
        markChanged(world);
        return true;
    }

    /**
     * Persists a value change that was applied straight to the {@link Channel} instance,
     * used by the per tick energy transfer.
     */
    public static void markChanged(World world) {
        ChannelWorldData data = getData(world);
        if (data != null) {
            data.markDirty();
        }
    }

}
