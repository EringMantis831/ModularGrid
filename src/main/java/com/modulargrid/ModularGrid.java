package com.modulargrid;

import com.modulargrid.common.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ModularGrid - a Modular Machinery: Community Edition addon.
 *
 * <p>Blocks/items live in {@code com.modulargrid.common}, registration is event driven,
 * see {@link com.modulargrid.common.registry.RegistryBlocks} and
 * {@link com.modulargrid.common.registry.RegistryItems}.</p>
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
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

}
