package com.modulargrid;

import com.modulargrid.common.CommonProxy;
import com.modulargrid.common.channel.FirmChannelConfig;
import com.modulargrid.common.command.CommandModularGrid;
import com.modulargrid.common.integration.circulation.CirculationIntegration;
import com.modulargrid.common.integration.crafttweaker.CraftTweakerIntegration;
import com.modulargrid.common.integration.fluxnetworks.FluxNetworksIntegration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ModularGrid - a Modular Machinery: Community Edition addon.
 */
@Mod(modid = ModularGrid.MOD_ID,
        name = ModularGrid.MOD_NAME,
        version = ModularGrid.VERSION,
        dependencies = "required-after:modularmachinery@[2.1.0,);",
        acceptedMinecraftVersions = "[1.12,1.13)")
@SuppressWarnings("MethodMayBeStatic")
public class ModularGrid {

    public static final String MOD_ID = Tags.MOD_ID;
    public static final String MOD_NAME = Tags.MOD_NAME;
    public static final String VERSION = Tags.VERSION;

    public static final String CLIENT_PROXY = "com.modulargrid.client.ClientProxy";
    public static final String COMMON_PROXY = "com.modulargrid.common.CommonProxy";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @Mod.Instance(MOD_ID)
    public static ModularGrid instance;

    @SidedProxy(modId = MOD_ID, clientSide = CLIENT_PROXY, serverSide = COMMON_PROXY)
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // the firm channel definitions live in config/modulargrid/firm_channel.json: they are needed before CraftTweaker
        // compiles any script, because a firm channel id is a script variable
        FirmChannelConfig.load(event.getModConfigurationDirectory());
        proxy.preInit();
        FluxNetworksIntegration.preInit();
        CirculationIntegration.preInit();
        // before CraftTweaker compiles any script, so channel ids are known while the scripts are built
        CraftTweakerIntegration.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandModularGrid());
        // the configuration may have been edited while the game was not running, and its values live in the save
        FirmChannelConfig.reload();
    }

}
