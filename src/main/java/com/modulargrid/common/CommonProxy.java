package com.modulargrid.common;

import com.modulargrid.common.registry.RegistryBlocks;
import com.modulargrid.common.registry.RegistryItems;
import net.minecraftforge.common.MinecraftForge;

/**
 * Common (server + client) side proxy.
 */
public class CommonProxy {

    public CommonProxy() {
        // Registry events are fired after every mod's preInit, so this is early enough
        MinecraftForge.EVENT_BUS.register(new RegistryBlocks());
        MinecraftForge.EVENT_BUS.register(new RegistryItems());
    }

    public void preInit() {
    }

    public void init() {
    }

}
