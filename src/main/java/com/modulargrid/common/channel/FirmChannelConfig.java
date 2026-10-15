package com.modulargrid.common.channel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modulargrid.ModularGrid;

import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the firm channel definitions from {@code config/modulargrid/firm_channel.json}.
 *
 * <p>The configuration holds <b>definitions only</b>; the values live in the channel store like every other channel's
 * value (see {@link ChannelWorldData}), so editing this file never touches a save and deleting an entry simply drops
 * that channel's value.</p>
 *
 * <p>Keys are camelCase: {@code allowPlayerEnergyChannels}, and per channel {@code channelId},
 * {@code channelLocalization}, {@code isPublic}, {@code isEnergy}, {@code masterTo}. Parsing is defensive per entry - a
 * malformed channel is reported and skipped instead of discarding the whole file - and the result is a plain value
 * object, so it can be unit tested without touching the file system (see {@link #parse(String)}).</p>
 */
public final class FirmChannelConfig {

    /** Directory inside the instance's config folder. */
    public static final String DIRECTORY_NAME = "modulargrid";

    /** File the definitions are read from. */
    public static final String FILE_NAME = "firm_channel.json";

    /** Whether players may register their own energy channel when the configuration says nothing. */
    public static final boolean DEFAULT_ALLOW_PLAYER_ENERGY_CHANNELS = true;

    private static final String KEY_ALLOW_PLAYER_ENERGY = "allowPlayerEnergyChannels";
    private static final String KEY_CHANNELS = "channels";
    private static final String KEY_CHANNEL_ID = "channelId";
    private static final String KEY_CHANNEL_LOCALIZATION = "channelLocalization";
    private static final String KEY_IS_PUBLIC = "isPublic";
    private static final String KEY_IS_ENERGY = "isEnergy";
    private static final String KEY_MASTER_TO = "masterTo";

    /** Default written when the file does not exist yet: no channels, players keep their energy channel. */
    private static final String DEFAULT_FILE = """
            {
              "allowPlayerEnergyChannels": %s,
              "channels": []
            }
            """.formatted(DEFAULT_ALLOW_PLAYER_ENERGY_CHANNELS);

    /** Only used to write {@link #toJson()}; reading goes through the defensive {@link #parse(String)}. */
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile FirmChannelConfig current = new FirmChannelConfig(Collections.emptyList(),
            DEFAULT_ALLOW_PLAYER_ENERGY_CHANNELS);

    /**
     * Whether the configuration was read successfully at least once. Values of firm channels are only dropped when it
     * was: without a configuration every value looks undeclared, and a file the author broke must never delete data.
     */
    private static volatile boolean loaded;

    /**
     * The config folder the instance was started with, remembered from {@link #load(File)} so a reread does not have to
     * go through Forge again.
     */
    private static volatile File configDirectory;

    private final List<FirmChannelDefinition> definitions;
    private final Map<String, FirmChannelDefinition> byId;
    private final boolean playerEnergyChannelsAllowed;

    private FirmChannelConfig(List<FirmChannelDefinition> definitions, boolean playerEnergyChannelsAllowed) {
        this.definitions = Collections.unmodifiableList(definitions);
        Map<String, FirmChannelDefinition> index = new LinkedHashMap<>();
        for (FirmChannelDefinition definition : definitions) {
            index.put(definition.getChannelId(), definition);
        }
        this.byId = Collections.unmodifiableMap(index);
        this.playerEnergyChannelsAllowed = playerEnergyChannelsAllowed;
    }

    // ------------------------------------------------------------------- access

    /** @return the definitions of the last {@link #load(File)} call, in configuration order */
    public static List<FirmChannelDefinition> getDefinitions() {
        return current.definitions;
    }

    /** @return the definition with that id, {@code null} when the configuration does not declare it */
    @Nullable
    public static FirmChannelDefinition find(String channelId) {
        return channelId == null ? null : current.byId.get(channelId);
    }

    /** @return whether the configuration itself declares that id */
    public static boolean isFirm(String channelId) {
        return find(channelId) != null;
    }

    /** @return whether the configuration was read successfully, see {@link #loaded} */
    public static boolean isLoaded() {
        return loaded;
    }

    /** @return the definitions of this configuration, regardless of which one is currently loaded */
    List<FirmChannelDefinition> definitionsOf() {
        return definitions;
    }

    /** @return the toggle of this configuration, regardless of which one is currently loaded */
    boolean allowsPlayerEnergyChannels() {
        return playerEnergyChannelsAllowed;
    }

    /**
     * @return whether players may register their own energy channel. A pack that provides firm energy channels
     *         switches this off so the pool layout stays under its control; already registered channels keep working.
     */
    public static boolean isPlayerEnergyChannelsAllowed() {
        return current.playerEnergyChannelsAllowed;
    }

    /** @return the file inside the instance's config folder */
    public static File getFile(File configDirectory) {
        return new File(new File(configDirectory, DIRECTORY_NAME), FILE_NAME);
    }

    // ------------------------------------------------------------------ loading

    /**
     * Reads the configuration, creating a minimal file when none exists yet. Called on server start and whenever the
     * machine registry is reloaded, so a datapack style edit only needs {@code /modularmachinery reload}.
     */
    public static void load(File configDirectory) {
        FirmChannelConfig.configDirectory = configDirectory;
        File file = getFile(configDirectory);
        try {
            if (!file.isFile()) {
                writeDefault(file);
            }
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            current = parse(json);
            loaded = true;
            ModularGrid.LOGGER.info("Loaded {} firm channel definition(s) from {}, player energy channels are {}",
                    current.definitions.size(), file, current.playerEnergyChannelsAllowed ? "allowed" : "disabled");
        } catch (IOException | RuntimeException ex) {
            ModularGrid.LOGGER.error("Could not read {}, keeping the previous {} firm channel definition(s)",
                    file, current.definitions.size(), ex);
        }
    }

    /**
     * Re-reads the configuration and drops the values of firm channels it no longer reaches. Called on server start and
     * whenever Modular Machinery reloads its machines, so a pack author only needs {@code /modularmachinery reload}
     * after editing {@code firm_channel.json}.
     */
    public static void reload() {
        File directory = configDirectory;
        if (directory == null) {
            ModularGrid.LOGGER.warn("Firm channels cannot be reread: the configuration directory is not known yet");
            return;
        }
        load(directory);
        // values of channels the configuration dropped can only be removed once a world holds them, and a pack that
        // declares energy channels replaces the ones players registered themselves
        int dropped = ChannelRegistry.pruneFirmChannels(DimensionManager.getWorld(0));
        if (dropped > 0) {
            ModularGrid.LOGGER.info("Firm channel configuration reread, {} value(s) that are no longer declared were "
                    + "dropped", dropped);
        }
        ChannelRegistry.prunePlayerEnergyChannels(DimensionManager.getWorld(0));
    }

    private static void writeDefault(File file) throws IOException {
        File directory = file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            ModularGrid.LOGGER.warn("Could not create the configuration directory {}", directory);
        }
        Files.writeString(file.toPath(), DEFAULT_FILE, StandardCharsets.UTF_8);
        ModularGrid.LOGGER.info("Created a default firm channel configuration at {}", file);
    }

    // ------------------------------------------------------------------ parsing

    /**
     * Parses the configuration text. Malformed channels are logged and skipped, so one typo does not hide every other
     * channel.
     *
     * @throws RuntimeException when the text is not a JSON object at all
     */
    static FirmChannelConfig parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("The configuration has to be a JSON object");
        }
        JsonObject object = root.getAsJsonObject();
        boolean playerEnergyChannels = readBoolean(object, KEY_ALLOW_PLAYER_ENERGY,
                DEFAULT_ALLOW_PLAYER_ENERGY_CHANNELS);

        List<FirmChannelDefinition> definitions = new ArrayList<>();
        JsonElement channels = object.get(KEY_CHANNELS);
        if (channels == null || channels.isJsonNull()) {
            return new FirmChannelConfig(definitions, playerEnergyChannels);
        }
        if (!channels.isJsonArray()) {
            ModularGrid.LOGGER.error("'{}' has to be an array, ignoring it", KEY_CHANNELS);
            return new FirmChannelConfig(definitions, playerEnergyChannels);
        }
        for (JsonElement element : channels.getAsJsonArray()) {
            FirmChannelDefinition definition = readChannel(element);
            if (definition == null) {
                continue;
            }
            if (definitions.contains(definition)) {
                ModularGrid.LOGGER.warn("Duplicate firm channel id '{}', ignoring the later one",
                        definition.getChannelId());
                continue;
            }
            definitions.add(definition);
        }
        warnAboutSeveralPlayerEnergyChannels(definitions);
        return new FirmChannelConfig(definitions, playerEnergyChannels);
    }

    /**
     * A player has exactly one energy channel, so only the first per player energy channel of the configuration is
     * used as theirs. Reporting it keeps the pack author from wondering which one that is.
     */
    private static void warnAboutSeveralPlayerEnergyChannels(List<FirmChannelDefinition> definitions) {
        String used = null;
        int count = 0;
        for (FirmChannelDefinition definition : definitions) {
            if (definition.isEnergy() && !definition.isPublic()) {
                count++;
                if (used == null) {
                    used = definition.getChannelId();
                }
            }
        }
        if (count > 1) {
            ModularGrid.LOGGER.warn("{} declares {} per player energy channels, only '{}' is used: a player owns one "
                    + "energy channel", FILE_NAME, count, used);
        }
    }

    @Nullable
    private static FirmChannelDefinition readChannel(JsonElement element) {
        if (!element.isJsonObject()) {
            ModularGrid.LOGGER.error("Every entry of '{}' has to be an object, ignoring {}", KEY_CHANNELS, element);
            return null;
        }
        JsonObject channel = element.getAsJsonObject();
        String channelId = readString(channel, KEY_CHANNEL_ID);
        if (!FirmChannelDefinition.isValidId(channelId)) {
            ModularGrid.LOGGER.error("Firm channel id '{}' is not usable: it has to match {} and be at most {} "
                            + "characters long, ignoring the entry",
                    channelId, FirmChannelDefinition.ID_PATTERN.pattern(), FirmChannelDefinition.MAX_ID_LENGTH);
            return null;
        }

        List<String> masterTo = new ArrayList<>();
        boolean masterToAll = false;
        JsonElement masters = channel.get(KEY_MASTER_TO);
        if (masters != null && !masters.isJsonNull()) {
            if (!masters.isJsonArray()) {
                ModularGrid.LOGGER.error("'{}' of firm channel '{}' has to be an array, ignoring the entry",
                        KEY_MASTER_TO, channelId);
                return null;
            }
            for (JsonElement master : masters.getAsJsonArray()) {
                String name = master.isJsonPrimitive() ? master.getAsString() : null;
                String normalized = FirmChannelDefinition.normalizeMachineName(name);
                if (normalized == null) {
                    // an empty entry means "every machine", which is the same as an empty list
                    masterToAll = true;
                } else if (!masterTo.contains(normalized)) {
                    masterTo.add(normalized);
                }
            }
        }

        boolean energy = readBoolean(channel, KEY_IS_ENERGY, false);
        boolean publicChannel = readBoolean(channel, KEY_IS_PUBLIC, true);
        String localization = readString(channel, KEY_CHANNEL_LOCALIZATION);
        return FirmChannelDefinition.create(channelId, localization, publicChannel, energy, masterTo, masterToAll);
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    /** @return the configuration as JSON, used by the documentation example and by tests */
    String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_ALLOW_PLAYER_ENERGY, playerEnergyChannelsAllowed);
        JsonArray channels = new JsonArray();
        for (FirmChannelDefinition definition : definitions) {
            JsonObject channel = new JsonObject();
            channel.addProperty(KEY_CHANNEL_ID, definition.getChannelId());
            channel.addProperty(KEY_CHANNEL_LOCALIZATION, definition.getChannelLocalization());
            channel.addProperty(KEY_IS_PUBLIC, definition.isPublic());
            channel.addProperty(KEY_IS_ENERGY, definition.isEnergy());
            JsonArray masters = new JsonArray();
            if (definition.isMachineRestricted()) {
                definition.getMasterTo().forEach(masters::add);
            } else {
                masters.add("");
            }
            channel.add(KEY_MASTER_TO, masters);
            channels.add(channel);
        }
        root.add(KEY_CHANNELS, channels);
        return PRETTY_GSON.toJson(root);
    }

    @Override
    public String toString() {
        return "FirmChannelConfig" + definitions;
    }
}
