package com.modulargrid.common.integration.crafttweaker;

import com.modulargrid.ModularGrid;
import com.modulargrid.common.channel.FirmChannelConfig;
import crafttweaker.mc1120.events.ScriptRunEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Registers the scripting side with CraftTweaker.
 *
 * <p>The registration lives in its own {@link Optional.Method} guarded method: its body is the only place that mentions
 * a CraftTweaker type, and Forge strips it when the mod is missing. Nothing calls it unless
 * {@link #isAvailable()} said the mod is there - the same shape the Flux Networks and Circulation Flow Networks
 * integrations use.</p>
 */
public final class CraftTweakerIntegration {

    private static boolean available;

    private CraftTweakerIntegration() {
    }

    /** Called from the mod's preInit, before CraftTweaker compiles any script. */
    public static void preInit() {
        available = Loader.isModLoaded("crafttweaker");
        if (available) {
            registerChannels();
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    @Optional.Method(modid = "crafttweaker")
    private static void registerChannels() {
        CtChannels.registerDeclaredChannels();
        // the builder is used as a prefix, so a short name saves every pack the same import
        CtChannels.registerShortClassName("ChannelModifierBuilder", CtChannelModifierBuilder.class);
        // CraftTweaker recompiles every script on /ct reload, and a channel id is only usable while its symbol exists
        MinecraftForge.EVENT_BUS.register(new ScriptListener());
        MinecraftForge.EVENT_BUS.register(new ChannelModifierSampler());
        ModularGrid.LOGGER.info("CraftTweaker is installed, channel ids are exposed to scripts");
    }

    /**
     * Keeps the script side in step with the configuration: a script reload rereads the firm channel definition file and
     * registers the channels of it, so adding a channel and reloading is enough.
     *
     * <p>The class is only ever loaded from the guarded registration above, so mentioning a CraftTweaker type in its
     * signature cannot break a setup without CraftTweaker.</p>
     */
    private static final class ScriptListener {

        @SubscribeEvent
        public void onScriptsCompiling(ScriptRunEvent.Pre event) {
            FirmChannelConfig.reload();
            CtChannels.registerDeclaredChannels();
            // the builder is used as a prefix, so a short name saves every pack the same import
            CtChannels.registerShortClassName("ChannelModifierBuilder", CtChannelModifierBuilder.class);
        }

    }

}
