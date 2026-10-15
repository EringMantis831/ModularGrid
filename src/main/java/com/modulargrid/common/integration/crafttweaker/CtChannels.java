package com.modulargrid.common.integration.crafttweaker;

import com.modulargrid.common.channel.Channel;
import com.modulargrid.common.channel.ChannelRegistry;
import com.modulargrid.common.channel.ChannelScriptContext;
import com.modulargrid.common.channel.FirmChannelConfig;
import com.modulargrid.common.channel.FirmChannelDefinition;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.zenscript.GlobalRegistry;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import stanhebben.zenscript.symbols.SymbolType;
import stanhebben.zenscript.type.ZenType;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Makes firm channels readable from scripts: {@code Temp.amount} for a bare channel id and {@code <channel:Temp>.amount}
 * for scripts that prefer the bracket syntax.
 *
 * <p>Only the channel id ever reaches a script - no owner UUID, no address - which is why only firm channels, the ones
 * a pack declares and names, are exposed. A player channel is addressed by its owner internally and stays out of
 * scripts.</p>
 *
 * <p>Resolution happens when the script runs, not when it is compiled, so a value that changes during a recipe is seen.
 * The instance a per player channel resolves to is the one of the machine currently ticking, see
 * {@link ChannelScriptContext}.</p>
 */
public final class CtChannels {

    /** Ids a global symbol was registered for; CraftTweaker throws on a duplicate name. */
    private static final Set<String> REGISTERED_IDS = new LinkedHashSet<>();

    /** Short class names that were registered, see {@link #registerShortClassName(String, Class)}. */
    private static final Set<String> REGISTERED_SHORT_NAMES = new LinkedHashSet<>();

    private static boolean bracketHandlerRegistered;

    private CtChannels() {
    }

    // ---------------------------------------------------------- script entry points

    /**
     * Value a script sees for a data channel.
     *
     * <p>Called by compiled {@code <channelId>.amount} expressions, see {@link CtChannelSymbol}.</p>
     */
    public static CtChannelValue valueOf(String channelId) {
        return new CtChannelValue(channelId);
    }

    /** Value a script sees for an energy channel, whose payload is a {@code long}. */
    public static CtEnergyChannelValue valueOfEnergy(String channelId) {
        return new CtEnergyChannelValue(channelId);
    }

    /** @return whether the pack declares this id as an energy channel, which decides the script side type of it */
    public static boolean isEnergyChannel(String channelId) {
        FirmChannelDefinition definition = FirmChannelConfig.find(channelId);
        return definition != null && definition.isEnergy();
    }

    // ------------------------------------------------------------------- resolution

    static float readFloat(String channelId) {
        Channel channel = resolve(channelId);
        return channel == null ? 0.0F : channel.getFloatValue();
    }

    static long readEnergy(String channelId) {
        Channel channel = resolve(channelId);
        return channel == null ? 0L : channel.getEnergyValue();
    }

    /**
     * Resolves the channel instance a script currently addresses.
     *
     * <p>A shared channel is the same instance for everyone. A per player channel is the instance of the machine whose
     * tick is running; without a machine (a script outside machine logic, or the server side of a client connected to a
     * multiplayer server) there is nothing to read and the answer is {@code null}, which every caller turns into a
     * zero.</p>
     *
     * @return the channel, {@code null} when the id is not declared or cannot be resolved here
     */
    @Nullable
    static Channel resolve(String channelId) {
        FirmChannelDefinition definition = FirmChannelConfig.find(channelId);
        if (definition == null) {
            return null;
        }
        World world = DimensionManager.getWorld(0);
        if (world == null) {
            // channel values live on the server; a script of a client that is not hosting the world sees none
            return null;
        }
        UUID player = definition.isPublic() ? null : ChannelScriptContext.currentPlayer();
        return ChannelRegistry.getFirmValue(world, definition, player);
    }

    // ----------------------------------------------------------------- registration

    /** @return the ZenScript type of {@link CtChannelValue}, resolved through CraftTweaker's shared registry */
    public static ZenType channelValueType() {
        return typeOf(CtChannelValue.class);
    }

    /** @return the ZenScript type of {@link CtEnergyChannelValue} */
    public static ZenType energyChannelValueType() {
        return typeOf(CtEnergyChannelValue.class);
    }

    /** @return the ZenScript type of a registered Zen class */
    static ZenType typeOf(Class<?> clazz) {
        return GlobalRegistry.getTypes().getType(clazz);
    }

    /**
     * Registers one of our Zen classes under its short name, so a script can write {@code ChannelModifierBuilder}
     * without an import. CraftTweaker puts a native class into the symbol tree under its full name, this adds the same
     * kind of symbol under the short one.
     */
    static void registerShortClassName(String shortName, Class<?> clazz) {
        if (!REGISTERED_SHORT_NAMES.add(shortName)) {
            return;
        }
        CraftTweakerAPI.registerGlobalSymbol(shortName, new SymbolType(typeOf(clazz)));
    }

    /**
     * Registers a global symbol for every declared firm channel, so the ids can be written like properties, plus the
     * bracket handler. Called before CraftTweaker compiles scripts and again on every script reload, because a symbol
     * has to exist while the script that uses it is compiled.
     */
    public static void registerDeclaredChannels() {
        if (!bracketHandlerRegistered) {
            bracketHandlerRegistered = true;
            CraftTweakerAPI.registerBracketHandler(new CtChannelBracketHandler());
        }
        for (FirmChannelDefinition definition : FirmChannelConfig.getDefinitions()) {
            register(definition.getChannelId());
        }
    }

    /**
     * Registers one channel id as a script global.
     *
     * <p>CraftTweaker's global table rejects a duplicate name with an exception, so registering is done once per id and
     * a channel that appears in the configuration later simply gets its symbol then.</p>
     */
    private static void register(String channelId) {
        if (!REGISTERED_IDS.add(channelId)) {
            return;
        }
        CraftTweakerAPI.registerGlobalSymbol(channelId, new CtChannelSymbol(channelId));
    }

}
